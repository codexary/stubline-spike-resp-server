package com.elsworne.stubline.spike.resp;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

public final class RespMain {
    private static final Logger LOG = Logger.getLogger(RespMain.class.getName());
    private RespMain() { }
    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 6380 : Integer.parseInt(args[0]);
        var server = new RespServer(port);
        server.start();
        LOG.info("RESP server listening on " + server.port());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { server.close(); } catch (IOException ignored) { } }));
        new CountDownLatch(1).await();
    }
}
