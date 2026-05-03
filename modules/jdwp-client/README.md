# JDWP Client 模块

该模块现在已按“扩展建议”一次性落地为可直接用于 Termux IPC/跨进程调试的完整客户端骨架。

## 已实现能力（当前版本）

- 连接与握手
  - 标准 `JDWP-Handshake`。
- 传输层
  - `JdwpTransport` 抽象。
  - `SocketJdwpTransport`（支持 TCP socket，可直接用于 IPC 转发链路末端）。
- 协议编解码
  - 完整 command packet 编码。
  - reply/event packet 自动识别与解析。
  - 大端序二进制读写工具 `JdwpBuffer`。
- 异步收包循环（已实现）
  - 后台事件泵线程持续收包。
  - 请求/回复通过 `id` 自动匹配。
  - 支持注册 event listener 接收异步事件。
- VM / Class / Symbol 查询
  - `VirtualMachine.Version`
  - `VirtualMachine.AllThreads`
  - `VirtualMachine.Suspend` / `Resume`
  - `VirtualMachine.ClassesBySignature`
  - `ReferenceType.Fields`
  - `ReferenceType.Methods`
  - `ObjectReference.ReferenceType`
  - `ObjectReference.GetValues`
- EventRequest
  - `EventRequest.Set`（当前提供 VM_DEATH 快捷注册）
  - `EventRequest.Clear`
- 错误处理
  - JDWP 错误码映射 + 异常抛出。

## 快速示例

```kotlin
SocketJdwpTransport("127.0.0.1", 5005).use { transport ->
  JdwpClient(transport).use { client ->
    client.connectAndHandshake()

    client.setEventListener { event ->
      println("JDWP event: set=${event.commandSet}, cmd=${event.command}")
    }

    val version = client.vmVersion()
    val threads = client.allThreads()
    val firstThread = threads.firstOrNull()?.let(client::threadInfo)

    val classes = client.classesBySignature("Lcom/example/Main;")
    val methods = classes.firstOrNull()?.let { client.methods(it.typeId) }

    println(version)
    println(firstThread)
    println(methods)
  }
}
```

## 集成建议（core/app 或 termux 层）

- 将 `JdwpTransport` 作为可替换依赖注入：
  - 本地调试用 `SocketJdwpTransport`。
  - Termux 会话链路可封装自定义 transport（Unix domain socket / 本地代理隧道）。
- 在上层提供符号服务（Class/Method/Field/Object）缓存，避免重复 round-trip。
- 若要进一步覆盖“方法调用/断点/单步”等高级能力，可在当前结构上继续新增对应 command set API。
