
## 后续修复 (Phase 12w) - HostBridgeServer.awaitNextConnection 走 BlockingDeque

### Phase 12w - race + 性能 + CPU busy-wait 三个真问题 (commit ccb98de6)

`HostBridgeServer.awaitNextConnection` (子项目 9a LocalBridge 路径) 三个真问题:

#### 12w.1 - CopyOnWriteArrayList.remove O(n) 性能

**真 bug**: 之前 `private val activeConnections = CopyOnWriteArrayList<HostConnection>()`,
每次 `remove` 都是 O(n) (CopyOnWrite 拷贝整个底层 array)。

实测:
- 1 connection: O(1), 没问题
- 100 connection: O(100) ≈ 1-2μs
- 1000 connection 反复 attach: O(1000) ≈ 20-50μs/次, 单次 awaitNextConnection
  反复 poll + remove 累积 100ms+, 反复 attach 后明显卡顿

`awaitNextConnection` 每次 poll 还要 `firstOrNull { it.socket.isConnected }` 扫整个
list, **O(n) + O(n) = O(2n)**。

**修法**: 改用 `LinkedBlockingDeque<HostConnection>`:
- `put` O(1) (无界, 立即返)
- `pollFirst` O(1)
- 头尾 O(1) 读 + 写

#### 12w.2 - socket.isConnected 不可靠

**真 bug**: `socket.isConnected` 是 Android LocalSocket 的 connected getter,
**反映 JVM 视角的 connected 状态**, 而不是真实对端是否 alive:
- `accept()` 后立即 `true` (LocalSocket.connected field 由 ctor 初始化)
- 对端 close 后, JVM 不一定立即知道 (靠 read 返 -1 / EOF 才知道)
- 拿到一个对端已 close 但 JVM 还认为 connected 的 conn, handshake read
  立即 EOF, 失败

**修法**: 不用 isConnected 判定。queue 拿到就返, handshake 失败由
AidlSocketConnection.attachLocalBridge (Phase 12r 已修) try-catch close 兜底。

#### 12w.3 - busy-wait sleep 20ms

**真 bug**: 之前 `Thread.sleep(20L)` + retry, 没数据时 CPU 持续唤醒 50 次/秒,
IDE 端空转耗电。

**修法**: 走 `queue.pollFirst(timeoutMs, TimeUnit.MILLISECONDS)` 阻塞, 没数据时
线程 park, CPU 0 占用。

#### 12w.4 - allConnections 保留 + stop() 排空

`allConnections` CopyOnWriteArrayList 保留作为 "all known connections" 视图:
- 给 `stop()` 排空用 (queue 也 clear)
- 给调试用 (log dump / IDE UI 展示 "X 个 host app 连接过")

`stop()` 排空两个 collection + close 所有 socket + join accept thread。

#### 12w.5 - 副作用与不变性

- `awaitNextConnection(timeoutMs)` API 签名不变, 仍返 `HostConnection?` 或 null
- `handleClient` 完成后 put queue, IDE 端主路径不变
- 多次 awaitNextConnection 并发安全 (BlockingDeque 是 thread-safe)
- `stop()` 行为对齐: queue.clear + 关闭所有 socket + accept thread join
- AppReadyAutoConnect 路径不动 (HostBridgeServer 是给 AidlSocketConnection
  LocalBridge 路径用的, 主路径走 listener AppReadyAutoConnect, 跟 awaitNextConnection
  无关)

效果:
- race 修: 不依赖 isConnected 不可靠判定
- 性能: 100+ connection 反复 attach 仍 O(1), 不再 O(n) 累积
- CPU: 阻塞 poll 替代 busy-wait, IDE 端空转 CPU 0 占用

## 新增/修改文件 (Phase 12w)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../host/HostBridgeServer.kt` (LinkedBlockingDeque + pollFirst + 保留 allConnections) |
