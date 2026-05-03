# JDWP Client（全功能架构版）

## 协议分层（已落地）

1. Transport 层：`JdwpTransport` / `SocketJdwpTransport`
2. Packetize 层：`JdwpCodec` + `JdwpBuffer`
3. JDWP Commands 层：`JdwpClient`（Set 1/2/9/11/15 等）
4. JDI-like 层：`JdwpSymbolRepository`（类/方法缓存解析）

## 核心能力

- 严格握手：`JDWP-Handshake` 14 字节双向校验。
- 异步收包：后台 pump + `CompletableFuture` 按 Packet ID 匹配回复。
- Event 处理：支持 event listener 与 EventRequest.Set / Clear。
- IDSizes：握手后自动调用 `VirtualMachine.IDSizes`，按目标 JVM 动态 ID 长度解析。
- 符号链路：`ClassesBySignature -> Methods/Fields`。
- 对象读写：`ObjectReference.GetValues / SetValues`。
- 远程方法调用：`ObjectReference.InvokeMethod`（支持 options，如 `INVOKE_SINGLE_THREADED`）。

## 已实现命令（摘要）

- VM(Set=1): `Version`, `AllThreads`, `IDSizes`, `Suspend`, `Resume`, `ClassesBySignature`
- ReferenceType(Set=2): `Fields`, `Methods`
- ObjectReference(Set=9): `ReferenceType`, `GetValues`, `SetValues`, `InvokeMethod`
- ThreadReference(Set=11): `Name`, `Status`
- EventRequest(Set=15): `Set`, `Clear`

## 使用示例

```kotlin
SocketJdwpTransport("127.0.0.1", 5005).use { transport ->
  JdwpClient(transport).use { client ->
    client.connectAndHandshake()

    client.setEventListener { event ->
      println("event: set=${event.commandSet}, cmd=${event.command}")
    }

    val repo = JdwpSymbolRepository(client)
    val cls = repo.resolveClass("Lcom/example/Main;")
    val method = repo.resolveMethod(cls, "run")

    println(client.vmVersion())
    println(method)
  }
}
```
