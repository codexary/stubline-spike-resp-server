package com.elsworne.stubline.spike.resp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A small binary-safe RESP2 server for observing TCP framing with redis-cli. */
public final class RespServer implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(RespServer.class);
  private final ServerSocket server;
  private final ConcurrentHashMap<Bytes, byte[]> values = new ConcurrentHashMap<>();
  private final ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor();
  private final ExecutorService acceptor = Executors.newSingleThreadExecutor();

  public RespServer(int port) throws IOException {
    server =
        new ServerSocket(
            port,
            50,
            InetAddress.getByName(System.getenv().getOrDefault("SPIKE_BIND_ADDRESS", "127.0.0.1")));
  }

  public int port() {
    return server.getLocalPort();
  }

  public void start() {
    acceptor.submit(this::acceptClients);
  }

  private void acceptClients() {
    while (!server.isClosed()) {
      try {
        Socket socket = server.accept();
        clients.submit(() -> serve(socket));
      } catch (SocketException e) {
        if (!server.isClosed()) {
          LOG.warn("Accept failed", e);
        }
      } catch (IOException e) {
        LOG.warn("Accept failed", e);
      }
    }
  }

  private void serve(Socket socket) {
    try (socket;
        var in = socket.getInputStream();
        var out = socket.getOutputStream()) {
      while (true) {
        List<byte[]> args = RespCodec.readCommand(in);
        if (args == null) {
          return;
        }
        execute(args, out);
        out.flush();
      }
    } catch (IOException e) {
      LOG.debug("Client disconnected or sent incomplete command", e);
    }
  }

  private void execute(List<byte[]> args, OutputStream out) throws IOException {
    String command =
        new String(args.getFirst(), StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT);
    switch (command) {
      case "PING" -> {
        if (args.size() == 1) {
          RespCodec.simple(out, "PONG");
        } else if (args.size() == 2) {
          RespCodec.bulk(out, args.get(1));
        } else {
          RespCodec.error(out, "wrong number of arguments for 'ping' command");
        }
      }
      case "ECHO" -> {
        if (args.size() == 2) {
          RespCodec.bulk(out, args.get(1));
        } else {
          RespCodec.error(out, "wrong number of arguments for 'echo' command");
        }
      }
      case "SET" -> {
        if (args.size() == 3) {
          values.put(new Bytes(args.get(1)), args.get(2).clone());
          RespCodec.simple(out, "OK");
        } else {
          RespCodec.error(out, "wrong number of arguments for 'set' command");
        }
      }
      case "GET" -> {
        if (args.size() == 2) {
          RespCodec.bulk(out, values.get(new Bytes(args.get(1))));
        } else {
          RespCodec.error(out, "wrong number of arguments for 'get' command");
        }
      }
      case "DEL" -> {
        if (args.size() < 2) {
          RespCodec.error(out, "wrong number of arguments for 'del' command");
        } else {
          long removed =
              args.subList(1, args.size()).stream()
                  .map(Bytes::new)
                  .distinct()
                  .filter(key -> values.remove(key) != null)
                  .count();
          RespCodec.integer(out, removed);
        }
      }
      default -> unknownCommand(args, out);
    }
  }

  private static void unknownCommand(List<byte[]> args, OutputStream out) throws IOException {
    StringBuilder message = new StringBuilder("unknown command '");
    message.append(new String(args.getFirst(), StandardCharsets.US_ASCII));
    message.append("', with args beginning with:");
    for (int i = 1; i < args.size(); i++) {
      message.append(" '");
      message.append(new String(args.get(i), StandardCharsets.UTF_8));
      message.append("'");
    }
    RespCodec.error(out, message.toString());
  }

  @Override
  public void close() throws IOException {
    server.close();
    acceptor.shutdownNow();
    clients.shutdownNow();
  }

  private record Bytes(byte[] data) {
    @Override
    public boolean equals(Object other) {
      return other instanceof Bytes bytes && Arrays.equals(data, bytes.data);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(data);
    }
  }
}
