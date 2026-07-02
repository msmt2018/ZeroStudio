# 断点调试器连接层 - 子项目 9: 集成 - 自动 attach + 构建时注入器扩展

> 日期: 2026-07-02
> 子项目 ID: 9
> 状态: 🟡 设计 + 部分实现 (待补 9d 构建时注入器)

## 背景

子项目 1-8 完成了 6 选 1 连接方案的 IDE 端 + host 端 runtime, 但端到端集成
存在两个问题:

1. **宿主应用启动后 IDE 端断点调试器没反应**:
   - `AppReadySignalWatcher` 监听到 host app 通过 logcat 发出的 "READY" 信
     号, 但**只通知, 不实际 attach**
   - `AutoAttachManager` 只在 IDE 重启 + 同 packageName 时**按保存的 host:port**
     拉起旧连接, 不支持新连接层
   - 现状: 用户手动启动 host app (如点击 launcher 图标), IDE 完全不响应

2. **构建时断点调试器参数/内容等注入器不完整**:
   - `IdeDebuggerInitScriptPlugin` 注入了 `ide-log-plugin` AAR, 但没注入
     `ide-debugger-host` (子项目 8 新建的模块)
   - 没设置 IDE LocalServerSocket name 到 Manifest placeholder
   - 没设置 `HostAttachAgent` 启动入口

## 目标

完成端到端集成:

- 宿主 app 启动后, IDE 端**自动**用合适的连接方案 (默认 AIDL+Socket fallback)
  完成 attach, 用户无需点 "Debug" 按钮
- `IdeDebuggerInitScriptPlugin` 注入 `ide-debugger-host` AAR + 设置必要的
  Manifest placeholder, 让 host 端能反连 IDE LocalServerSocket

## 设计

### 9a. IDE 端 HostBridgeServer (LocalServerSocket 管理)

新文件: `core/app/src/main/java/com/itsaky/androidide/debugger/connection/host/HostBridgeServer.kt`

- 在 IDE 启动时 bind 一个 `LocalServerSocket`, 名字固定
  (e.g. `ide-debug-bridge-{uid}`), 通过 system property / SharedPreferences
  让 host 端可读
- `accept()` 后接收 host 端 HELLO 行: `HELLO pkg=<pkg> pid=<pid>`
- 解析后丢给 `AppReadyAutoConnect.onHostConnected(socket, pkg, pid)`
- 同时保留: 接收的 socket 包装成 LocalSocket 提供给 `AidlSocketConnection`

### 9b. IDE 端 AppReadyAutoConnect

新文件: `core/app/src/main/java/com/itsaky/androidide/debugger/AppReadyAutoConnect.java`

- 订阅 `AppReadySignalWatcher`, 监听 logcat "READY" 信号
- 同时订阅 `HostBridgeServer.onHostConnected`
- 收到信号后:
  1) 查 `ConnectionRegistry` 找匹配 packageName 的 `IDebugConnection`
  2) 没有就创建一个 AIDL+Socket fallback (`AidlSocketConnection`)
  3) 调用 `connection.resolve() + connect() + attach()` (后台线程)
  4) 成功: 通知 `DebuggerController` (既有), UI 切到 debug 视图
  5) 失败: log + 不重试 (用户可手动重连, 避免在没真的 host 时刷错)

### 9c. host 端 DebuggerBootstrapProvider 增强

修改: `ide-log-plugin/src/main/java/com/zerostudio/logplugin/bootstrap/DebuggerBootstrapProvider.java`

启动后除了 `JdwpServer.start(0)` + logcat "READY", 还:

1) 读 Manifest placeholder `ide_local_server_name` (= IDE LocalServerSocket 名字)
2) 用反射 (避免硬依赖) 调 `HostAttachAgent.connectToIdeLocalServer(name)`
3) `openLocalAbstractJdwpSocket()`, 在两 socket 间做字节桥
4) IDE 端 `HostBridgeServer` accept 后就能拿到这个 socket, 走 JDWP

**注意**: 这要求 `ide-debugger-host` AAR 已经被注入到 host app; 当下注入
由 9d 实现。

### 9d. IdeDebuggerInitScriptPlugin 扩展

修改: `tooling/plugin/src/main/java/com/itsaky/androidide/gradle/IdeDebuggerInitScriptPlugin.kt`

新增:
1) `ide-debugger-host` AAR 注入 (从 Maven coord 拉取, 加到 runtimeClasspath)
2) Manifest placeholder:
   - `ide_local_server_name` = `ide-debug-bridge-{project.group}-{project.name}`
     (固定但 per-project 避免冲突)
   - `host_attach_agent_class` = `com.itsaky.androidide.zerostudio.ide.debugger.host.HostAttachAgent`
3) 给 host 端 `applicationVariants.all` 注入 `manifestPlaceholders["ide_local_server_name"]`

### 9e. AidlSocketConnection 走 HostBridgeServer (可选)

修改: `core/app/src/main/java/com/itsaky/androidide/debugger/connection/impl/AidlSocketConnection.kt`

AIDL+Socket 默认实现保持 TCP `ServerSocket` 路径 (基于 Intent extra 传端口),
**新增 overload** `attachViaLocalBridge(localSocketName)` 走 LocalServerSocket:

```kotlin
suspend fun attachViaLocalBridge(localSocketName: String): Result<AttachInfo> {
    // host 端启动后会自动反连 IDE LocalServerSocket
    // IDE 端 HostBridgeServer 已经 accept, 直接拿 socket
    val sock = HostBridgeServer.awaitConnection(localSocketName, timeoutMs)
    return AidlJdwpProtocol.performHandshakeAndVersionProbe(sock, ...)
}
```

如果 host 端 Manifest placeholder 已设置 (9d), 走这个新路径; 否则 fallback
到现有 TCP `ServerSocket` 路径。

## 行为矩阵

| 启动方式 | host 配置 | IDE 配置 | 走哪条路径 |
|---|---|---|---|
| 用户点 IDE "Debug" | (任意) | (任意) | 现有 TCP `ServerSocket` 路径 (AIDL+Socket) |
| 用户手动启动 host app | 注入 `ide-debugger-host` | `HostBridgeServer` 启动 | 9a + 9c 反向连接 (新) |
| 同上 (没注入 host) | 只有 `ide-log-plugin` | `AppReadyAutoConnect` 启动 | 9b TCP 直连 host jdwpPort (fallback) |
| 同上 (用户也禁用自动 attach) | (任意) | Settings 关闭 | 不 attach, 用户手动 |

## 实施顺序

1. **9a** HostBridgeServer (新文件) - IDE 端 LocalServerSocket
2. **9b** AppReadyAutoConnect (新文件) - 收到信号自动 attach
3. **9c** DebuggerBootstrapProvider 增强 (修改) - host 端反连
4. **9d** IdeDebuggerInitScriptPlugin 扩展 (修改) - 构建时注入
5. **9e** AidlSocketConnection overload (修改) - 走 LocalBridge

## 测试

- `HostBridgeServerTest`: bind + accept + HELLO 解析
- `AppReadyAutoConnectTest`: 收到 mock 信号 -> 触发 connect
- `IdeDebuggerInitScriptPluginTest`: 验证 placeholder 注入 (修改既有测试)

## 风险 / 限制

- 9c 要求 host 端 `ide-debugger-host` AAR 注入, 旧项目 (没重新 build) 走 9b fallback
- 9d 的 `ide-debugger-host` Maven coord 必须跟 `ide-log-plugin` 在同一仓库
  (`ide-debugger-host:1.0.0`), 否则 Gradle 拉不到
- 9a 的 LocalServerSocket name 需要唯一, 多个 IDE 实例同时跑会冲突
  (per-user uid 应该够, 但要测)
- 9b 的 retry 策略: 不要因为 host 短暂未启动就刷错, 限 1 次尝试 + 用户手动重试
