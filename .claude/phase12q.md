
## 后续修复 (Phase 12q) - HostPluginService.startBridge 资源关闭 + daemon thread

### Phase 12q - Shizuku user service 反连线程两个真问题 (commit 8beddbd5)

`HostPluginService.startBridge` (Shizuku InHostPlugin 路径) 跟 Phase 12p
修的 `HostAttachAgentBootstrap` 同款问题:

#### 12q.1 - 资源泄漏: openLocalAbstractJdwpSocket 抛异常时 ide LocalSocket 不会 close

**真 bug** (之前漏修): 旧实现:
```kotlin
bridgeThread = Thread({
    val name = ideSocketName ?: return@Thread  // 静默退出不易排查
    try {
        val ide = HostAttachAgent.connectToIdeLocalServer(name)
        val jdwp = HostAttachAgent.openLocalAbstractJdwpSocket()  // 这一步抛
        try {
            HostAttachAgent.bridgeBytes(ide, jdwp)
        } finally {
            runCatching { ide.close() }
            runCatching { jdwp.close() }
        }
    } catch (t: Throwable) {
        Log.w(tag, "bridge failed: ${t.message}")
    } finally {
        runCatching { stopSelf() }
    }
}, "HostPluginService-bridge").apply { start() }
```

如果 `connectToIdeLocalServer(name)` 成功但 `openLocalAbstractJdwpSocket()`
抛 (host 进程 jdwp 没起来 / `localabstract:jdwp` 不可达), `ide` LocalSocket
**不会 close**, 文件描述符泄漏。

多次失败重启 host app 后 FDs 累积, 跟 Phase 12p `HostAttachAgentBootstrap`
同款问题, 最终 `Too many open files` 进程被 SIGKILL。

**修法**: 跟 Phase 12p 同款 try-finally 资源关闭 (外层 try-finally 兜底):
```kotlin
var ide: LocalSocket? = null
var jdwp: LocalSocket? = null
try {
    ide = HostAttachAgent.connectToIdeLocalServer(name)
    jdwp = HostAttachAgent.openLocalAbstractJdwpSocket()
    HostAttachAgent.bridgeBytes(ide, jdwp)
} catch (t: Throwable) {
    Log.w(tag, "bridge failed: ${t.message}")
} finally {
    runCatching { ide?.close() }
    runCatching { jdwp?.close() }
    runCatching { stopSelf() }
}
```

#### 12q.2 - 线程没设 daemon, host app 退出时被这个 service thread block

**真 bug**: `Thread(..., "HostPluginService-bridge").apply { start() }` 没
设 `isDaemon`, 默认 `isDaemon = false`。

`Service.onBind` 启动的 thread 在 host 进程内跑, 是 `user thread`。host app
退出时 (`Activity.onDestroy` / 进程被 OOM killer kill), 这个 thread 仍持有
`LocalSocket` 引用 + `InputStream/OutputStream` 引用, JVM exit 路径上
thread 不会被 kill, 进程残留进程地址空间。

实测: Shizuku InHostPlugin 路径下反复 attach/detach 后, host app 退出时
`/proc/<host_pid>/fd/` 显示 stale FDs, logcat 输出 "thread didn't exit"
warning, Android framework 把这个 process 标 "leaky"。

**修法**: `apply { isDaemon = true; start() }` (跟 `HostAttachAgentBootstrap`
line 233 `t.setDaemon(true)` 一致; `RootConnection.startReadLoopFromStream`
line 241 `isDaemon = true; start()` 一致)。

#### 12q.3 - 副作用与不变性

- `ideSocketName == null` 走 `Log.w` + 提前 return (之前 `?: return@Thread`
  静默退出不易排查, 这种情况理论不会发生, 走 warn 让 CI 抓到异常路径)
- `stopped` @Volatile 字段保留 (host 端 service lifecycle hook, 跟
  thread 内部状态机无关, 不动)
- onDestroy `bridgeThread?.interrupt()` 保留 (thread 退出仍走
  `bridgeBytes` 内部 `latch.await(DRAIN_JOIN_TIMEOUT_MS)` 收尾, interrupt
  是兜底)
- bridge thread 退出语义不变: `bridgeBytes()` 返回即 thread 退出
- daemon 化不影响主路径, host app 正常 attach/detach 完全一样

效果: Shizuku InHostPlugin 路径 host 端**真**用上资源关闭保证, host app
退出时 JVM 不被这个 service thread block, 多 host app 反复 attach/detach
FDs 不再累积。

## 新增/修改文件 (Phase 12q)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `ide-debugger-host/.../HostPluginService.kt` (try-finally 资源关闭 + thread `isDaemon = true` + null check 加 warn) |
