
## 后续修复 (Phase 12t) - InnetVmSocksConnection.attach 资源关闭

### Phase 12t - SOCKS5 路径 attach 资源泄漏 (commit 15f4728a)

子项目 5 InnetVmSocks 路径 `attach()` 跟 Phase 12p/12q/12r/12s 同款
资源泄漏修法:

**真 bug** (之前漏修): `socksClient.connect()` 拿 `sock`, 之后走
`performHandshakeAndVersionProbe`:
```kotlin
val sock = socksClient.connect(...)
val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(
    socket = sock, ...
)
socket = sock
```

如果握手失败 (e.g. SOCKS5 server REP != 0 / JDWP handshake failed / EOF),
`socket = sock` 之前没人 close, `sock` FDs 泄漏。

`retryPolicy.retry` 默认 `maxAttempts = settings.retryMaxAttempts` (3-6 次),
每次失败泄漏 1 个 Socket FD。

**修法** (跟 AdbForwardConnection.attach line 213-227 /
AidlSocketConnection.attachLocalBridge line 281-301 同款):
```kotlin
val sock = socksClient.connect(...)
try {
    val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(...)
    socket = sock
    AttachInfo(...)
} catch (t: Throwable) {
    runCatching { sock.close() }
    throw t
}
```

#### 12t.1 - 副作用与不变性

- 5 个 connection 路径 (`AidlSocketConnection TCP` / `AdbForwardConnection` /
  `InnetVmSocksConnection` / `ShizukuConnection` / `RootConnection`) 全部走
  "handshake 失败 close socket" 模式
- `ConnectionBackedDebugger.run()` 失败路径已 cleanup (Phase 12s)
- 反复 attach 不再泄漏 FDs
- `state` 切回 `Closed(error)`, UI 行为一致

效果: 5 个 connection 全部走"无资源泄漏"模式, 反复 attach/detach 端到端
无 FDs 累积。

## 新增/修改文件 (Phase 12t)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../impl/InnetVmSocksConnection.kt` (attach try-catch close sock) |
