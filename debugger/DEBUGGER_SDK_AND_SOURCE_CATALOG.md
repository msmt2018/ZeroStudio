# Android 断点调试器：SDK、依赖与上游源码总目录

审计基准：2026-11-26。本文是 ZeroStudio 的目标技术选型和采购/集成清单，不表示所有列出的 SDK 已加入工程或已在 Android 设备端运行通过。

## 1. 设计原则与术语

ZeroStudio 本身运行在 Android 设备上。不能直接照搬 Android Studio 桌面 IDE 的 host 假设：桌面端运行的 Linux/macOS/Windows 工具二进制，不会因为加入 Gradle 依赖就能在 Android ARM64 进程中运行。每项依赖必须确认运行宿主、ABI、Android API 级别、JDK/Android 类库兼容性、许可证和传递依赖。

把能力分成五类：

- **SDK/API**：供 Kotlin/Java 调用的库，例如 ADBLib、DDMLib、Shizuku API。
- **协议/引擎**：JDWP、JDI、DAP、GDB Remote Serial Protocol、LLDB。
- **目标端组件**：设备上的 adbd、ART、lldb-server、debuggable app、可选 helper。
- **源码工程**：可审计、可裁剪、可移植的上游项目，例如 Android tools/base、LLVM、AOSP。
- **测试基础设施**：Emulator、Android Test、UTP、UiAutomator、Espresso 等。

禁止把“上游项目存在”“Maven artifact 可解析”“模块编译成功”“真机 attach 成功”混为同一个完成状态。

## 2. 目标调试器分层

```text
Compose Debugger UI
  Breakpoints / Threads / Frames / Variables / Watches / Console
                 |
Debugger Application & Session Coordinator
  DebugSessionManager / TargetSelector / StateMachine / Event Router
                 |
Domain Contracts
  Device / Process / DebugTarget / Breakpoint / Suspend / SourceLocation
       |                                  |
Java-Kotlin Debug Engine             Native Debug Engine
  JDWP protocol client                 LLDB client / DAP adapter
  request/event/suspend                symbols / registers / memory
       |                                  |
Android Debug Transport              Native Debug Transport
  JDWP byte stream                     lldb-server / GDB-remote
       |                                  |
ADB infrastructure + Root/Shizuku + Android Control Plane
  ADB server, forward, shell, install, push, logcat, process discovery
       |
Android device: adbd / ART / JDWP endpoint / app process / lldb-server
```

关键所有权：

1. 每个目标进程、每种 debugger engine、每条调试会话都有唯一 owner。
2. 一条 JDWP byte stream 只能由一个 JDWP packet reader 消费；唯一 JDWP client 负责 handshake、命令、reply、event 和 close。
3. ADB 设备管理与 JDWP 协议引擎不是同一层。ADB 负责发现设备、选择 PID、建立转发和提供 byte stream；JDWP client 负责 VM 调试语义。
4. Java/Kotlin JDWP 与 Native LLDB 是两套独立会话。Dual 模式由 Session Coordinator 关联同一进程，而不是合并协议。
5. Activity/Intent/UI 操作属于 Android Debug Control Plane，不属于 JDWP packet。

## 3. 依赖分级总览

| 级别 | 技术/SDK | 目标用途 | 决策 |
|---|---|---|---|
| P0 | Android ADB protocol / ADB server | 设备连接、shell、forward、install、push、logcat | 必需；先复用当前实现并统一 API |
| P0 | ADBLib（Google Android tools/base） | 异步 ADB host client、设备/进程/JDWP 服务 | 优先评估；替代重复造轮子，不直接与现有 ADB 实现并行抢状态 |
| P0 | 自研/现有 JDWP engine | ART Java/Kotlin 断点、线程、栈帧、变量、step、evaluate | 必需；修复协议和 transport，暂不因 DDMLib 而删除 |
| P0 | 统一 JdwpTransport + DebugSession API | Socket/LocalSocket/FD/pipe 传输抽象 | 必需；先实现契约再接各 transport |
| P0 | Android ART / AOSP JDWP 行为资料 | 目标 VM 兼容、JDWP/ART 扩展 | 必需研究；不将 AOSP 整个运行时作为依赖 |
| P1 | DDMLib | 兼容旧 Android Tools device/client APIs、logcat/process 参考 | 可选适配层；不可成为第二个 JDWP owner |
| P1 | Kotlin Coroutines / Flow | 异步 I/O、状态流、取消和会话生命周期 | 必需；与现有工程版本对齐 |
| P1 | ASM | JVM class、方法、字节码与行表解析/分析 | 按源码定位实际需要引入 |
| P1 | Kotlin metadata / SMAP support | Kotlin inline、suspend、multifile 源码映射 | 必需能力；评估 compiler embeddable/metadata 依赖体积 |
| P1 | Android SDK Build Tools / apksigner / aapt2 / D8-R8 | APK/DEX 构建、签名、映射文件获取 | 与现有 Tooling 复用；不是 debugger runtime 的必需库 |
| P1 | Shizuku API + UserService | 用户授权下的高权限设备操作、FD/stream bridge | 可选 transport/control adapter；不替代 ADB/JDWP |
| P1 | Root shell adapter | root 环境进程/文件/启动 lldb-server | 可选；需 SELinux/权限矩阵 |
| P1 | LLVM LLDB + lldb-server | C/C++/Rust native debugging | Native 调试必需；需要 Android host 可执行构建/移植方案 |
| P1 | DAP protocol / lldb-dap | 统一 Native debugger frontend 接口 | 推荐；先验证 Android ARM64 host 运行方案 |
| P2 | UTP DDMLib Device Provider + proto | 自动化设备测试/设备编排 | 仅测试集成；不进入生产 debugger runtime |
| P2 | AndroidX Test / UiAutomator / Espresso | Activity/Intent/UI 控制与端到端测试 | 按需求加入测试模块 |
| P2 | JDWP/JDI reference tooling | 协议行为与语义参考 | 参考优先；JDI 的 JDK 模块不可假定 Android app runtime 可用 |
| P2 | dexdump / dexlib2 / baksmali | DEX 调试信息、反汇编、mapping 诊断 | 可选诊断/反编译模块 |
| P3 | Perfetto / Simpleperf / ART tracing | 性能、线程、运行时诊断 | 独立 profiler 领域，不混入断点核心 |
| P3 | Crashpad / tombstone parser | Native 崩溃诊断 | 可选扩展，不是断点 attach 前置依赖 |

## 4. 用户提出的四项依赖：准确定位

用户提出：

```gradle
implementation("com.android.tools.adblib:adblib-ddmlibcompatibility:9.6.0-alpha04")
implementation("com.android.tools.ddms:ddmlib:32.6.0-alpha06")
implementation("com.android.tools.utp:android-device-provider-ddmlib:32.4.0-alpha06")
implementation("com.android.tools.utp:android-device-provider-ddmlib-proto:32.4.0-alpha06")
```

### 4.1 adblib-ddmlibcompatibility

- 属于 Android Tools ADBLib/DDMLib 兼容桥，不是 ADBLib 全量 API 的代名词。
- 作用是让旧 DDMLib 接口与新的 ADBLib 基础设施协作，方便渐进迁移。
- 不应把它误当成一个 JDWP 调试引擎。
- 引入前必须检查 POM/Gradle Module Metadata 的传递依赖、Android host 可用性、Kotlin/Guava/Netty 等冲突、许可证及其对现有 ADB server 管理方式的影响。
- 不能同时让 ZeroStudio 自研 ADB、ADBLib、DDMLib 各自创建独立设备监控和 JDWP proxy；必须只有一个设备状态/ADB session owner。

### 4.2 com.android.tools.ddms:ddmlib

- DDMLib 是 Android Developer Tools 的设备/客户端监控库，覆盖设备状态、进程、logcat、shell、文件传输以及历史 DDMS/JDWP client 监控能力。
- DDMLib 的 client monitoring 可能建立 JDWP proxy/监控会话。ZeroStudio 已有自研 JDWP engine，因此必须明确 DDMLib 是否仅用于设备与进程发现，不能无意中启动第二个 debugger client。
- 当前 Android Studio 上游源码说明 Android Studio 主要依赖 ADBLib，ddmlib-studio 是为兼容而保留的精简副本；这说明 ADBLib 应作为新架构优先研究对象，DDMLib 作为迁移兼容层。
- 如果只需要 ADB shell/device/process API，优先评估 ADBLib 对应能力；仅在确实依赖旧 DDMLib API 时增加兼容 adapter。

### 4.3 android-device-provider-ddmlib

- UTP（Unified Test Platform）设备提供器，用于自动化测试运行期间向测试平台提供 DDMLib 设备能力。
- 不是生产调试器的设备连接 SDK，也不负责断点、JDWP 引擎或 LLDB。
- 放入单独的 test-integration / instrumentation tooling 模块。

### 4.4 android-device-provider-ddmlib-proto

- 上述 UTP provider 的 protobuf 配置/消息定义。
- 与 provider 版本必须匹配。
- 不要因为依赖 proto 就把 UTP 传递到 app runtime。

### 4.5 版本规则

上述四个版本属于不同 artifact/version train，不能仅凭版本数字或 alpha 标签推断兼容。每次选版必须记录：

1. Maven Central/Google Maven 实际 POM 和 Gradle Module Metadata。
2. ADBLib compatibility 对 ddmlib 的传递依赖版本。
3. UTP provider 与 proto 是否同一发布版本。
4. 依赖树中的 Kotlin、Guava、protobuf、Netty、annotations、common 等版本冲突。
5. Android app runtime 中是否引用 java.desktop、javax.swing、jdk.* 或桌面文件 API。
6. APK 体积、D8/R8、minSdk、ABI 和许可证检查。
7. 真机 USB/Wireless ADB、多个 ADB server、断连恢复、JDWP attach 测试。

不得直接复制上面的版本号到 Version Catalog 并宣称可用。先在独立实验模块解析和运行验证，再决定是否纳入生产模块。

## 5. ADB 设备基础设施：需要的 SDK 与源码

### 5.1 ADB 核心职责

- 设备枚举与状态：device / unauthorized / offline / reconnecting。
- USB 与 Wireless debugging 连接。
- ADB server session、server version、transport ID 和 device serial。
- shell command、exec、install、uninstall、push、pull、sync。
- port forwarding / reverse。
- logcat stream。
- JDWP process 列表与按 PID 建立 JDWP forward。
- 目标进程退出、设备断开、PID 复用时的失效通知。

### 5.2 主要上游

| 上游 | URL | 用途 |
|---|---|---|
| Android platform/tools/base | https://android.googlesource.com/platform/tools/base | Google Android Tools；ADBLib、DDMLib、UTP 等源码 |
| AOSP platform/packages/modules/adb | https://android.googlesource.com/platform/packages/modules/adb | adbd/ADB 协议与 host 工具实现 |
| AOSP platform/system/core | https://android.googlesource.com/platform/system/core | Android system core、旧 ADB 相关代码与平台工具 |
| Android Debug Bridge docs | https://developer.android.com/tools/adb | ADB 命令、forward、shell、文件传输文档 |

### 5.3 ADBLib 的架构要求

Google ADBLib 的设计是 Kotlin/Java 异步 ADB host client，通过 AdbSessionHost 注入线程池、日志、超时和资源策略；支持多个 ADB host session，不依赖全局可变状态。ZeroStudio 需要适配其 coroutine dispatcher、channel、logger、lifecycle 和 Android app host 环境。

建议拆分：

- `DeviceDiscovery`：设备列表和状态。
- `AdbSessionProvider`：ADB server session owner。
- `AdbCommandService`：shell/exec/install/push/pull。
- `JdwpProcessDiscovery`：PID、进程名、JDWP 可用性。
- `JdwpForwardService`：创建、跟踪和清理转发。
- `LogcatService`：独立日志流。
- `AdbCapabilities`：ADB server/device capability。

不能让 UI 直接依赖 ADBLib/DDMLib 类型。

## 6. Java/Kotlin：JDWP 与调试引擎

### 6.1 必需协议和运行时能力

- JDWP handshake；VirtualMachine.Version、IDSizes、Capabilities/CapabilitiesNew。
- ClassPrepare、ClassUnload、LoadedClasses、ReferenceType、Methods、LineTable。
- EventRequest.Set/Clear、Composite Event。
- Breakpoint、SingleStep、Exception、ThreadStart/Death、VMStart/Death。
- Suspend/Resume、ThreadReference.Frames、StackFrame.GetValues/SetValues。
- ObjectReference、ReferenceType、ArrayReference、StringReference。
- ClassType.InvokeMethod、ObjectReference.InvokeMethod（受限执行）。
- Monitor/field access 等能力按需要逐步实现。
- Android Runtime DDMS chunks/ART extensions 按目标设备行为单独适配。

### 6.2 JDWP 关键规范

- JDWP 是 wire protocol，不是完整 IDE debugger。
- 每条连接只执行一次 handshake。
- 唯一 packet reader 负责拆包；不允许 connection read loop 与 JDWP engine 同时读同一输入流。
- IDSizes 必须从目标 VM 查询，不能固定 objectID/threadID/frameID 宽度。
- Reply、VM Event、未知命令和异步事件必须严格区分。
- 断点使用 EventRequest.Set 返回的 requestID 与真实 Location 绑定。
- suspendPolicy 决定 stack/variable 可读时机。
- detach、terminate、disconnect、VM.Dispose 必须区分。
- 重新连接后旧 VM IDs 与 event request IDs 全部失效。

### 6.3 JDI 与 JVM TI 的边界

JPDA 包含 JVM TI、JDWP 和 JDI。JDI 是更高层的 Java debugger API，JDWP 是传输协议。ZeroStudio 是 Android app runtime，不能直接假设桌面 JDK 的 `jdk.jdi`、`jdk.attach`、JVM TI native agent 可以在 Android 上使用。

可选路线：

- 继续完善现有 JDWP engine，适合控制体积和 Android runtime 兼容。
- 将 JDI 作为 JVM 端工具/测试参考或独立 host-side adapter，前提是运行环境确实是完整 JDK。
- 评估 IntelliJ debugger platform API 时必须核对平台运行时依赖，不能仅复制 `java/debugger` 目录。

### 6.4 上游源码

| 项目 | URL | 使用方式 |
|---|---|---|
| Oracle JPDA/JDWP/JDI specs | https://docs.oracle.com/en/java/javase/24/docs/specs/jpda/jpda.html | 协议/语义规范参考 |
| Oracle JDWP spec | https://docs.oracle.com/en/java/javase/24/docs/specs/jdwp/jdwp-spec.html | packet、command、event 规范 |
| AOSP ART | https://android.googlesource.com/platform/art | ART debugger/JDWP 实际实现参考 |
| Android tools/base | https://android.googlesource.com/platform/tools/base | Android Studio/AGP 调试集成参考 |
| IntelliJ Community | https://github.com/JetBrains/intellij-community/tree/master/java/debugger | JDI engine、requests、events、suspend、position/evaluation 设计参考 |

## 7. 源码定位、DEX 与 Kotlin 调试信息

断点不能只用 source filename + line 作为主键。至少要处理 Gradle module/variant/sourceSet、source URI、classloader、class signature、method signature、DEX method index、code index、SMAP、Kotlin metadata 和 R8 mapping。

候选技术：

| 技术 | 作用 | 采用建议 |
|---|---|---|
| ASM | class 文件、method、字节码、LineNumberTable、调试属性解析 | 推荐；检查 Android host 字节码兼容 |
| Kotlin Metadata | Kotlin declaration / facade / multifile 等映射辅助 | 需要时按 compiler 版本对齐 |
| Kotlin compiler embeddable | Kotlin PSI/metadata/编译器语义 | 体积较大；不要仅为行号映射直接引入完整编译器 |
| SMAP / SourceDebugExtension | inline、Kotlin/JVM 源码行映射 | 必须支持并建立 fixture 测试 |
| R8 mapping | 混淆后的类/方法/行号还原 | 通过构建产物读取，不将 R8 全部作为 runtime debugger 依赖 |
| dexlib2 / smali | DEX 结构、debug info、反汇编与诊断 | 可选；评估 LGPL/BSD 等具体版本许可证和体积 |
| Android SDK D8/R8 | DEX 转换、优化与 mapping 产物 | 由构建工具链使用，不应与 debugger runtime 混为一体 |

必须覆盖 Kotlin inline、suspend continuation、lambda、匿名类、FooKt facade、@JvmName、多文件 facade、synthetic bridge、Java inner class、generated sources、多个 classloader 和混淆映射。无法确认的映射应标记 Pending/Unverified，不能伪装为 Verified。

## 8. Native：LLDB、lldb-server、DAP

### 8.1 需要的组件

- LLVM LLDB core：debugger engine、symbols、registers、memory、breakpoint、thread/frame。
- `lldb-server`：部署在 Android 目标设备，附加目标 PID 并提供 remote debugging transport。
- `lldb-dap`：DAP frontend adapter，供 IDE 通过 Debug Adapter Protocol 操作 LLDB。
- LLVM Clang/NDK toolchain：编译目标 ABI 的 native binary 和 debug symbols。
- ELF/DWARF：符号、行表、类型和变量信息。
- Build ID / symbol store：将设备已加载 .so 与正确未剥离符号文件匹配。
- GDB Remote Serial Protocol：LLDB 与 lldb-server 的远程通信层。
- Rust pretty-printers / visualizers：作为后续体验增强，不是 native attach 的前置条件。

### 8.2 ZeroStudio 的关键宿主约束

ZeroStudio 运行在 Android，而 LLVM 官方预编译的桌面 lldb/lldb-dap 通常面向 Linux/macOS/Windows host。不能把 Linux x86_64 `lldb-dap` 或桌面 `liblldb.so` 直接打包到 Android app 并认为可执行。

需要验证两种方案：

1. **On-device Native host build**：构建适配 Android host ABI（优先 arm64-v8a）的 LLDB、liblldb、lldb-dap，处理 Android bionic、Python 支持、依赖库和可执行权限。
2. **Embedded LLDB API**：把 liblldb 以 JNI/NDK 方式嵌入，直接暴露精简 Native Debug API；避免桌面 DAP server 运行时，但需要长期维护 JNI 生命周期与 LLDB API 适配。

设备端 `lldb-server` 是另一份目标端二进制，需要按目标设备 ABI/Android 版本构建或随 NDK/LLVM 工具链获取。不要把 host lldb-server 与 device lldb-server 混淆。

Native attach 还依赖设备的 run-as/ptrace/SELinux/调试权限和正确符号。Android 官方文档列出 run-as 与 ptrace 等限制；必须把权限能力探测做成可见诊断，而不是 attach 失败后静默重试。

### 8.3 DAP 的定位

DAP 是 IDE 与 debugger adapter 之间的控制协议，不是 LLDB remote wire protocol，也不直接连接 Android app。典型链路：

```text
Compose Debug UI -> DAP client -> lldb-dap -> LLDB -> GDB Remote -> device lldb-server -> native process
```

若在 Android host 上无法稳定运行 lldb-dap，可实现原生 LLDB JNI adapter，但仍应把内部 Debug API 设计为与 DAP 语义对齐，以便后续替换。

### 8.4 上游源码

| 项目 | URL | 用途 |
|---|---|---|
| LLVM Project | https://github.com/llvm/llvm-project | LLDB、lldb-dap、Clang、LLVM libraries |
| LLDB documentation | https://lldb.llvm.org/ | LLDB API、remote debugging、platform support |
| Debug Adapter Protocol | https://github.com/microsoft/debug-adapter-protocol | IDE 与 debugger adapter 的协议规范 |
| Android NDK | https://developer.android.com/ndk | Android ABI、工具链、符号和 Native build |
| AOSP platform/tools/base | https://android.googlesource.com/platform/tools/base | Android Studio LLDB/ADB integration 参考 |
| Android Studio debugger docs | https://developer.android.com/studio/debug | Java-only、Native-only、Dual 模式行为参考 |

## 9. DDMLib、ADBLib 与 UTP：模块归属

建议按职责隔离，而不是将所有 artifacts 放进 `:core:app`：

| 建议逻辑模块 | 责任 | 依赖策略 |
|---|---|---|
| `:debugger:device-api` | Device/Process/DebugTarget/Capabilities 契约 | 无 DDMLib 类型 |
| `:debugger:adb-api` | ADB session、shell、forward、logcat 契约 | 无 UI |
| `:debugger:adblib-adapter` | Google ADBLib adapter | 先做 Android host feasibility |
| `:debugger:ddmlib-compat` | 必要时封装旧 DDMLib API | 可选，禁止重复 JDWP owner |
| `:debugger:jdwp-transport` | Socket/LocalSocket/FD/pipe 统一字节流 | 不依赖 UI |
| `:debugger:jdwp-engine` | packet/command/event/VM state | 唯一 JDWP client |
| `:debugger:source-mapping` | class/DEX/SMAP/R8/source mapping | 可独立测试 |
| `:debugger:native-api` | Native debugger contract | 不泄漏 LLDB 类型 |
| `:debugger:lldb-adapter` | LLDB JNI 或 DAP client | 可选运行实现 |
| `:debugger:control-api` | Activity/Intent/UI automation 命令契约 | 权限和 allowlist |
| `:debugger:utp-test` | UTP device provider/proto 集成 | testImplementation/test module only |

这是目标模块建议，不表示这些 Gradle project 当前已创建。创建前先审查现有 `:debugger:adb-connection:*` 和 `:debugger:Breakpoint-debugger:*`，避免重复模块。

## 10. Android Debug Control Plane

JDWP 负责 VM 执行控制，不负责启动 Activity、发送 Intent、点击 UI 或截屏。控制面可组合：

- ADB shell：`am start`、`am broadcast`、`am force-stop`、`cmd package`、`run-as`。
- Shizuku：经用户授权执行受限系统命令、启动 user service、传递 FD。
- Root adapter：仅在设备 root 且策略允许时执行。
- AndroidX Test / UiAutomator / Espresso：测试场景的 UI 控制与断言。
- App-specific Debug Control Agent：受限 Binder/AIDL/独立 socket API。

API 使用 typed commands，例如 LaunchActivity、SendBroadcast、ForceStop、CaptureScreenshot、CaptureUiHierarchy。不要把任意 shell 字符串拼接进 JDWP packet；所有命令必须验证 package、权限、参数类型、allowlist、超时与取消。

## 11. Shizuku 与 Root

| 技术 | 上游 | 作用 | 边界 |
|---|---|---|---|
| Shizuku API | https://github.com/RikkaApps/Shizuku-API | Android app 使用 Shizuku binder/API | 授权与系统服务代理，不是 JDWP engine |
| Shizuku Server | https://github.com/RikkaApps/Shizuku | user service/server 机制参考 | 需遵守项目许可证、Android 版本和权限要求 |
| Root shell | Android su/设备实现 | 受 root 权限执行进程/文件/调试命令 | 不能假设每台设备都有 root |
| Android LocalSocket / ParcelFileDescriptor | Android SDK | 本地 socket/FD 传输 | 统一包装为 JdwpTransport，不要强转 java.net.Socket |

Root/Shizuku 适配器必须提供明确的 stream/FD 所有权、关闭时机、授权撤销处理、进程退出处理和错误码。它们不能绕过 JDWP session owner 规则。

## 12. 自动化测试和设备基础设施

### 生产调试运行时不需要的测试依赖

- `com.android.tools.utp:android-device-provider-ddmlib`
- `com.android.tools.utp:android-device-provider-ddmlib-proto`

二者属于 UTP 测试设备提供链。仅在专门 test tooling 模块验证；不要作为 app production implementation。

### 建议测试栈

| 技术 | 目的 |
|---|---|
| JUnit 4/5（按工程当前测试体系） | 协议、状态机、mapper 单元测试 |
| kotlinx-coroutines-test | coroutine session、取消、Flow 测试 |
| MockK / Mockito（按当前工程现状择一） | adapter/engine 测试替身 |
| AndroidX Test Core/Runner | Android instrumentation |
| Espresso | app 内 UI 行为测试 |
| UiAutomator | 跨 app/system UI 操作 |
| Android Emulator / AVD | API 版本、ABI、可重复设备测试 |
| UTP | 多设备/设备提供器/测试编排（后期） |
| Test fixtures / JDWP packet fixtures | partial I/O、异常包、乱序事件回归 |
| LLVM LLDB test suite | LLDB/DAP 兼容与协议验证 |

必须具备模拟设备、至少一台真实 ARM64 设备、Wireless ADB、Shizuku 授权撤销和可选 Root 测试矩阵。UTP 不替代本地真机验收。

## 13. 可选的高级调试与诊断 SDK

这些不是 P0 断点调试器前置依赖，按独立功能域规划：

- Perfetto / Android tracing：性能时间线、调度和系统轨迹。
- Simpleperf：CPU profiling。
- Android Studio profiler / JVMTI：仅作平台行为参考，Android runtime 权限和兼容性必须单独验证。
- ART runtime stats / GC / heap dump：运行时诊断。
- Tombstone / debuggerd / Crashpad：native crash 分析。
- APK Analyzer / apkanalyzer、apkanalyzer CLI：APK/DEX/资源诊断。
- R8 retrace：混淆堆栈还原。
- JFR/JMC：桌面 JVM 诊断参考，不可直接假设 Android ART 支持完整 JFR。

这些功能应建立 Profiler/Diagnostics 模块，不应扩大 JDWP engine 的职责。

## 14. 许可证、依赖和发布检查

每个依赖登记：

- Maven coordinates 或 git URL + immutable commit/tag。
- SPDX/license 文件、NOTICE、上游版权。
- 是否有 native binary、ABI、C/C++ runtime、Python runtime。
- 传递依赖与重复类。
- minSdk/compileSdk/JDK/Kotlin compatibility。
- 是否包含桌面专用 API 或动态加载行为。
- R8 keep rules、consumer rules、ProGuard mapping 产物。
- 安全公告和更新频率。
- 是否可以 source-build、是否需要裁剪模块。

禁止只看 Maven artifact 页面就引入。每次升级需跑依赖树、APK Analyzer、license report、D8/R8、ABI 检查、instrumented smoke test。

## 15. 推荐落地顺序（严格自底向上）

### 阶段 0：盘点现有代码，禁止重复造轮子
1. 审核 `:debugger:adb-connection:adblib`、`:libadb`、`:connection` 的真实职责；注意仓库里名为 adblib 的自研模块不等同于 Google `com.android.tools.adblib`。
2. 审核 `:debugger:Breakpoint-debugger:ide-debugger` 的 JDWP packet/command/event 完整度。
3. 审核 `:core:app` 中 AdbRunner、AdbForwardConnection、RootAdbBridge 和 ShizukuConnection。
4. 输出 artifact dependency graph、源码来源和许可证表。

### 阶段 1：统一底层契约
5. 定义 DeviceId、ProcessIdentity、DebugTarget、DebugCapabilities、JdwpTransport、NativeTransport。
6. 建立唯一 AdbSessionProvider 和唯一 JDWP session owner。
7. 移除 Socket-only 假设，支持 Input/Output stream 与 FD/LocalSocket。
8. attach 连接阶段不执行 handshake；由 JDWP engine 独占 handshake。

### 阶段 2：ADB + JDWP golden path
9. 设备枚举 → package/process 列表 → PID 选择 → ADB JDWP forward → handshake → VMReady。
10. 完成 class prepare → breakpoint set → composite event → suspend → frames/variables → step/resume → detach。
11. USB/Wireless ADB、设备断开、PID 变化、进程重启均测试。

### 阶段 3：源码映射与 IDE 语义
12. 完成 IDSizes、classloader、LineTable、SMAP、Kotlin inline/suspend、R8 mapping。
13. 统一 persistent breakpoint 与 runtime requestID 映射。
14. 完成条件断点、logpoint、hit count、exception breakpoint 和多线程行为。

### 阶段 4：兼容层与权限 transport
15. 评估 ADBLib 并做独立 spike；确认 Android host 支持后接入。
16. 只有需要旧 API 时才接 DDMLib compatibility。
17. 接 Root、Shizuku LocalSocket/FD，不允许分叉出第二个 debugger engine。

### 阶段 5：Native debugging
18. 先完成设备端 lldb-server 启动、ABI、权限和符号匹配。
19. 再构建 Android host LLDB/DAP 或 JNI adapter。
20. 建立 Native-only 会话。
21. 最后由 coordinator 做 Java + Native Dual session。

### 阶段 6：测试与控制面
22. UTP 仅作为测试基础设施评估。
23. 接入 AndroidX Test/UiAutomator/截图与层级抓取。
24. Activity/Intent/ForceStop 由独立 DebugControl API 管理。

## 16. 上游源码与规范索引

- Android tools/base：https://android.googlesource.com/platform/tools/base
- AOSP ADB：https://android.googlesource.com/platform/packages/modules/adb
- AOSP ART：https://android.googlesource.com/platform/art
- AOSP system/core：https://android.googlesource.com/platform/system/core
- IntelliJ Community debugger：https://github.com/JetBrains/intellij-community/tree/master/java/debugger
- LLVM/LLDB：https://github.com/llvm/llvm-project
- LLDB docs：https://lldb.llvm.org/
- DAP specification：https://github.com/microsoft/debug-adapter-protocol
- Shizuku API：https://github.com/RikkaApps/Shizuku-API
- Shizuku server：https://github.com/RikkaApps/Shizuku
- AndroidX Test：https://github.com/android/android-test
- UiAutomator：https://developer.android.com/training/testing/other-components/ui-automator
- Espresso：https://developer.android.com/training/testing/espresso
- UTP source tree：https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/utp/
- ADB docs：https://developer.android.com/tools/adb
- Android debugger docs：https://developer.android.com/studio/debug
- Android platform debugger docs：https://developer.android.com/studio/platform/debug
- JPDA overview：https://docs.oracle.com/en/java/javase/24/docs/specs/jpda/jpda.html
- JDWP spec：https://docs.oracle.com/en/java/javase/24/docs/specs/jdwp/jdwp-spec.html

## 17. 参考依据与限制

- Android Studio 官方调试文档明确区分 Java/Kotlin、Native-only 和 Dual（Java + Native）模式；Dual 会分别 attach Java debugger 与 LLDB。
- Android Studio for Platform 文档展示了在设备上通过 PID 启动 lldb-server、端口转发并使用 DAP/LLDB adapter 的流程；该流程面向特定 platform/host 环境，不能直接视为 ZeroStudio Android-host 的现成方案。
- Google ADBLib README 描述了异步 API、AdbSessionHost 注入、可定制线程池/日志/超时和多 ADB host session。
- Google DDMLib 源码文档说明其设备/客户端监控线程模型；当前 Android Studio 源码说明 Android Studio 主要依赖 ADBLib，ddmlib-studio 是兼容性副本。
- Oracle JPDA 文档定义 JDI/JDWP/JVM TI 的分层；桌面 JDK 的 JDI/JVM TI 可作为语义参考，但不能假设在 Android app runtime 可直接使用。

本文是架构选型和实施清单。任何 SDK 版本、artifact 可用性、Android host ABI 兼容性和许可证都必须在真实依赖解析与构建中再次验证。
