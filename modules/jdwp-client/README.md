# JDWP Client（远程 RPC 调试版）

本模块把 JDWP 当作“远程过程调用系统”实现，面向 Android <-> Termux JVM 的 TCP 字节流调试。

## 分层架构
1. Transport: `JdwpTransport` / `SocketJdwpTransport`
2. Packetize: `JdwpCodec` + `JdwpBuffer`
3. Command Mapping: `JdwpClient`
4. JDI-like: `JdwpSymbolRepository`

## 关键远程能力
- 严格握手：`JDWP-Handshake`。
- 心跳保活：定时 `VirtualMachine.Version`，超时触发断线回调。
- 动态 IDSizes：自动适配 32/64 位 ID。
- 远程对象持有：`DisableCollection` / `EnableCollection`。
- 方法调用链路：线程挂起 -> MethodsByName -> InvokeMethod -> 返回值/异常对象。
- 调用栈：`ThreadReference.Frames`。
- 断点/事件：`EventRequest.Set/Clear` + 异步事件回调。
- 全类列表：`VirtualMachine.AllClasses`。

## 快速验证（建议第一步）
```kotlin
val client = JdwpClient(SocketJdwpTransport("127.0.0.1", 5005))
client.connectAndHandshake()
println(client.allThreads())
println(client.allClasses().take(20))
```

## 变量修改示例
```kotlin
val cls = client.classesBySignature("Lcom/example/Main;").first()
val field = client.fields(cls.typeId).first { it.name == "counter" }
client.setObjectValues(ObjectId(0x1234), listOf(ValueToSet(field.id, TaggedValue('I'.code.toByte(), 42))))
```
