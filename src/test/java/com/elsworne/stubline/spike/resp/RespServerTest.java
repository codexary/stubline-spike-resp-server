package com.elsworne.stubline.spike.resp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RespServerTest {
  @Test
  void handlesSplitCommandsAndPipelinedCommands() throws Exception {
    try (var server = new RespServer(0)) {
      server.start();
      try (var socket = connect(server)) {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        out.write("*2\r\n$4\r\nECHO\r\n$5\r\nhe".getBytes(StandardCharsets.US_ASCII));
        out.flush();
        out.write(
            "llo\r\n*3\r\n$3\r\nSET\r\n$1\r\nk\r\n$1\r\nv\r\n*2\r\n$3\r\nGET\r\n$1\r\nk\r\n*2\r\n$3\r\nDEL\r\n$1\r\nk\r\n*2\r\n$3\r\nGET\r\n$1\r\nk\r\n"
                .getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals("hello".getBytes(StandardCharsets.US_ASCII), readBulk(in));
        assertEquals("+OK", readLine(in));
        assertArrayEquals("v".getBytes(StandardCharsets.US_ASCII), readBulk(in));
        assertEquals(":1", readLine(in));
        assertEquals("$-1", readLine(in));
      }
      try (var socket = connect(server)) {
        socket
            .getOutputStream()
            .write("PING\r\nINCR counter\r\n".getBytes(StandardCharsets.US_ASCII));
        assertEquals("+PONG", readLine(socket.getInputStream()));
        assertEquals(
            "-ERR unknown command 'INCR', with args beginning with: 'counter'",
            readLine(socket.getInputStream()));
      }
    }
  }

  @Test
  void preservesRandomBinaryBytesInSetGetAndEcho() throws Exception {
    byte[] key = randomBytes(31, 7);
    byte[] value = randomBytes(4096, 9);
    try (var server = new RespServer(0)) {
      server.start();
      try (var socket = connect(server)) {
        OutputStream out = socket.getOutputStream();
        writeCommand(out, ascii("SET"), key, value);
        writeCommand(out, ascii("GET"), key);
        writeCommand(out, ascii("ECHO"), value);
        assertEquals("+OK", readLine(socket.getInputStream()));
        assertArrayEquals(value, readBulk(socket.getInputStream()));
        assertArrayEquals(value, readBulk(socket.getInputStream()));
      }
    }
  }

  @Test
  void repliesToPipeTerminatorAfterManyPipelinedCommands() throws Exception {
    byte[] terminator = randomBytes(20, 42);
    try (var server = new RespServer(0)) {
      server.start();
      try (var socket = connect(server)) {
        var pipeline = new ByteArrayOutputStream();
        for (int i = 0; i < 200; i++) {
          writeCommand(pipeline, ascii("SET"), ascii("key-" + i), ascii("value-" + i));
        }
        writeCommand(pipeline, ascii("ECHO"), terminator);
        socket.getOutputStream().write(pipeline.toByteArray());
        for (int i = 0; i < 200; i++) {
          assertEquals("+OK", readLine(socket.getInputStream()));
        }
        assertArrayEquals(terminator, readBulk(socket.getInputStream()));
      }
    }
  }

  @Test
  void skipsEmptyLineBeforeBinaryRedisCliPipeTerminator() throws Exception {
    byte[] terminator = {
      0x00, 0x0d, 0x0a, (byte) 0x80, (byte) 0xff, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
    };
    try (var server = new RespServer(0)) {
      server.start();
      try (var socket = connect(server)) {
        var pipeline = new ByteArrayOutputStream();
        pipeline.write(ascii("*3\r\n$3\r\nSET\r\n$1\r\na\r\n$1\r\n1\r\n"));
        pipeline.write(ascii("*3\r\n$3\r\nSET\r\n$1\r\nb\r\n$1\r\n2\r\n"));
        pipeline.write(ascii("\r\n*2\r\n$4\r\nECHO\r\n$20\r\n"));
        pipeline.write(terminator);
        pipeline.write(ascii("\r\n"));
        socket.getOutputStream().write(pipeline.toByteArray());
        socket.shutdownOutput();

        var expected = new ByteArrayOutputStream();
        expected.write(ascii("+OK\r\n+OK\r\n$20\r\n"));
        expected.write(terminator);
        expected.write(ascii("\r\n"));
        assertArrayEquals(expected.toByteArray(), socket.getInputStream().readAllBytes());
      }
    }
  }

  @Test
  void choosesProtocolForEachCommandAfterEmptyLines() throws Exception {
    try (var server = new RespServer(0)) {
      server.start();
      try (var socket = connect(server)) {
        socket
            .getOutputStream()
            .write(ascii("\r\n\r\nPING\r\n*1\r\n$4\r\nPING\r\n\r\nPING\r\n\r\n"));
        socket.shutdownOutput();
        assertArrayEquals(
            ascii("+PONG\r\n+PONG\r\n+PONG\r\n"), socket.getInputStream().readAllBytes());
      }
    }
  }

  @Test
  void recoversFromClientReset() throws Exception {
    try (var server = new RespServer(0)) {
      server.start();
      var reset = connect(server);
      reset.setSoLinger(true, 0);
      reset
          .getOutputStream()
          .write("*2\r\n$4\r\nECHO\r\n$5\r\nhe".getBytes(StandardCharsets.US_ASCII));
      reset.close();
      try (var next = connect(server)) {
        next.getOutputStream().write("PING\r\n".getBytes(StandardCharsets.US_ASCII));
        assertEquals("+PONG", readLine(next.getInputStream()));
      }
    }
  }

  private static Socket connect(RespServer server) throws Exception {
    Socket socket = new Socket("localhost", server.port());
    socket.setSoTimeout(3000);
    return socket;
  }

  private static void writeCommand(OutputStream out, byte[]... args) throws Exception {
    out.write(ascii("*" + args.length + "\r\n"));
    for (byte[] arg : args) {
      out.write(ascii("$" + arg.length + "\r\n"));
      out.write(arg);
      out.write(ascii("\r\n"));
    }
  }

  private static String readLine(InputStream in) throws Exception {
    var bytes = new ByteArrayOutputStream();
    while (true) {
      int next = in.read();
      if (next == '\r') {
        assertEquals('\n', in.read());
        return bytes.toString(StandardCharsets.US_ASCII);
      }
      bytes.write(next);
    }
  }

  private static byte[] readBulk(InputStream in) throws Exception {
    int length = Integer.parseInt(readLine(in).substring(1));
    byte[] result = in.readNBytes(length);
    assertEquals('\r', in.read());
    assertEquals('\n', in.read());
    return result;
  }

  private static byte[] ascii(String text) {
    return text.getBytes(StandardCharsets.US_ASCII);
  }

  private static byte[] randomBytes(int size, long seed) {
    byte[] bytes = new byte[size];
    new Random(seed).nextBytes(bytes);
    return bytes;
  }
}
