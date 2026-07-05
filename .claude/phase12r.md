
## 后续修复 (Phase 12r) - HostBridgeServer HELLO 读阻塞真 bug + LocalBridge 资源关闭

### Phase 12r - LocalBridge 路径两个真 bug 修 (commit 9f0d3e75)

子项目 9a LocalBridge (HostBridgeServer) + 9e (AidlSocketConnection LocalBridge 路径)
两个真问题:

#### 12r.1 - HostBridgeServer.handleClient HELLO 读永远卡 5s 后丢弃

**真 bug** (用户痛点: "手动启动 host app IDE 端没反应"):

`readLineWithTimeout` 之前用非阻塞 polling:
```kotlin
while (System.currentTimeMillis() < deadline) {
    if (input.available() > 0) {  // 经常返 0 即使 kernel buffer 已有数据
        val n = input.read(buf)
        ...
    } else {
        Thread.sleep(20L)
    }
}
```

**bug 链路**:
- Android `LocalSocket.getInputStream().available()` 在底层是 stream-oriented
  socket, JVM 层不知道 kernel buffer size, **经常返 0 即使 kernel 已收到
  host 端的 HELLO 字节**
- 走 20ms sleep + `available() == 0` 检查, 一直 sleep 直到 5s timeout
- `readLineWithTimeout` 返空字符串 `""`
- `parseHello("")` 返 null (line 212 `if (raw.isBlank()) return null`)
- `client.close()` 丢弃
- AppReadyAutoConnect 不触发, IDE 端没反应

实测: 任何 host app 启动 → ContentProvider 走 `HostAttachAgentBootstrap` 写
"HELLO pkg=...\n" → IDE `HostBridgeServer.handleClient` 5s 后丢连接, **端到端
"手动启动 host app" 路径完全跑不通**。

**修法**: 改用 `BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readLine()`
+ `client.soTimeout = 5_000`:
- `readLine()` 阻塞读一行 (到 `\n` 或 EOF)
- `soTimeout = 5s` 内来数据就解析, 超时抛 `SocketTimeoutException`
- handleClient catch `SocketTimeoutException` 当 timeout 处理
- 删 `readLineWithTimeout` (dead code, 改用标准库 API)

效果: HELLO 5s 内能正常读解析, "手动启动 host app IDE 端响应" 端到端跑通。

#### 12r.2 - AidlSocketConnection.attachLocalBridge 资源泄漏

**真 bug** (跟 Phase 12p/12q 同款):

`attachLocalBridge` 拿 `conn = bridge.awaitNextConnection(timeoutMs)` 后, 走
`performHandshakeAndVersionProbe`:
```kotlin
val ls = conn.socket
val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(...)
localBridgeSocket = ls
...
```

如果 `performHandshakeAndVersionProbe` 抛 (e.g. JDWP handshake 失败 / EOF /
timeout), `localBridgeSocket = ls` **没执行**, 但 `conn.socket` (LocalSocket)
**也没 close**。

`retryPolicy.retry` 默认 `maxAttempts = settings.retryMaxAttempts` (默认 3-6 次),
每次失败都泄漏 1 个 LocalSocket FD。多次 attach 失败后 FDs 累积, 跟
`HostAttachAgentBootstrap` / `HostPluginService` 之前漏的 case 同款。

**修法**: handshake try-catch close ls:
```kotlin
val ls = conn.socket
try {
    val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(...)
    localBridgeSocket = ls
    ...
} catch (t: Throwable) {
    runCatching { ls.close() }
    throw t
}
```

跟 `AdbForwardConnection.attach()` line 213-227 同款修法。

#### 12r.3 - 副作用与不变性

- HELLO read 改阻塞 readLine 后, 端到端延迟降低: 之前固定 5s timeout
  (无 HELLO 数据时) → 现在有数据时立即读, 无数据时 5s 兜底 timeout
- `readLineWithTimeout` 删, dead code 减少
- LocalBridge 资源关闭: 多次 retry 失败不再累积 FDs
- `HostBridgeServer.awaitNextConnection` 拿走的 conn 由 `attachLocalBridge`
  负责生命周期 (成功 → `localBridgeSocket` 持有; 失败 → finally close),
  行为跟 `AdbForwardConnection.attach` 完全对齐
- `HostBridgeServer.parseHello` 不动 (Phase 12i 已修过 EOF 安全)
- `HandleClient` 的 try-catch outer 仍兜底 (BufferedReader 自身异常 / IO 异常)

效果:
- "手动启动 host app" 端到端跑通 (HELLO 5s 内能 read 到)
- LocalBridge 路径无资源泄漏 (跟 TCP 路径 `AdbForwardConnection` 同等待遇)

## 新增/修改文件 (Phase 12r)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../host/HostBridgeServer.kt` (readLine 替代 polling + 删 dead code) |
| 改 | `core/app/.../impl/AidlSocketConnection.kt` (attachLocalBridge try-catch close) |
