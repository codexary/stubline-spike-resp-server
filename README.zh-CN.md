[English](README.md) | 简体中文

# stubline-spike-resp-server

这是一个 spike：一个最小的服务器，实现了足够多的 Redis 协议（RESP2），能让真正的 `redis-cli` 和它对话。我们在考虑给座位库存前面加一层 Redis；正式接入之前，先写它来弄清客户端和服务器之间到底传哪些字节。它不用于生产环境。

支持的命令：`PING`、`ECHO`、`SET`、`GET`、`DEL`，可以用 RESP2 数组加 bulk string 发送，也可以用 inline 命令发送（直接一行文字，比如在 `nc` 里敲 `PING`）。值始终按原始字节保存在一个共享的内存 Map 里，所以二进制数据能原样往返。未知命令返回和 Redis 相同的错误文字。

## 构建与测试

需要 JDK 21。Maven wrapper（`./mvnw`）会自己下载项目指定版本的 Maven，不用另外安装。

```bash
./mvnw -q verify
```

## 运行

```bash
./mvnw -q exec:java
```

服务器输出 `RESP server listening on 6380`，之后一直运行，按 Ctrl+C 停止。

## 用 redis-cli 试一试

另开一个终端；如果本机装了 `redis-cli`：

```bash
redis-cli -p 6380 PING
redis-cli -p 6380 SET greeting hello
redis-cli -p 6380 GET greeting
```

预期回复依次是 `PONG`、`OK`、`hello`。

### 不安装 Redis 也能用

Docker 可以运行官方的 `redis:8` 镜像，镜像里自带 `redis-cli`。`--rm` 表示命令结束后删除这个容器；`host.docker.internal` 是容器访问你本机上服务器时用的名字（macOS 和 Windows 上的 Docker Desktop 会提供它；Linux 上要在 `redis:8` 之前加 `--add-host=host.docker.internal:host-gateway`）。

```bash
SPIKE_BIND_ADDRESS=0.0.0.0 ./mvnw -q exec:java
```
在另一个终端运行：

```bash
docker run --rm -it redis:8 redis-cli -h host.docker.internal -p 6380 PING
```

已在 macOS 的 Docker Desktop 29.7.2 上测试。

## 录下 redis-cli 发出的字节

`scripts/capture-pipe-bytes.sh` 用 `nc` 代替服务器监听端口，对它运行一次 `redis-cli --pipe`，再用 `xxd` 打印录到的字节。从输出能看到 `redis-cli` 在 pipe 最后一条命令之后追加的内容：一个空行，然后是一条带 20 个随机字节的 `ECHO`，它会等服务器把这 20 个字节原样送回来。

```bash
scripts/capture-pipe-bytes.sh
```

服务器默认只监听本机回环地址，因为它没有身份认证。运行 Docker 示例前先停止本地服务器；该示例显式监听所有接口，让容器能够访问宿主机。仅在可信本地网络运行。

## 许可证

本仓库采用 [MIT License](LICENSE)。

## 关于本仓库

这个独立小项目记录 Stubline 的实验。结论写在 Stubline 主仓库的 ADR 中；上文记录了测试结果。

本仓库的代码与文档借助 AI 工具编写；记录的输出都来自实际运行。
