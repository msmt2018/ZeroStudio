# JDWP 协议说明

> Phase F3 — JDWP (Java Debug Wire Protocol) 在 ZeroStudio IDE 中的实现说明。
> 覆盖 handshake、packet format、命令集、事件集与扩展位。

## 1. 概述

JDWP 是 JDI (Java Debug Interface) 与目标 JVM 之间的事实标准调试协议。
ZeroStudio 调试器 (ide-debugger 模块) 是 JDWP 客户端，目标端
(ide-log-plugin 的 `JdwpServer`) 是 JDWP 服务端。两者在 loopback 上
用 TCP 直接通信。

参考规范：
- [JEP-12: JDWP](https://openjdk.org/jeps/12)
- [JVMTI / JDWP 文档](https://docs.oracle.com/javase/8/docs/platform/jvmti/jvmti.html)

## 2. 传输层

### 2.1 Handshake

TCP 连接建立后，双方各发送 14 字节 handshake：

```
JDWP-Handshake   =   "JDWP-Handshake"   (14 字节, ASCII, 不带 NUL)
```

实现：
- 客户端 (`ide-debugger/JdwpClient`) 读 14 字节校验，校验通过后 echo。
- 服务端 (`ide-log-plugin/JdwpServer`) 读 14 字节后 echo 回去。

echo 是必须的：协议要求双方校验对方确实在发 JDWP 而不是别的字节流。

### 2.2 Packet Format

每个 JDWP 报文：

```
+----+--------+------------------+
| Len|  ID    |  Flags + Cs + Cmd|
+----+--------+------------------+
|  4 |   4    |  1 + 1 + 1 + ..  |
+----+--------+------------------+
|       Payload (variable)       |
+--------------------------------+
```

- `Length` (4 字节, BE) — 整个 packet 的长度，包括 length 字段自身
- `Id` (4 字节, BE) — 客户端生成；服务端 echo
- `Flags` (1 字节) — 0x80 表示 reply packet
- `CommandSet` (1 字节) — 命令集 ID
- `Command` (1 字节) — 命令集内编号
- `Payload` — 命令相关

`ide-debugger/jdwp/JdwpPacketCodec` 负责序列化/反序列化。

## 3. Command Sets

| Set | 名称 | 实现状态 | 说明 |
|-----|------|----------|------|
| 1   | VirtualMachine | 部分 | Version / IDSizes / Resume / Suspend / Exit / ClassesBySignature |
| 2   | ReferenceType | 部分 | Signature / ClassLoader / SourceFile / Methods |
| 3   | ClassType | 部分 | Superclass / SetValues / InvokeMethod |
| 4   | Method | 部分 | LineTable / VariableTable |
| 5   | ObjectReference | 部分 | ReferenceType / GetValues / SetValues |
| 6   | StringReference | 完整 | Value |
| 8   | StackFrame | 部分 | GetValues / SetValues / ThisObject |
| 9   | ThreadReference | 部分 | Name / Suspend / Resume / Frames |
| 10  | ThreadGroupReference | 最小 | Name / Parent |
| 11  | ArrayReference | 部分 | Length / GetValues |
| 13  | MethodEntry | - | 不实现 |
| 14  | Monitor | - | 不实现 |
| 15  | EventRequest | 部分 | Set / Clear / ClearAllBreakpoints |
| 64  | Event | 发送方 | VM_START / BREAKPOINT / SINGLE_STEP / EXCEPTION |

## 4. Event Kinds

服务端的 `Event` 包 (cs=64, cmd=100) 可携带多个事件，每个事件的 layout：

```
suspendPolicy (1 byte)        # 0=NONE, 1=EVENT_THREAD, 2=ALL
eventCount   (4 bytes BE)
[eventKind (1 byte), requestId (4 bytes BE), body... ] * eventCount
```

| Kind | 名称 | 服务端实现 |
|------|------|-----------|
| 0x40 | VM_START | ✅ 由 `JdwpServer.emitVMStartEvent` 主动发送 |
| 0x46 | BREAKPOINT | 订阅；由 `SourceLocator.installBreakpointAt` 注入 |
| 0x01 | SINGLE_STEP | 订阅；由 `SourceLocator.enableSingleStepEvents` |
| 0x04 | EXCEPTION | 订阅；由 `SourceLocator.enableExceptionEvents` |
| 0x0F | CLASS_PREPARE | 订阅；由 `SourceLocator.enableClassPrepare` |

## 5. 关键命令详解

### 5.1 VirtualMachine.Version (cs=1, cmd=1)

请求：空。

响应：
```
description (string)   # "ZeroStudio ide-log-plugin JDWP server"
major       (int)     # 1
minor       (int)     # 9
version     (int)     # 0
vmName      (string)  # "ART"
```

### 5.2 VirtualMachine.ClassesBySignature (cs=1, cmd=2)

请求：`signature (string)`，例如 `Lcom/example/Foo;`

响应：
```
count (int)
[ typeTag (1 byte), classId (8 bytes), status (4 bytes) ] * count
```

实现见 `SourceLocator.installBreakpoint` 内的 ClassesBySignature 调用。

### 5.3 Method.LineTable (cs=4, cmd=1)

请求：`classId (8 bytes) + methodId (8 bytes)`

响应：
```
start  (8 bytes)  # first code index
end    (8 bytes)  # one-past-last code index
lines  (4 bytes)  # count
[ codeIndex (8 bytes) + lineNumber (4 bytes) ] * count
```

### 5.4 StackFrame.GetValues (cs=8, cmd=1) — 批量

请求：
```
threadId (8 bytes)
frameId  (8 bytes)
count    (4 bytes)
[ slot (4 bytes) + tag (1 byte) ] * count
```

响应：
```
count (4 bytes)
[ tag (1 byte) + value (tag-specific) ] * count
```

这是 Phase H.1 的批量优化点：单次 GetValues 拉取整个 frame 的所有变量。
比循环调用 `Field.GetValue` 节省 50+ 次 roundtrip。

### 5.5 EventRequest.Set (cs=15, cmd=1) — BREAKPOINT

请求：
```
eventKind    (1 byte)    # 0x46
suspendPolicy(1 byte)    # 2 = ALL
modCount     (4 bytes)
[ modKind (1 byte) + modData ] * modCount
```

BREAKPOINT 的 Location 修饰符：
```
modKind     (1 byte)    # 0x01 = LOCATION
classId     (8 bytes)
methodId    (8 bytes)
codeIndex   (8 bytes)
```

可选的 COUNT 修饰符：
```
modKind     (1 byte)    # 0x07 = COUNT
hitCount    (4 bytes)
```

响应：`requestId (4 bytes)`，客户端会缓存到 `Breakpoint.requestId`。

## 6. 错误码

| Code | 名称 | 含义 |
|------|------|------|
| 0    | NONE | 成功 |
| 11   | INVALID_THREAD | threadId 不在 VM 中 |
| 13   | INVALID_FRAME | frameId 不在 thread 中 |
| 20   | NOT_IMPLEMENTED | 命令未实现 |
| 35   | INVALID_SLOT | slot 越界 |
| 50   | ABSENT_INFORMATION | 无行号表 |
| 51   | INVALID_EVENT_TYPE | eventKind 不支持 |
| 99   | NOT_IMPLEMENTED (服务端私有) | 目标端自定义 |

## 7. 协议版本协商

- 服务端 (ide-log-plugin) 在 1.0.0 时不支持版本协商。
- 客户端 (ide-debugger) 在 `JdwpClient.connect()` 内首先做 handshake，
  handshake 失败立即抛 `IOException("Bad JDWP handshake")`。
- 后续 PR-D9 可以加 `HandshakeExtension`：在 14 字节后追加
  `version (int) + capabilities (int)`，由两端协商。

## 8. 流控 / 心跳

- 协议本身没有 heartbeat。
- 客户端 `DebugSessionHeartbeat` (每 5s) 发送一条空的 `EventRequest` 包
  检测对端是否还活着。失败 3 次后断开重连。
- 服务端不需要主动发心跳 (loopback 不会丢包)；

## 9. 大小限制

| 字段 | 上限 | 出处 |
|------|------|------|
| Packet payload | 16 MB | `JdwpPacketCodec.MAX_PACKET_SIZE` |
| String | 1 MB | `ByteBuf.MAX_STRING_LEN` |
| Variables per GetValues | 256 | 服务端栈帧限制 |

## 10. 调试

- `adb logcat -s JdwpServer:V JdwpClient:V` 看两端的协议 trace。
- `tcpdump -i lo -w /tmp/dump.pcap port 5005` (loopback) 抓包。
- Wireshark 加载 dump + `jdwp` 协议解码器。
