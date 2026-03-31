[English](FINDINGS.md) | 简体中文

# 实验结论

## 观察

这个 RESP2 小服务器支持 `PING`、`ECHO`、`SET`、`GET`、`DEL` 和二进制值。真实 `redis-cli` 暴露了最初测试没发现的两处解析缺陷，其中涉及 `redis-cli --pipe` 在命令后发送的额外字节。仓库保留了修复和抓取字节的脚本。

## 对后端的影响

即使协议范围很小，也要拿真实客户端验证兼容性。后端保留手写 HTTP 解析器做报文边界实验，售票路由则交给 JDK `HttpServer`。参见[后端 ADR 0002：Use the JDK HTTP server](https://github.com/codexary/stubline-backend/blob/main/docs/adr/0002-use-jdk-http-server.md)。
