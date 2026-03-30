package com.elsworne.stubline.spike.resp;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads one RESP2 array at a time from a stream, independent of TCP read boundaries. */
final class RespCodec {
    private static final int MAX_LINE = 1024;
    private static final int MAX_BULK = 1_048_576;
    private RespCodec() { }

    static List<String> readCommand(InputStream in) throws IOException {
        int marker = in.read();
        if (marker == -1) return null;
        if (marker != '*') throw new IOException("Expected RESP array");
        int count = number(readLine(in));
        if (count < 1 || count > 16) throw new IOException("Invalid argument count");
        List<String> args = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (in.read() != '$') throw new IOException("Expected bulk string");
            int length = number(readLine(in));
            if (length < 0 || length > MAX_BULK) throw new IOException("Invalid bulk length");
            byte[] data = in.readNBytes(length);
            if (data.length != length || in.read() != '\r' || in.read() != '\n') throw new EOFException("Truncated bulk string");
            args.add(new String(data, StandardCharsets.UTF_8));
        }
        return args;
    }

    private static String readLine(InputStream in) throws IOException {
        var bytes = new ByteArrayOutputStream();
        while (true) {
            int value = in.read();
            if (value < 0) throw new EOFException("Truncated RESP line");
            if (value == '\r') {
                if (in.read() != '\n') throw new IOException("Invalid line ending");
                return bytes.toString(StandardCharsets.US_ASCII);
            }
            bytes.write(value);
            if (bytes.size() > MAX_LINE) throw new IOException("RESP line too long");
        }
    }

    private static int number(String raw) throws IOException {
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException e) { throw new IOException("Invalid RESP number", e); }
    }

    static void simple(OutputStream out, String text) throws IOException { write(out, "+" + text + "\r\n"); }
    static void error(OutputStream out, String text) throws IOException { write(out, "-ERR " + text + "\r\n"); }
    static void integer(OutputStream out, long value) throws IOException { write(out, ":" + value + "\r\n"); }
    static void bulk(OutputStream out, String value) throws IOException {
        if (value == null) { write(out, "$-1\r\n"); return; }
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        write(out, "$" + data.length + "\r\n");
        out.write(data);
        write(out, "\r\n");
    }
    private static void write(OutputStream out, String text) throws IOException { out.write(text.getBytes(StandardCharsets.UTF_8)); }
}
