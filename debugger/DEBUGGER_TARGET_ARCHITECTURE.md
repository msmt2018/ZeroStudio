# ZeroStudio 断点调试器目标架构与源码审计入口

## 1. 现状结论

当前仓库已经存在自研 JDWP engine、Android ADB/Root/Shizuku 多路径连接代码、宿主 attach helper 和日志/调试 UI 相关模块，但静态代码存在以下架构缺口：

1. `ConnectionBackedDebugger` 只接受 `java.net.Socket`，Root stream 与 Shizuku LocalSocket 不能完整进入 JDWP engine。
2. 部分 attach 阶段执行 handshake/VM.Version probe，随后 JDWP client 再握手，存在重复 handshake 风险。
3. `HostAttachAgent` 使用固定 abstract socket 名称，未可靠按目标 PID 建立 endpoint。
4. HELLO 控制消息与后续 JDWP 字节复用同一 socket，reader handoff 有预读/抢读风险。
5. 宿主注入代码生成第二个 JDWP client，可能与 IDE engine 竞争同一 ART debugger endpoint。
6. 多进程发现与 PID 选择不足，不能只根据 package process name warning 判断目标。
7. JDWP 断点执行与 Activity/Intent/UI 控制职责混杂。
8. Native/LLDB/lldb-server/DAP 链路未形成完整可验证闭环。

完整的逐文件证据和修复路线见 [现有源码架构与可用性审计](DEBUGGER_ARCHITECTURE_AUDIT.md)。本文给出目标架构，SDK 与上游源码清单见 [SDK、依赖与上游源码总目录](DEBUGGER_SDK_AND_SOURCE_CATALOG.md)。

## 2. 核心领域契约

底层优先建立与第三方实现无关的契约：

- `DeviceId`：serial、transportId、连接类型、设备状态。
- `ProcessIdentity`：package、processName、pid、uid、process start identity。
- `DebugTarget`：device + process + debuggability + JDWP/Native capabilities。
- `JdwpTransport`：输入/输出字节流、close、metadata、timeout；不限定 Socket。
- `NativeTransport`：lldb-server endpoint、GDB remote channel、目标 ABI。
- `DebugSession`：session ID、target、engine、state、ownership、cancellation。
- `BreakpointIdentity`：稳定 UUID、source URI、module/variant、line/column。
- `BreakpointBinding`：VM requestID、class/method/location、verified state。
- `SuspendSnapshot`：suspend policy、thread、frames、variables 和有效期。

## 3. Session 状态机

```text
Idle
  -> DiscoveringDevices
  -> SelectingTarget
  -> ConnectingTransport
  -> Handshaking
  -> NegotiatingCapabilities
  -> ConfiguringBreakpoints
  -> Running <-> Suspended
  -> Detaching / Terminating
  -> Closed / Failed
```

每个状态定义允许操作、超时、取消和恢复行为。设备断开、目标进程退出、PID 被复用、JDWP handshake 失败、VM_DEATH、LLDB server exit 都必须形成明确 terminal/retry state。

## 4. Java/Kotlin JDWP session

唯一 JDWP engine 负责：

- handshake 和 VM capability negotiation。
- packet reader/writer 与 command ID。
- command reply 和 VM composite event 路由。
- IDSizes、class、method、line table、classloader 查询。
- EventRequest 生命周期与断点 requestID 映射。
- suspend/resume、frames、variables、objects、evaluation。
- detach、disconnect、VM death 与 session cleanup。

Transport adapter 只负责提供可靠 byte stream，不读写 JDWP packet，不执行 handshake，不管理 breakpoint。ADB forward 是首个 golden path；Root、Shizuku 后接入。

## 5. Native debugger session

Native engine 单独维护 LLDB session：

- 设备端按 ABI 启动/管理 lldb-server。
- 确定目标 PID、ptrace/run-as/SELinux 权限。
- 将 host debugger 连接到 lldb-server 的 remote protocol。
- 装载匹配 Build ID 的未剥离 ELF/DWARF symbols。
- 通过 LLDB API 或 DAP adapter 管理断点、线程、寄存器、内存和栈帧。
- 与 Java session 通过 target identity 关联。

不能把桌面 x86_64 LLDB binary 当作 Android ARM64 host binary。Android host LLDB 构建/移植是 Native debugging 的独立工程。

## 6. Dual Java + Native session

同一 Android process 可以有 Java JDWP session 和 Native LLDB session。Coordinator 统一 target identity、生命周期、停止原因和 UI 展示，但两个 engine 的协议读写、断点模型、线程 ID 和 frame ID 必须保持隔离。

Java thread ID 与 Native thread ID 不可直接假定相同；可通过 OS TID / process identity 做映射，并标记映射可信度。一个 engine 断开不应未经策略判断就强制关闭另一个 engine。

## 7. Android Control Plane

以下命令不属于 JDWP：

- LaunchActivity / SendBroadcast / ForceStop。
- CaptureScreenshot / CaptureUiHierarchy。
- InstallApk / ClearAppData（危险操作需确认）。
- InvokeHostDebugAction（app 自定义白名单动作）。

通过 ADB、Shizuku、Root 或 AndroidX Test adapter 执行。参数 typed、权限显式、命令可审计、支持取消和超时。

## 8. 推荐模块依赖方向

```text
debugger-ui
    -> debugger-application
        -> debugger-domain-api
            <- debugger-adb-adapter (ADBLib / existing ADB)
            <- debugger-ddmlib-compat (optional)
            <- debugger-jdwp-transport
            <- debugger-jdwp-engine
            <- debugger-source-mapping
            <- debugger-native-adapter (LLDB/DAP)
            <- debugger-control-adapters (ADB/Shizuku/Root/Test)
```

以上为逻辑模块边界，实施前必须与当前 Gradle include 对照。优先扩展现有模块，避免为了文档图而创建重复 Gradle project。

## 9. 交付门禁

Java/Kotlin golden path 至少完成：

- 真机/模拟器 device discovery 与多设备选择。
- package 多进程列表、PID identity 和进程重启处理。
- ADB JDWP forward 与唯一 handshake。
- VM.Version/IDSizes/capabilities。
- set/clear breakpoint、Composite Event、suspend、frames/variables。
- step/resume、detach 后 app 继续运行。
- 断连、超时、VM death、异常 packet 和重复 attach 回归。

Native golden path 至少完成：

- 目标 ABI lldb-server 启动。
- host LLDB 实际可执行且 ABI 正确。
- run-as/ptrace/SELinux capability 诊断。
- ELF Build ID 与 DWARF symbols 匹配。
- native breakpoint hit、stack/register/memory、step、detach。

Dual session 必须验证同一进程 Java 与 Native 两侧断点可独立命中、切换和关闭。

## 10. 相关源码

- [现有源码架构与可用性审计](DEBUGGER_ARCHITECTURE_AUDIT.md)
- [SDK、依赖与上游源码总目录](DEBUGGER_SDK_AND_SOURCE_CATALOG.md)
