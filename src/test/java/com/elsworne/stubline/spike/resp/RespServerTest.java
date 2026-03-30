package com.elsworne.stubline.spike.resp;

import static org.junit.jupiter.api.Assertions.*;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RespServerTest {
  @Test
  void handlesSplitCommandsAndPipelinedCommands() throws Exception {
    try (var server = new RespServer(0)) {
      server.start();
      try (var socket = new Socket("localhost", server.port())) {
        socket.setSoTimeout(3000);
        var out = socket.getOutputStream();
        var in = socket.getInputStream();
        out.write("*2\r\n$4\r\nECHO\r\n$5\r\nhe".getBytes(StandardCharsets.UTF_8));
        out.flush();
        out.write(
            "llo\r\n*3\r\n$3\r\nSET\r\n$1\r\nk\r\n$1\r\nv\r\n*2\r\n$3\r\nGET\r\n$1\r\nk\r\n*2\r\n$3\r\nDEL\r\n$1\r\nk\r\n*2\r\n$3\r\nGET\r\n$1\r\nk\r\n"
                .getBytes(StandardCharsets.UTF_8));
        out.flush();
        String expected = "$5\r\nhello\r\n+OK\r\n$1\r\nv\r\n:1\r\n$-1\r\n";
        assertEquals(
            expected,
            new String(
                in.readNBytes(expected.getBytes(StandardCharsets.UTF_8).length),
                StandardCharsets.UTF_8));
      }
      try (var socket = new Socket("localhost", server.port())) {
        socket.setSoTimeout(3000);
        socket.getOutputStream().write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(
            "+PONG\r\n", new String(socket.getInputStream().readNBytes(7), StandardCharsets.UTF_8));
      }
    }
  }

  @Test
  void recoversFromClientReset() throws Exception {
    try (var server = new RespServer(0)) {
      server.start();
      var reset = new Socket("localhost", server.port());
      reset.setSoLinger(true, 0);
      reset
          .getOutputStream()
          .write("*2\r\n$4\r\nECHO\r\n$5\r\nhe".getBytes(StandardCharsets.UTF_8));
      reset.close();
      try (var next = new Socket("localhost", server.port())) {
        next.setSoTimeout(3000);
        next.getOutputStream().write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(
            "+PONG\r\n", new String(next.getInputStream().readNBytes(7), StandardCharsets.UTF_8));
      }
    }
  }
}
