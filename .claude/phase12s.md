
## 后续修复 (Phase 12s) - ConnectionBackedDebugger.run 失败路径 cleanup, 不再泄漏 FDs

### Phase 12s - 失败路径 cleanup (commit 91074099)

`ConnectionBackedDebugger.run()` 走完 `resolve` -> `connect` -> `attach`
后, 拿 socket 构造 JdwpClient:
```kotlin
try {
    val socket = connection.attachedSocket()
    val client = JdwpClient()
    client.connect(socket, "", 0)
    debugger = Debugger.forClient(client)
} catch (uoe: UnsupportedOperationException) {
    log.warn("...")
    return Result.failure(uoe)  // 不 cleanup
} catch (t: Throwable) {
    log.error("...", t)
    return Result.failure(t)  // 不 cleanup
}
```

**真 bug** (跟 Phase 12p/12q/12r 同款):

`attachedSocket()` 抛 `UnsupportedOperationException` (AidlSocketConnection
LocalBridge 路径 / RootConnection, 这两条路径走 flow API 不暴露
java.net.Socket) 或 `client.connect()` 抛 (e.g. protocol handshake 失败),
之前**直接 return failure, 没调 `connection.detach()` / `connection.release()`**。

后果:
- `state` 仍卡在 `Attached`, 用户观察 `state.collectAsState()` 看到 "已连接"
  但 debugger 为 null, 行为不一致
- FDs (`LocalSocket` / `ServerSocket` / `adb forward` 端口) 没人 release,
  反复 attach 失败后 FDs 累积泄漏, 跟之前几波修的同款问题
- `Release` 路径有 (state -> Idle), 但因为 state 卡在 Attached, GC 后资源
  仍占着 socket name / abstract namespace, 下次同名字 LocalServerSocket
  bind 失败

**修法**: 两种 catch 都加 `runCatching { connection.detach() }` +
`runCatching { connection.release() }` (跟 `shutdown()` line 86-98 风格一致):
```kotlin
} catch (uoe: UnsupportedOperationException) {
    log.warn("ConnectionBackedDebugger: $uoe (this is expected for stub impls)")
    runCatching { connection.detach() }
    runCatching { connection.release() }
    return Result.failure(uoe)
} catch (t: Throwable) {
    log.error("ConnectionBackedDebugger: bridging failed", t)
    runCatching { connection.detach() }
    runCatching { connection.release() }
    return Result.failure(t)
}
```

UOE 仍走 `log.warn` (production 端这俩 connection 不应该用 ConnectionBackedDebugger
包装, 它们是 flow API 路径, 走 [ConnectionBackedDebugger] 算 misuse), 其他
Throwable 走 `log.error`。

#### 12s.1 - 副作用与不变性

- `run()` 失败语义: state 切回 `Closed(null)` (走 `release()`) 或 `Closed(error)`
  (走 `detach()`), 跟 `shutdown()` 路径一致
- FDs 全部释放, 反复 attach 不再累积
- UI 端 `state.collectAsState()` 看到 `Closed` 而不是卡在 `Attached`,
  行为一致
- `shutdown()` 路径不动 (line 86-98), 它已经正确 cleanup
- 成功路径完全不变 (return success(attachInfo) 不动)

效果: `ConnectionBackedDebugger.run()` 任何失败路径都正确释放底层
connection 资源, 跟 `shutdown()` 走完的 cleanup 行为完全对齐。

## 新增/修改文件 (Phase 12s)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../ConnectionBackedDebugger.kt` (2 个 catch 都加 detach + release 兜底) |
