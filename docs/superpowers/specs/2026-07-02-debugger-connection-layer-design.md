# Debugger Connection Layer 重构设计

> 日期：2026-07-02
> 范围：子项目 1 —— 抽象层 + 注册中心 + 偏好设置
> 分支：`trae/agent-2PhVDV`（不新建分支，沿用约定）
> 后续：子项目 2~8 各自的设计稿另起文档

## 1. 背景与目标

ZeroStudio IDE 当前的 JDWP 调试器连接层（`core/app/.../debugger/` 下的
`DebugSessionLauncher` + `DebuggerController` + `ShizukuBridge` + `RunAsBridge`）
存在以下结构性问题：

- **缺少统一的传输抽象**：每种连接方式（loopback / Shizuku / run-as）都
  散落在独立类里，没有共同接口；新增方案要改 4~5 个文件。
- **没有状态机**：UI 侧靠回调 + null 检查判断"现在能不能发命令"，实现
  复杂且容易出竞态。
- **目标包名跟连接解耦失败**：`DebuggerController.connect(host, port)`
  不知道 `host`/`port` 是给哪个包用的；`AutoAttachManager` 自己另存一份
  (host, port, pkg) 三元组，重复且容易漂移。
- **断点信息无落点**：用户设置的断点只存在 IDE 内存里，没有写进宿主
  app 的 dex（参考 AIDE 的 `ADRTMetadata.FILE_IDS` 模式），所以宿主
  app 端没有自己的断点表，attach 后只能单方面等 JDWP EventRequest。
- **Shizuku 的能力没被充分利用**：现有 `ShizukuBridge` 只能跑 `pm install` /
  `run-as` / 简单 shell，没用到 binder 直连、隐藏 API、SOCKS5 出口等。
- **缺少独立后端 / 独立 app 的连接方案**：内网虚拟机（光速虚拟机、
  vmos、虚拟大师）和 USB / LAN 设备没有实现。

## 2. 重构目标

抽象出统一的 `IDebugConnection` 接口，6 种连接方式各自实现一份，注册
中心根据 DataStore 里的配置工厂化创建。

- 用户在 **设置** 页面里**手动 6 选 1**（默认 `AIDL_SOCKET`）。
- 6 种实现**完全独立**，运行时只存在一种，不互相切换、不跨方案降级。
- 每种实现内部自带**单方案重试**（3 次 + 指数退避），失败时上报
  `ConnectionError`，UI 显示 + 让用户点"重试"或切方案。
- 抽象层只关心"**resolve → connect → attach**"生命周期和 JDWP 字节流；
  目标包名、宿主体就绪监听、断点注入由其它子项目（断点注入生成器
  子项目 6）负责。

## 3. 抽象层接口契约

### 3.1 `ConnectionType`

```kotlin
sealed class ConnectionType(
    val id: String,
    val displayName: String,
    val requiresRoot: Boolean,
    val requiresShizuku: Boolean,
) {
    object AidlSocket   : ConnectionType("aidl_socket",   "AIDL Socket (免Root)",         false, false)
    object Shizuku      : ConnectionType("shizuku",       "Shizuku 桥接",                 false, true)
    object Root         : ConnectionType("root",          "Root 直连 JDWP",               true,  false)
    object InnetVmSocks : ConnectionType("innet_vm_socks","内网虚拟机 (SOCKS5 代理)",     false, false)
    object InnetVmAdb   : ConnectionType("innet_vm_adb",  "内网虚拟机 (ADB 端口转发)",     false, false)
    object UsbLan       : ConnectionType("usb_lan",       "USB / 局域网 ADB",             false, false)
}
```

> 注：内网虚拟机原本合并成 1 个方案（内含 SOCKS5 + ADB 两种实现），
> 用户反馈是两种**完全独立**的连接技术栈（Proxy 协议 vs adb forward），
> 拆成 `InnetVmSocks` + `InnetVmAdb` 两个独立方案。共 6 种方案。
>
> 旧 id `"innet_vm"` 通过 [ConnectionType.fromIdCompat] 自动映射到
> `InnetVmSocks`，保证从旧版本升级的用户不会回退到默认 AIDL socket。

### 3.2 `ConnectionState`（6 状态）

```kotlin
sealed class ConnectionState {
    object Idle        : ConnectionState()
    object Resolving   : ConnectionState()
    object Connecting  : ConnectionState()
    object Handshaking : ConnectionState()
    data class Attached(val pid: Int, val jdwpSessionId: Long) : ConnectionState()
    data class Closed(val error: ConnectionError? = null) : ConnectionState()
}
```

UI 端 `collectAsState()` 直接消费，规则：
- `Idle / Resolving / Connecting / Handshaking` → 状态栏"连接中…"，调试
  按钮**禁用**。
- `Attached` → 启用所有调试按钮。
- `Closed(error != null && retryable)` → 状态栏红字 + 错误码，"重试"按钮
  触发**当前方案内**重试，不切方案。
- `Closed(error != null && !retryable)` → 状态栏红字 + 错误码 + "去设置"
  跳转链接。

### 3.3 `ConnectionError`

```kotlin
sealed class ConnectionError(val retryable: Boolean) {
    object PermissionDenied    : ConnectionError(false)
    object HostAppNotRunning   : ConnectionError(true)
    object DebugFlagMissing    : ConnectionError(false)  // android:debuggable != true
    object BuildConfigNotDebug : ConnectionError(false)  // BuildConfig.DEBUG != true
    object Timeout             : ConnectionError(true)
    object JdwpHandshakeFailed : ConnectionError(true)
    object PortResolveFailed   : ConnectionError(true)
    object NetworkUnreachable  : ConnectionError(true)
    data class IoFailure(val cause: Throwable) : ConnectionError(true)
    data class Unknown(val cause: Throwable)   : ConnectionError(true)
}
```

### 3.4 `ConnectionCapability`

实现可声明自身能力，UI 决定显示哪些高级选项：

```kotlin
sealed class ConnectionCapability {
    object CanInstallInHost  // 能往宿主进程注入 stub（Shizuku C、Root）
    object CanReadProcNet    // 能读 /proc/net/unix 找 jdwp socket
    object CanExposeSocks    // 能开 SOCKS5 出口（Shizuku D、InnetVmSocks）
    object NeedsHostForeground // 需要宿主应用在前台（AIDL Socket 等）
}
```

### 3.5 `DebugTarget`

```kotlin
data class DebugTarget(
    val packageName: String,
    val mainActivity: String,
    val debuggable: Boolean = true,
)
```

由 `DebugConnectionFactory.create(type, target, settings)` 显式传入，
不再从 `host:port` 反推。

### 3.6 `IDebugConnection`

```kotlin
interface IDebugConnection {
    val type: ConnectionType
    val target: DebugTarget
    val state: StateFlow<ConnectionState>
    val capabilities: Set<ConnectionCapability>

    suspend fun resolve(): Result<ResolveInfo>      // Resolving -> Connecting
    suspend fun connect(): Result<Unit>             // Connecting -> Handshaking
    suspend fun attach(): Result<AttachInfo>        // Handshaking -> Attached
    suspend fun detach()                            // Attached -> Closed
    fun release()                                   // 强制释放

    suspend fun sendJdwp(bytes: ByteArray)
    fun receiveJdwp(): Flow<ByteArray>
}
```

### 3.7 辅助类型

```kotlin
data class ResolveInfo(
    val transportKind: String,    // "tcp" / "unix" / "aidl" / "binder" / "adb-forward"
    val endpoint: String,         // "127.0.0.1:5005" / "@jdwp-1234" / "binder:0x12"
    val requiresHostRunning: Boolean,
)

data class AttachInfo(
    val pid: Int,
    val jdwpSessionId: Long,
    val jdwpDescription: String,
)
```

### 3.8 `HostSideBridge`（Shizuku C 路径用）

```kotlin
interface HostSideBridge {
    /** Shizuku 注入后,宿主内 stub 反向连接,把 Socket 交给 IDE */
    suspend fun awaitBridgeChannel(): Socket
}
```

### 3.9 `ConnectionRetryPolicy`

```kotlin
class ConnectionRetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 500,
    val multiplier: Double = 2.0,
) {
    suspend fun <T> retry(block: suspend (attempt: Int) -> Result<T>): Result<T>
}
```

每个实现内部在 `resolve/connect/attach` 三段各自包 `retry {}`，**单方案
内**重试，**不跨方案降级**。

## 4. 注册中心

```kotlin
object DebugConnectionRegistry {
    fun create(
        type: ConnectionType,
        target: DebugTarget,
        settings: DebugConnectionSettings,
    ): IDebugConnection
}
```

实现 `when (type)` 工厂方法，6 个分支对应 6 个 `IDebugConnection` 实现
类。**子项目 1 阶段 6 个实现都是 stub**（仅 `resolve/connect/attach` 抛
`NotImplementedError`，不阻塞后续子项目编译）。真实实现在子项目 2~7
陆续补齐。

## 5. DataStore + 偏好设置

- 新增 `utilities/preferences/.../DebugConnectionPreferences.kt`，存
  `activeType: String`（默认 `"aidl_socket"`）和各实现的子配置
  （`AidlSocketConfig` / `ShizukuConfig` / `RootConfig` /
  `InnetSocksConfig` / `InnetAdbConfig` / `UsbLanConfig`）。
- `core/app/.../preferences/debuggerPrefExts.kt` 新增 `Debugger` 设置页
  （挂在 `BuildAndRunPreferences` 同一级），UI：
  - 单选"默认连接方式"（6 选 1）
  - 各方案参数表单（端口、shizuku 包名、SOCKS5 代理 / ADB 端口转发 / USB-LAN 设备地址）
  - 高级开关（"宿主必须在前台" / "自动重试"）

## 6. 与现有 `DebuggerController` 的集成

不破坏现有 `DebuggerController.connect(host, port)` 签名。新增
`DebuggerController.connectVia(target: DebugTarget)` 内部走抽象层：

1. 读 `DebugConnectionPreferences.activeType`
2. `DebugConnectionRegistry.create(type, target, settings)`
3. 调 `connection.resolve() / connect() / attach()` 串成 flow
4. 拿到 JDWP 通道后，把字节流转给现有 `Debugger` 实例（构造一个
   `ConnectionBackedDebugger` 适配器，复用 `ide-debugger` 里
   `JdwpPacketCodec` + 事件派发）
5. `Debugger` 后续的 `resume/stepOver/...` 全部通过适配器转发到
   `connection.sendJdwp(bytes)`，收到的包 `connection.receiveJdwp()` 后
   喂给 `Debugger.handlePacket`

**两条路径并存**：保留旧 `connect(host, port)`，新增 `connectVia(target)`
作为"调试入口"按钮（`DebugSessionLauncher.start()` 终点）的默认路径。
旧路径仅在抽象层异常时降级使用 + 日志告警。

## 7. AppReadySignalWatcher 定位

保持**独立**于抽象层（用户已确认）。`DebugSessionLauncher` 在 build →
install → launch 之后用 `AppReadySignalWatcher` 等宿主 logcat 的
`ZeroStudioDebug READY pkg=... jdwp=...` 信号，**拿到信号才调
`connectVia(target)`**。这跟抽象层是否就绪解耦，IDE 启动期 / 调试
入口关闭期都能监听。

## 8. 后续子项目

| 子项目 | 内容 | 状态 |
|--------|------|------|
| 1. 抽象层（本文档） | 接口 + 状态机 + 注册中心 + 偏好 | **本 PR** |
| 2. AIDL+Socket 实现 | 跟 AIDE ADRT 同思路，宿主内 stub 起 `LocalServerSocket` 反向连 | 待 PR |
| 3. Shizuku 实现 | 4 子路径 A/B/C/D + `ShizukuBinderWrapper` | 待 PR |
| 4. Root 实现 | `Runtime.exec("su -c ...")` + `/proc/net/unix` 探测 | 待 PR |
| 5. 内网 VM (SOCKS5 代理) | SOCKS5 客户端 + jdwp-tunnel over proxy | 待 PR |
| 6. 内网 VM (ADB 端口转发) | `adb connect host:port` + `adb forward` (虚拟机内 adbd) | 待 PR |
| 7. USB / LAN ADB | `adb connect ip:port` + `adb forward` (物理设备 / 真机) | 待 PR |
| 8. 断点注入生成器 | 构建期生成 `.kt` 写到宿主 dex + 同步到 stub | 待 PR |

## 9. 不在本 PR 范围

- 6 个真实连接实现（子项目 2~7）
- 宿主体改造 / `DebuggerBootstrapProvider` 重写（子项目 8 联动）
- Shizuku 仓库同步（用户提到的 git 重新拉取操作另开 task）
- `core/app/.../debugger/LogcatReader` / `LogWireClient` 调整
- UI 偏好页面与现有 IDE 主题的视觉对齐（仅占位）

## 10. 测试

- `IDebugConnection` 状态机：给定一个 fake 实现，断言 `resolve/connect/attach`
  路径触发 `StateFlow` 状态变更序列。
- `DebugConnectionRegistry.create()`：6 种 type 都返回非 null 实例。
- `ConnectionRetryPolicy`：`maxAttempts=3, initial=500, mult=2.0` 时，
  第二次失败应该在 500ms 后，第三次在 1000ms 后。
- `ConnectionBackedDebugger` 字节流回路：往 `sendJdwp` 写一帧，
  `receiveJdwp` 能读回同一帧。

## 11. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 6 个 stub 占位代码误用 | 抛 `UnsupportedOperationException("sub-project N not implemented")`，运行期立刻崩，避免静默走错路径 |
| 新老双路径漂移 | 旧 `connect(host, port)` 标 `@Deprecated`，3 个 PR 周期后删除 |
| 旧 `id = "innet_vm"` 升级漂移 | [ConnectionType.fromIdCompat] 把它自动映射到 `InnetVmSocks` |
| Kotlin Coroutines 引入新依赖 | 用 `kotlinx-coroutines-android`（项目已依赖），不引新包 |
| 偏好页与现有 UI 风格不一致 | 占位 minimal UI，视觉留到 UI polish PR |
