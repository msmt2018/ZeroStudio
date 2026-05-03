# JDWP Client 模块

该模块提供可直接在项目内复用的 JDWP 协议客户端能力，目标覆盖：

- 跨进程调试。
- IPC socket 转发场景（例如 Termux 客户端与会话终端进程之间）。
- 在上层模块（如 `core/app`）中按符号维度访问 JVM 内部对象：类、方法、字段等。

## 当前能力

- 标准 `JDWP-Handshake` 握手。
- 命令包与回复包编码/解码。
- VM 基础命令：
  - `VirtualMachine.Version`
  - `VirtualMachine.ClassesBySignature`
- 引用类型命令：
  - `ReferenceType.Fields`
  - `ReferenceType.Methods`

## 快速示例

```kotlin
SocketJdwpTransport("127.0.0.1", 5005).use { transport ->
  JdwpClient(transport).use { client ->
    client.connectAndHandshake()
    val version = client.vmVersion()
    val classes = client.classesBySignature("Lcom/example/Main;")
    val methods = client.methods(classes.first().typeId)
  }
}
```

## 扩展建议

- 按 JDWP Command Set 分层扩展命令实现（ThreadReference / ObjectReference / EventRequest 等）。
- 引入异步收包循环，支持事件通知与请求回复并发。
- 在 IPC 场景外包一层会话鉴权/多路复用协议。
