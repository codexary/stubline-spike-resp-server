package com.elsworne.stubline.spike.resp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RespServer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(RespServer.class.getName());
    private final ServerSocket server;
    private final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();
    private final ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor();
    private final ExecutorService acceptor = Executors.newSingleThreadExecutor();

    public RespServer(int port) throws IOException { server =
        new ServerSocket(
            port,
            50,
            InetAddress.getByName(System.getenv().getOrDefault("SPIKE_BIND_ADDRESS", "127.0.0.1"))); }
    public int port() { return server.getLocalPort(); }

    public void start() {
        acceptor.submit(() -> {
            while (!server.isClosed()) {
                try { Socket socket = server.accept(); clients.submit(() -> serve(socket)); }
                catch (SocketException e) { if (!server.isClosed()) LOG.log(Level.WARNING, "Accept failed", e); }
                catch (IOException e) { LOG.log(Level.WARNING, "Accept failed", e); }
            }
        });
    }

    private void serve(Socket socket) {
        try (socket; var in = socket.getInputStream(); var out = socket.getOutputStream()) {
            while (true) {
                List<String> args = RespCodec.readCommand(in);
                if (args == null) return;
                execute(args, out);
                out.flush();
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Client disconnected or sent incomplete command", e);
        }
    }

    private void execute(List<String> args, java.io.OutputStream out) throws IOException {
        String command = args.getFirst().toUpperCase(Locale.ROOT);
        switch (command) {
            case "PING" -> {
                if (args.size() == 1) RespCodec.simple(out, "PONG");
                else if (args.size() == 2) RespCodec.bulk(out, args.get(1));
                else RespCodec.error(out, "wrong number of arguments for PING");
            }
            case "ECHO" -> {
                if (args.size() == 2) RespCodec.bulk(out, args.get(1));
                else RespCodec.error(out, "wrong number of arguments for ECHO");
            }
            case "SET" -> {
                if (args.size() == 3) { values.put(args.get(1), args.get(2)); RespCodec.simple(out, "OK"); }
                else RespCodec.error(out, "wrong number of arguments for SET");
            }
            case "GET" -> {
                if (args.size() == 2) RespCodec.bulk(out, values.get(args.get(1)));
                else RespCodec.error(out, "wrong number of arguments for GET");
            }
            case "DEL" -> {
                if (args.size() < 2) RespCodec.error(out, "wrong number of arguments for DEL");
                else {
                    long removed = args.subList(1, args.size()).stream().distinct().filter(key -> values.remove(key) != null).count();
                    RespCodec.integer(out, removed);
                }
            }
            default -> RespCodec.error(out, "unknown command");
        }
    }

    @Override public void close() throws IOException { server.close(); acceptor.shutdownNow(); clients.shutdownNow(); }
}
