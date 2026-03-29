English | [简体中文](README.zh-CN.md)

# stubline-spike-resp-server

A spike: a minimal server that speaks enough of the Redis protocol (RESP2) for the real `redis-cli` to talk to it. We built it to understand the wire protocol before putting Redis in front of the seat inventory. It is not meant for production.

## Build and test

You need JDK 21. The Maven wrapper downloads the right Maven version by itself.

```bash
./mvnw -q verify
```

The server binds to loopback by default because it has no authentication. Stop the loopback server before the Docker example; that example explicitly binds all interfaces so a container can reach the host. Use it only on a trusted local network.

## License

This repository is licensed under the [MIT License](LICENSE).

## About this repository

This standalone spike records an experiment for Stubline. Its conclusion is recorded in the main Stubline repository ADR; the observed result is described above.

The code and documents in this repository were written with the help of AI tools; every recorded output comes from actually running them.
