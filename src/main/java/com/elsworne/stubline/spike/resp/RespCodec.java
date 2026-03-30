package com.elsworne.stubline.spike.resp;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads RESP2 commands and inline commands without changing bulk-string bytes. */
final class RespCodec {
  private static final int MAX_LINE = 1024;
  private static final int MAX_BULK = 1_048_576;

  private RespCodec() {}

  static List<byte[]> readCommand(InputStream in) throws IOException {
    while (true) {
      int marker = in.read();
      if (marker == -1) {
        return null;
      }
      if (marker == '*') {
        break;
      }
      List<byte[]> args = readInline(in, marker);
      if (!args.isEmpty()) {
        return args;
      }
      // redis-cli --pipe inserts an empty line before its RESP ECHO terminator.
      // Read a fresh marker so the next command can use either protocol.
    }
    int count = number(readLine(in));
    if (count < 1 || count > 16) {
      throw new IOException("Invalid argument count");
    }
    List<byte[]> args = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      if (in.read() != '$') {
        throw new IOException("Expected bulk string");
      }
      int length = number(readLine(in));
      if (length < 0 || length > MAX_BULK) {
        throw new IOException("Invalid bulk length");
      }
      byte[] data = in.readNBytes(length);
      if (data.length != length || in.read() != '\r' || in.read() != '\n') {
        throw new EOFException("Truncated bulk string");
      }
      args.add(data);
    }
    return args;
  }

  private static List<byte[]> readInline(InputStream in, int first) throws IOException {
    byte[] line = readLine(in, first);
    String text = new String(line, StandardCharsets.US_ASCII).trim();
    if (text.isEmpty()) {
      return List.of();
    }
    List<byte[]> args = new ArrayList<>();
    for (String part : text.split("\\s+")) {
      args.add(part.getBytes(StandardCharsets.US_ASCII));
    }
    return args;
  }

  private static byte[] readLine(InputStream in) throws IOException {
    return readLine(in, -1);
  }

  private static byte[] readLine(InputStream in, int first) throws IOException {
    var bytes = new ByteArrayOutputStream();
    int value = first >= 0 ? first : in.read();
    while (true) {
      if (value < 0) {
        throw new EOFException("Truncated RESP line");
      }
      if (value == '\r') {
        if (in.read() != '\n') {
          throw new IOException("Invalid line ending");
        }
        return bytes.toByteArray();
      }
      bytes.write(value);
      if (bytes.size() > MAX_LINE) {
        throw new IOException("RESP line too long");
      }
      value = in.read();
    }
  }

  private static int number(byte[] raw) throws IOException {
    try {
      return Integer.parseInt(new String(raw, StandardCharsets.US_ASCII));
    } catch (NumberFormatException e) {
      throw new IOException("Invalid RESP number", e);
    }
  }

  static void simple(OutputStream out, String text) throws IOException {
    write(out, "+" + text + "\r\n");
  }

  static void error(OutputStream out, String text) throws IOException {
    write(out, "-ERR " + text + "\r\n");
  }

  static void integer(OutputStream out, long value) throws IOException {
    write(out, ":" + value + "\r\n");
  }

  static void bulk(OutputStream out, byte[] value) throws IOException {
    if (value == null) {
      write(out, "$-1\r\n");
      return;
    }
    write(out, "$" + value.length + "\r\n");
    out.write(value);
    write(out, "\r\n");
  }

  private static void write(OutputStream out, String text) throws IOException {
    out.write(text.getBytes(StandardCharsets.US_ASCII));
  }
}
