English | [简体中文](README.zh-CN.md)

# stubline-spike-resp-server

A spike: a minimal server that speaks enough of the Redis protocol (RESP2) for the real `redis-cli` to talk to it. We built it to understand the wire protocol before putting Redis in front of the seat inventory. It is not meant for production.

Supported commands: `PING`, `ECHO`, `SET`, `GET`, `DEL`, sent as RESP2 arrays of bulk strings or as inline commands (a plain line such as `PING`, which is what you type into `nc`). Values are kept as raw bytes in one shared in-memory map, so binary values round-trip unchanged. Unknown commands get the same error text as Redis.

## Build and test

You need JDK 21. The Maven wrapper downloads the right Maven version by itself.

```bash
./mvnw -q verify
```

## Run

```bash
./mvnw -q exec:java
```

The server logs `RESP server listening on 6380` and keeps running until you press Ctrl+C.

## Try it with redis-cli

In a second terminal, with `redis-cli` installed:

```bash
redis-cli -p 6380 PING
redis-cli -p 6380 SET greeting hello
redis-cli -p 6380 GET greeting
```

Expected replies: `PONG`, `OK`, `hello`.

The server binds to loopback by default because it has no authentication. Stop the loopback server before the Docker example; that example explicitly binds all interfaces so a container can reach the host. Use it only on a trusted local network.

## License

This repository is licensed under the [MIT License](LICENSE).

## About this repository

This standalone spike records an experiment for Stubline. Its conclusion is recorded in the main Stubline repository ADR; the observed result is described above.

The code and documents in this repository were written with the help of AI tools; every recorded output comes from actually running them.
