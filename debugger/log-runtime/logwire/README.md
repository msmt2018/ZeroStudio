# logwire

ZeroStudio 的日志流传输协议库。定义了 IDE 端与宿主端之间传输 logcat 日志
的 wire format,两端共用此模块以保证消息布局永远一致。

> 纯 Java 库 (java-library),不依赖 Android SDK。IDE 端和宿主端都依赖它,
> 是两端唯一共享的协议定义点。

## Wire Format

每条消息的帧格式:

```
┌──────────────┬──────────┬─────────────────┬───────────────┐
│  magic (4B)  │ type(1B) │  length (4B BE) │  payload (NB) │
│  "LOGW"      │          │  payload 字节数  │               │
└──────────────┴──────────┴─────────────────┴───────────────┘
```

- `magic`: ASCII `"LOGW"` (`0x4C4F4757`),每帧开头
- `type`: 消息类型 (见下表)
- `length`: payload 字节数 (big-endian, 最大 1 MiB)
- `payload`: 消息体

所有多字节整数使用 big-endian。所有字符串使用 4 字节长度前缀 + UTF-8。

## 消息类型

| 类型 | ID | payload | 说明 |
| --- | --- | --- | --- |
| `LOG_PAYLOAD` | `0x01` | `LogPayload` | 一条日志记录 |
| `HANDSHAKE` | `0x02` | `Handshake` | 连接建立后的握手 |
| `HEARTBEAT` | `0x03` | 空 | 心跳保活 |
| `BYE` | `0x04` | 空 | 正常断开 |
| `ERROR` | `0x05` | `ErrorPayload` | 错误通知 |

## 日志级别

镜像 `android.util.Log`:

| 常量 | 值 | 对应 |
| --- | --- | --- |
| `LOG_VERBOSE` | 2 | `Log.v` |
| `LOG_DEBUG` | 3 | `Log.d` |
| `LOG_INFO` | 4 | `Log.i` |
| `LOG_WARN` | 5 | `Log.w` |
| `LOG_ERROR` | 6 | `Log.e` |
| `LOG_ASSERT` | 7 | `Log.wtf` |

## 目录结构

```
src/main/java/com/itsaky/androidide/logwire/
├── WireConstants.java    协议常量 (magic / version / 类型 / 级别) + BE 读写工具
├── Handshake.java        握手 payload (protocolVersion / pid / pkg / sessionId)
├── FrameCodec.java       帧编解码 (magic + type + length + payload 的读写)
├── LogPayload.java       日志 payload (level / tag / message / timestamp / pid)
├── LogLevel.java         日志级别枚举
├── ErrorPayload.java     错误 payload (code / message)
└── LogWireClient.java    客户端,连接 LogSocketServer 接收日志流
```

## 握手流程

```
宿主端 (ide-log-plugin)               IDE 端
        │                                │
        │──── TCP connect ──────────────>│
        │                                │
        │<─── HANDSHAKE (version=1) ────│  (或宿主端先发)
        │                                │
        │──── LOG_PAYLOAD (日志) ───────>│
        │──── LOG_PAYLOAD (日志) ───────>│
        │                                │
        │──── HEARTBEAT ───────────────>│  (保活)
        │                                │
        │──── BYE ─────────────────────>│  (断开)
```

`Handshake` 中的 `protocolVersion` 必须匹配,否则立即发送 `BYE` 断开。

## API 用法

```java
// 宿主端: 发送日志
LogPayload payload = new LogPayload(
    WireConstants.LOG_INFO, "MyTag", "hello world",
    System.currentTimeMillis(), Process.myPid());
byte[] frame = FrameCodec.encode(WireConstants.TYPE_LOG_PAYLOAD, payload.write());
outputStream.write(frame);

// IDE 端: 接收日志
byte[] header = readN(inputStream, 9);  // magic(4) + type(1) + length(4)
byte type = header[4];
int length = WireConstants.readIntBE(header, 5);
byte[] payloadBytes = readN(inputStream, length);
if (type == WireConstants.TYPE_LOG_PAYLOAD) {
    LogPayload log = LogPayload.read(payloadBytes);
    System.out.println(log.tag + ": " + log.message);
}
```

## 依赖

- `utilities/shared` (api)
- `google.guava` / `google.gson`
- `androidx.annotation`
- `logging/logger`

## 相关模块

- [`ide-log-plugin`](../ide-log-plugin/README.md) — 宿主端,使用本协议发送日志
- [`ide-debugger`](../../Breakpoint-debugger/ide-debugger/README.md) — IDE 端,使用本协议接收日志

## License

GPL-3.0-or-later (same as AndroidIDE)
