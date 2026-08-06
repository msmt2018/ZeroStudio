# ZeroStudio 断点调试器不可用原因调查报告

> 日期：2026-08-06
> 范围：`debugger/Breakpoint-debugger`、`core/app/src/main/java/com/itsaky/androidide/debugger/connection`、宿主侧 `ide-debugger-host`。

## 1. 官方标准基线

### 1.1 Java/Kotlin 断点调试的标准通道

- Java/Kotlin 断点调试应以 JDWP（Java Debug Wire Protocol）作为调试器与目标 VM 的线协议。Oracle 官方 JDWP 规范说明：JDWP 是 debugger 与被调试 VM 之间的通信协议，允许同一调试器连接同机或远程 VM：<https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/jdwp-spec.html>。
- JDWP transport 的连接标准包含两步：先建立连接，再交换固定 ASCII 字符串 `JDWP-Handshake` 来确认双方确实是 JDWP 调试端点：<https://docs.oracle.com/en/java/javase/11/docs/specs/jdwp/jdwp-transport.html>。
- Android Java/Kotlin 断点调试本质上仍需要 JDWP-compliant debugger。Android 官方调试文档也将 Java/Kotlin 调试与 native 调试分开描述，Java/Kotlin 断点由 Android Studio 调试器处理，native 断点由 LLDB 处理：<https://developer.android.com/studio/debug>。

### 1.2 Native/C/C++ 断点调试的标准通道

- Android native 调试标准是 LLDB。Android Studio 官方文档说明 native code 使用 LLDB 调试，断点命中时 Debug 窗口提供 Frames、Variables、Watches 等能力：<https://developer.android.com/studio/debug>。
- NDK 命令行 native 调试也应走 NDK/LLDB 工具链；`ndk-gdb` 文档说明它现在历史上是 gdb 名称但实际用于启动命令行 native debugging，并要求 app debuggable：<https://developer.android.com/ndk/guides/ndk-gdb>。

### 1.3 AIDL 的标准用途与本问题的边界

- AIDL 是 Android 进程间 IPC 机制。Android 官方 AIDL 文档说明 `.aidl` 构建后生成 `IBinder` 接口，client 绑定 service 后通过 IBinder 调用方法：<https://developer.android.com/develop/background-work/services/aidl>。
- 因此 AIDL 可以用于应用业务 IPC、控制面信令、服务启动，但不应该替代或混入 JDWP 包流作为断点调试数据通道。断点调试数据面应保持单一 JDWP 字节流，否则会出现握手竞争、包边界错乱、read loop 抢流等问题。

## 2. 当前源码能力盘点

### 2.1 已具备的 Java/Kotlin JDWP 调试核心

`debugger/Breakpoint-debugger/ide-debugger` 已经包含自研 JDWP client 与基础调试 API：

- `JdwpClient`：实现 JDWP socket 连接、`JDWP-Handshake`、命令发送、事件读取。
- `JdwpPacket` / `JdwpPacketCodec` / `JdwpPacketReader`：实现 JDWP 包模型、编码/解码与 reader loop。
- `CommandSet` / `CommandCodes` / `JdwpEvents` / `JdwpError`：覆盖 JDWP 命令集、事件、错误码常量。
- `Debugger`：封装断点、线程、栈帧、变量读取、resume/step/eval 等 IDE 侧调试能力。
- `DebugEventBus`：解析 JDWP Composite Event 并分发 VMStart、Breakpoint、Step、Exception、ClassPrepare 等事件。

结论：仓库内置了 Java/Kotlin 断点调试器核心，但能否可用取决于连接层是否把 IDE 与目标 VM 之间的字节流稳定地、唯一地连接到 JDWP socket。

### 2.2 Native/LLDB 调试能力缺口

当前 `debugger/Breakpoint-debugger` 中未发现完整 LLDB client / lldb-server lifecycle / DAP adapter / symbol-server 管理链路。存在 `DwarfSymbolResolver`、`NativeAddress` 等符号/地址模型，但它们不足以构成 Android native 断点调试器。

结论：当前项目不能称为“完整 Java/Kotlin + LLDB 断点调试器”。Java/Kotlin JDWP 核心存在；LLDB native 断点调试仍缺：

1. lldb-server 部署/启动/端口转发；
2. LLDB client 或 DAP 适配层；
3. native breakpoint、thread/frame/register/memory 操作；
4. ABI/符号/so 映射与源码定位；
5. 与 Java/Kotlin 调试会话并存的 mixed debugging 协调。

## 3. 无法使用/不稳定的主要原因

### 3.1 AIDL/IPC 路径与 JDWP 数据面混用

旧连接层把 `AidlSocketConnection` 作为默认方案，同时 Shizuku Auto 中也会优先考虑 `WifiAdb` / `Binder` 等非纯 JDWP 字节流路径。问题包括：

- `ConnectionType.AidlSocket` 曾经是默认连接类型；自动 attach 也默认选择它。
- `ShizukuConnection` 的 `WifiAdb` 子路径复用 `AidlSocketConnection`，导致 Shizuku 连接层仍可能绕回 AIDL-style 反连。
- `Binder` 子路径把 Shizuku binder/fd transfer 作为调试传输设想，当前实现又 fallback 到 InHostPlugin，语义不清晰。
- 多条路径同时具备“启动宿主/反连/握手/读取”的行为，容易出现 JDWP handshake 被其它 reader 消费、AIDL 控制消息与 JDWP bytes 混用、attach 后 socket 所有权不唯一等问题。

标准判断：JDWP transport 要求连接建立后双方交换 `JDWP-Handshake` 并在同一条流上按 JDWP 包协议通信。AIDL 的 IBinder 调用是 IPC/RPC，不是 JDWP transport。

### 3.2 默认连接类型不符合“只保留 JDWP 协议传输”

旧代码默认使用 `ConnectionType.AidlSocket`。如果用户未手动切换，断点调试默认不是 ADB/JDWP forward，也不是 root/JDWP 直连，而是 AIDL Socket 方案。这与“IDE 端和宿主客户端只通过 JDWP 请求断点调试器”的目标冲突。

### 3.3 Shizuku Auto 子路径顺序不标准

旧 Auto capability 顺序为：`WifiAdb` → `Binder` → `InHostPlugin` → `Socks`。其中 `WifiAdb` 注释中明确“复用 AIDL 方案”；`Binder` 是 fd/binder 设想且当前不可用或 fallback。这样会让 Auto 模式优先选择非纯 JDWP 数据面，增加失败概率。

### 3.4 宿主侧服务存在控制面 Binder，但 JDWP 数据面应独立

`ide-debugger-host` 中 `HostPluginService`、`IdeShizukuSocksUserService` 属于 host-side bridge，用于把宿主进程内 `localabstract:jdwp` 暴露给 IDE。它们可以作为“启动/控制/保活”的桥，但断点调试数据本身必须保持 JDWP byte bridge：

- IDE ↔ host plugin 的控制交互不应传递调试命令；
- host plugin ↔ `localabstract:jdwp` 应仅做 byte-forward；
- IDE 侧 `JdwpClient` 应独占最终 input/output stream。

## 4. 本次修复策略

本次变更不是重写 JDWP client，而是先把连接层从“不规范 AIDL 默认路径”收敛为“JDWP-only 可选路径”：

1. `AidlSocket` 不再出现在可选连接类型列表中；旧偏好 `aidl_socket` 自动迁移到 `UsbLan`。
2. `DebugConnectionSettings.activeType` 默认改为 `UsbLan`，即默认走 ADB/JDWP forward 类路径。
3. `DebugConnectionRegistry` 收到旧 `AidlSocket` 类型时不再构建 `AidlSocketConnection`，而是使用 `UsbLanConnection` 兼容旧调用。
4. `AppReadyAutoConnect` 默认选择 `UsbLan`，不再默认选择 AIDL Socket。
5. `Shizuku` Auto capability 只保留 `InHostPlugin` 与 `Socks` 两条 JDWP byte bridge 路径。
6. `ShizukuConnection` 显式拒绝旧 `WifiAdb/AIDL` 与 `Binder-fd` 子路径，避免用户或旧配置把会话带回混合传输。

## 5. 后续仍需补齐的标准调试器能力

### 5.1 Java/Kotlin JDWP

- 为 `UsbLanConnection` / `AdbForwardConnection` 增加端到端测试：真实或 fake JDWP server 必须验证 `JDWP-Handshake`、`VirtualMachine.Version`、`EventRequest.Set(BREAKPOINT)`、`Composite Event`。
- 明确 socket 所有权：attach 成功后只有 `JdwpClient` 读取 JDWP stream，其它 connection read loop 必须关闭或不启动。
- 断点标准流程：ClassPrepare → ReferenceType.MethodsWithGeneric / LineTable → EventRequest.Set(BREAKPOINT + LocationOnly) → SuspendPolicy → Resume。
- Kotlin 支持：依赖 JVM bytecode + SMAP/SourceDebugExtension/source mapping，不应另建非 JDWP 断点协议。

### 5.2 Android native / LLDB

- 新增 LLDB transport 和 lldb-server 管理，不应复用 JDWP。
- native 调试应和 Java/Kotlin JDWP 会话并列，由 UI/会话管理层协调 mixed debugging。

### 5.3 AIDL 清理建议

- 保留 Android/Shizuku 自身必要 Binder 控制面可以接受，但不要把 AIDL 命名、配置、默认项暴露为断点调试传输。
- 后续可把 `connection/aidl/AidlJdwpProtocol.kt` 重命名为 `connection/jdwp/JdwpHandshake.kt`，因为其内容是 JDWP handshake/version helper，而不是 AIDL 协议。
- `AidlSocketConnection` 可在下一阶段删除或移到 legacy 包，前提是确认没有设置页、测试、旧自动连接再引用它。

## 6. 结论

断点调试器无法稳定使用的核心不是 `ide-debugger` JDWP client 完全缺失，而是连接层默认与 Auto 路径混入了 AIDL/Binder IPC 思路，导致 JDWP 数据面不唯一、不标准。按官方模型，Java/Kotlin 调试应使用 JDWP；native 调试应使用 LLDB；AIDL 只适合 Android IPC 控制面，不适合作为断点调试数据面。

本次已先把默认连接与 Shizuku Auto 收敛到 JDWP-only 方向，停止对外选择 AIDL Socket，并拒绝旧 Shizuku AIDL/Binder-fd 子路径。后续应继续删除 legacy AIDL 连接实现、重命名 JDWP helper 包，并补齐 LLDB native 调试器。
