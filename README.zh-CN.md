[English](README.md) | 简体中文

# stubline-spike-resp-server

这是一个 spike：一个最小的服务器，实现了足够多的 Redis 协议（RESP2），能让真正的 `redis-cli` 和它对话。我们在考虑给座位库存前面加一层 Redis；正式接入之前，先写它来弄清客户端和服务器之间到底传哪些字节。它不用于生产环境。

## 构建与测试

需要 JDK 21。Maven wrapper（`./mvnw`）会自己下载项目指定版本的 Maven，不用另外安装。

```bash
./mvnw -q verify
```

服务器默认只监听本机回环地址，因为它没有身份认证。运行 Docker 示例前先停止本地服务器；该示例显式监听所有接口，让容器能够访问宿主机。仅在可信本地网络运行。

## 许可证

本仓库采用 [MIT License](LICENSE)。

## 关于本仓库

这个独立小项目记录 Stubline 的实验。结论写在 Stubline 主仓库的 ADR 中；上文记录了测试结果。

本仓库的代码与文档借助 AI 工具编写；记录的输出都来自实际运行。
