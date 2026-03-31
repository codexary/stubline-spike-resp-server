English | [简体中文](FINDINGS.zh-CN.md)

# Findings

## What we observed

The small RESP2 server handles `PING`, `ECHO`, `SET`, `GET`, and `DEL`, including binary values. Real `redis-cli` exposed two parser defects missed by the initial tests, including the extra bytes sent by `redis-cli --pipe`. The repository contains the fixes and a byte-capture script.

## Backend decision

Even a narrow wire parser needs compatibility work against real clients. The backend keeps its hand-written HTTP parser for framing experiments and uses the JDK `HttpServer` for ticketing routes. See [backend ADR 0002 — Use the JDK HTTP server](https://github.com/codexary/stubline-backend/blob/main/docs/adr/0002-use-jdk-http-server.md).
