# Debugger Connection Layer - 子项目 2：AIDL+Socket 实现

> 日期：2026-07-02
> 依赖：子项目 1（抽象层 + 注册中心 + 偏好）
> 范围：IDE 端 AidlSocketConnection 真实实现 + JDWP 协议助手
> 后续：子项目 8 联动实现 Host 端 ADRT runtime

## 1. 选型理由

| 候选 | 利 | 弊 | 决定 |
|------|----|----|------|
| TCP loopback (`ServerSocket(127.0.0.1, 0)`) | 直接拿 `java.net.Socket` 给 `JdwpClient`，无包装器 | 需要 host 加 `INTERNET` 权限 | ✅ 采用 |
| LocalServerSocket | 更安全（abstract name 做 secret），不需要 INTERNET | 需 Socket 包装器跳过 `setTcpNoDelay` | ❌ 留作未来选项 |
| Binder/AIDL 全程 | 完全 IPC 原生 | 字节流走 binder 性能差 + 复杂 | ❌ |

Host 端 ADRT runtime 走 AIDL service + socket 转发，AIDL 这层只做
**服务发现**（"host app 是否运行 + 是否是 debug 变体"），byte 流走
TCP loopback。

## 2. 时序

```
IDE 端                                          Host 端
  |                                                |
  |-- 1. 读 host app 的 build.gradle ---------------> (子项目 8 准备)
  |    (确认 debuggable + INTERNET 权限)              |
  |                                                |
  | 2. ServerSocket(127.0.0.1, 0) -> port           |
  | 3. startActivity(host.mainActivity)              |
  |    extra = "ide.debug.aidl.port" = port          |
  |------------------------------------------------->|
  |                                                |
  |                              4. ADRT runtime 启动 (子项目 8)
  |                              5. 读 port from intent extra
  |                              6. Socket("127.0.0.1", port)
  |                              7. 发送 "JDWP-Handshake" (14 bytes)
  |<-------------------------------------------------|
  |                                                |
  | 8. accept() -> Socket                           |
  | 9. 发送 "JDWP-Handshake" 回 host               |
  | 10. 接收 host 的 handshake 完毕                |
  | 11. 发 VM.Version 命令包                        |
  | 12. 收 VM.Version 响应包 (拿到 vmId + jdwpVersion) |
  |                                                |
  | 13. state = Attached(pid, jdwpSessionId)        |
  | 14. attachDebugger -> resume()                  |
  |                                                |
  | 15. 进入正常 JDWP 调试循环                      |
```

## 3. IDE 端实现

### 3.1 状态机映射

| IDebugConnection 阶段 | 做的事 | 失败模式 |
|----------------------|--------|---------|
| `resolve()` | 用 `ActivityManager.getRunningAppProcesses()` 探测 host app 是否在前台 | `HostAppNotRunning` |
| `connect()` | 1) `ServerSocket(0)` 拿 port 2) `startActivity` 拉起 host | `HostAppNotRunning` / `DebugFlagMissing` |
| `attach()` | 1) `serverSocket.accept()` 等 host 2) JDWP 握手 3) 收 VM.Version 响应 | `Timeout` / `JdwpHandshakeFailed` |
| `detach()` | 1) 走 JDWP `VM.Dispose` 2) 关 socket | 失败也强制关 |
| `sendJdwp()` | `socket.getOutputStream().write(bytes)` | `IllegalStateException`(未 attach) |
| `receiveJdwp()` | `flow { socket.getInputStream().read(buf) -> emit }` | flow 自然结束 |
| `attachedSocket()` | 返回 clientSocket（已经是 `java.net.Socket`） | `IllegalStateException` |

### 3.2 关键文件

```
core/app/src/main/java/com/itsaky/androidide/debugger/connection/
  ├── aidl/
  │   ├── AidlJdwpProtocol.kt      # 握手 + VM.Version 助手
  │   ├── AidlHostLauncher.kt      # startActivity + intent extra
  │   └── AidlProcessProbe.kt      # ActivityManager.getRunningAppProcesses 探测
  └── impl/
      └── AidlSocketConnection.kt  # 真实实现 (替换之前的 stub)
```

### 3.3 单方案重试

`ConnectionRetryPolicy` 包 `resolve/connect/attach` 三段：
- `resolve()` 重试 3 次（每次 500ms 退避）— 给用户时间在前台启动 host
- `connect()` 重试 3 次 — 拉起 host 后给应用启动时间
- `attach()` 重试 3 次 — 等 host 的 ADRT runtime reverse-connect 上来
- **不跨方案降级** — 失败就 `Closed(retryable=true)` 让 UI 显示重试按钮

## 4. 与现有 DebuggerController 的集成

`DebuggerController.connectVia(target)` 已经在子项目 1 加了，但当时
stubs 全部抛 `UnsupportedOperationException`。子项目 2 实现完后，
调 `connectVia()` 走新连接层就真的能连上 host app（前提是 host
有 ADRT runtime 注入，那是子项目 8 的事）。

`DebugSessionLauncher` 暂时不变：旧路径 `connect(host, port)` 仍然
可以走。等子项目 2 跑通 + 子项目 8 完成后，再切到 `connectVia()`。

## 5. Host 端要求（不在本 PR 范围，但 IDE 端依赖）

- Manifest 加 `android:debuggable="true"`
- Manifest 加 `<uses-permission android:name="android.permission.INTERNET" />`
- 加 `tooling/host-adrt` AAR 到依赖（子项目 8）
- Application.onCreate 里调 `AdrtRuntime.boot(this, portIntentExtra)`（子项目 8）

## 6. 风险

| 风险 | 缓解 |
|------|------|
| 端口被占 | `ServerSocket(0)` 立即 bind + 拿实际端口，0 race |
| 127.0.0.1 不可达 | Android 同设备内 127.0.0.1 永远可达（除非 SELinux 限制，debug app 一般不受限） |
| INTERNET 权限用户拒绝 | debug 变体自动加，不影响 release |
| host 启动慢导致 attach 超时 | `attach()` 给 30s timeout（`ConnectionRetryPolicy` 3 次共 30s） |
| 多个 host app 同时调试 | 每个 connection 用独立 ServerSocket + 独立 port |
| JdwpClient.setTcpNoDelay 对 LocalSocket 抛错 | 暂时不用 LocalSocket；如果以后切，catch SocketException 即可 |

## 7. 测试

- `AidlJdwpProtocolTest`：单元测试握手字节序列 + VM.Version 响应解析
- `AidlSocketConnectionTest`：用一个 fake host (ServerSocket) 跑
  resolve/connect/attach 全流程，断言 attach 成功
- `AidlHostLauncherTest`：用 `Robolectric` 模拟 `startActivity` + 读
  intent extra（如果 Robolectric 配置允许）
- 集成测试：真机 + 真 host app — CI 跑不了，本地手动验

## 8. 实现说明（已落地，2026-07-02）

### 8.1 文件清单

```
core/app/src/main/java/com/itsaky/androidide/debugger/connection/
  aidl/
    AidlJdwpProtocol.kt      # 握手 + VM.Version (object, 纯 Kotlin)
    AidlHostLauncher.kt      # startActivity + intent extra (interface + IntentHostLauncher + Fake)
    AidlProcessProbe.kt      # ActivityManager 探测 (interface + ActivityManagerProbe + AppProcessInfo + Fake)
  impl/
    AidlSocketConnection.kt  # 真实实现 (替换之前的 stub)

ide-debugger/src/main/java/com/zerostudio/debugger/jdwp/JdwpClient.java
                              # connect(Socket,...) 里 setTcpNoDelay 包 try/catch,
                              # 兼容未来 LocalSocket 路径

core/app/src/test/java/com/itsaky/androidide/debugger/connection/
  aidl/AidlJdwpProtocolTest.kt
  impl/AidlSocketConnectionTest.kt
```

### 8.2 关键设计点

- **懒加载**：`AidlSocketConnection` 默认从 `IDEApplication.instance` 拿 launcher / probe
  上下文，但用 `by lazy` 包裹，**只在没注入 fake 时才触发**。这让单测可以
  完全绕过 Android Context 加载。
- **三段重试**：`resolve()` / `connect()` / `attach()` 三段各自用 `ConnectionRetryPolicy`
  包一层（默认 3 次指数退避，500ms 起步）。失败一次后释放 ServerSocket 防泄漏，
  下次重试重新 bind。
- **错误分类**：`mapConnectError` 把 IOException → `IoFailure`，其它 → `Unknown`；
  `mapAttachError` 根据 message 区分 `JdwpHandshakeFailed` / `Timeout` / `IoFailure` / `Unknown`，
  让 UI 知道该建议重试还是切方案。
- **detach 协议**：发 `VM.Dispose` (CommandSet 1 / Command 2) 命令，失败也强制关 socket；
  避免半挂状态。
- **JdwpClient 容错**：`connect(Socket, host, port)` 里 `setTcpNoDelay` 包了 try/catch，
  未来切 `LocalServerSocket` 时不用再改 JdwpClient。

### 8.3 与抽象层的钩子对齐

- `attachedSocket()`：直接返回 `clientSocket`（已经是 `java.net.Socket`），让
  `ConnectionBackedDebugger.run()` 能直接喂给 `JdwpClient.connect(Socket, ...)`。
- `receiveJdwp()`：开一个守护线程循环读 socket，把字节块切到 `MutableSharedFlow` 上；
  实际 JDWP 字节流消费由 `JdwpClient` 接管，`receiveJdwp()` 主要给非
  `ConnectionBackedDebugger` 路径（直接用 `IDebugConnection`）使用。

### 8.4 不在本子项目范围

- **Host 端 ADRT runtime**：子项目 8，需要在 host app 的 Application.onCreate 里
  读 intent extra 端口 + reverse-connect + 走 ADRT metadata 注入。
- **5 种实现并跑**：AIDL 方案默认，其它方案在子项目 3-7 各自实现。
- **真机集成测试**：CI 没设备，本地开发者真机/模拟器手验。
