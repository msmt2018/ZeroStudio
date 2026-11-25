# ZeroStudio 断点调试器源码架构与可用性审计

审计日期：2026-11-26
仓库：msmt2018/ZeroStudio
审计 HEAD：291fd191efa5148cc978e6746f898cac026947b4
对照：msmt2018/intellij-community（JetBrains IntelliJ Community fork）、Android 官方 JDWP/Android Studio 文档

> 本报告是源码静态审计，不代表已完成真实设备端到端验证。源码存在、接口可接通和设备验证通过是三个不同结论。

## 1. 总结结论

**开发方向部分正确，但当前整体尚未达到稳定可用，也不能称为与 Android Studio / IntelliJ IDEA 内置调试器等价。** 当前更接近自研 JDWP 原型、Android 多路径连接层、宿主反向字节桥和 UI 展示层。关键缺陷集中在 transport 到 JDWP engine 的集成、目标进程定位和会话所有权，而不是 UI。

最重要的发现：

1. HostAttachAgent 把目标 JDWP abstract socket 名称写死为 jdwp，没有 PID 参数；Android 标准 ADB JDWP attach 按 PID 选择目标，例如 adb forward tcp:12345 jdwp:<PID>。不能把固定 jdwp 名称当作已验证的 ART endpoint。
2. IDebugConnection 同时定义 Socket 和 Flow 字节流接口，但 ConnectionBackedDebugger 只调用 attachedSocket()。Root 和 Shizuku InHostPlugin 的非 java.net.Socket 连接即使 attach 成功，也无法交给 JdwpClient。
3. 部分 attach() 已通过 AidlJdwpProtocol 执行 JDWP handshake + VM.Version，随后 JdwpClient.connect(socket) 又执行 handshake，存在同一条流重复握手风险。
4. HostAttachAgentBootstrap 将 HELLO 文本和后续 JDWP 字节复用同一 LocalSocket，需要严格 handoff；更好的方案是控制面与 JDWP 数据面分离。
5. BreakpointInjectionCodeGen 计划在宿主内再运行 JDWP client 并自定义回传断点事件，可能与 IDE 端 JDWP client 竞争同一 ART debugger endpoint。
6. 多进程宿主检测目前主要记录 warning，没有可靠列出并选择目标 PID。
7. JDWP 断点事件不是 Android Activity/Intent/UI 控制协议，必须另建 Android Control Plane。
8. Native/LLDB 链路不完整；符号地址模型不能代替 lldb-server、LLDB/DAP、native breakpoint 和 mixed-session 协调。

## 2. 静态审计评分

| 领域 | 评分 | 结论 |
|---|---:|---|
| JDWP packet 基础实现 | 6/10 | 有 client、codec、reader、命令 ID 和 reply future；仍需完整协议验证 |
| Java/Kotlin 基础断点 | 5/10 | 有断点、事件、线程、栈帧、变量和 step 基础实现 |
| 源码位置解析 | 4/10 | JavaParser/ASM/Kotlin fallback 已有，SMAP/inline/multifile 等不足 |
| ADB attach | 6/10 | 方向正确，应作为首选 golden path；需真机验证 |
| Root attach | 2/10 | Root stream 与 Socket-only adapter 不兼容 |
| Shizuku attach | 3/10 | SOCKS Socket 路径可继续验证；InHostPlugin LocalSocket 无法进入当前 adapter |
| 宿主自动发现 | 4/10 | Provider/HELLO/LocalServerSocket/bridge 已有，PID/endpoint 未闭环 |
| 断点事件到 UI | 4/10 | 有 SuspendInfo/EventBus/UI adapters，需验证生命周期 |
| Activity/Intent/UI 控制 | 1/10 | 不是 JDWP 能力，尚需独立控制协议 |
| Native/LLDB | 1/10 | 尚无完整 LLDB transport/server/DAP 链 |
| IDEA/AS 级完整度 | 3/10 | 概念有相似处，协议覆盖、平台集成和稳定性差距明显 |

## 3. 协议名称与正确职责

你提到的“jwop”应是 **JDWP（Java Debug Wire Protocol）**。

Java/Kotlin 标准调试链路：IDE 连接目标 JVM/ART JDWP transport；双方交换 JDWP-Handshake；通过 JDWP packet 交换命令、reply 和 VM event；IDE 用 EventRequest.Set 安装断点/step/exception 请求；VM 按 SuspendPolicy 暂停线程或 VM；IDE 查询线程、栈帧、局部变量和对象；最后发送 Resume/Step。

JDWP 不是 JSON 断点消息协议，也不是 ActivityManager、Intent 或 UI 自动化协议。

协议参考：
- Oracle JDWP Specification: https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/jdwp-spec.html
- JDWP Transport / Handshake: https://docs.oracle.com/en/java/javase/11/docs/specs/jdwp/jdwp-transport.html
- Android Studio Debugger: https://developer.android.com/studio/debug

## 4. 当前源码架构

仓库至少存在两组调试相关实现，边界尚未完全收敛。

### 4.1 新拆分 debugger 子项目

- debugger/Breakpoint-debugger/ide-debugger
- debugger/Breakpoint-debugger/ide-debugger-host
- debugger/Breakpoint-debugger/ide-log-plugin
- debugger/Breakpoint-debugger/logwire

ide-debugger 包含 JDWP client、packet codec/reader、Debugger facade、Breakpoint、SuspendInfo、SourceLocator、EvalEngine、DebugEventBus；ide-debugger-host 提供宿主 agent/provider/bridge；ide-log-plugin 和 logwire 负责宿主插件/日志相关能力。

### 4.2 core/app 既有调试器和连接层

- core/app/src/main/java/com/itsaky/androidide/debugger
- core/app/src/main/java/com/itsaky/androidide/debugger/connection
- core/app/src/main/java/com/itsaky/androidide/debugger/connection/host
- core/app/src/main/java/com/itsaky/androidide/debugger/connection/impl

包括 DebuggerController、DebugSessionLauncher、JdwpPortResolver、ShizukuBridge、RunAsBridge、AutoAttachManager、ConnectionBackedDebugger、HostBridgeServer 和各连接实现。

风险：engine 与 core/app 历史 debugger/connection 并存。如果两边都管理 session、断点状态、连接状态或自动 attach，就会出现双重状态机、重复连接和状态不同步。必须指定唯一权威实现：engine 负责 VM 调试语义；app orchestration 负责目标选择、transport 和会话编排；UI 只订阅统一状态。

建议目标架构：

```text
IDE UI / Editor Gutter
        |
        v
Debugger Orchestration
  - DebugSessionManager / TargetProcessDiscovery / AttachCoordinator
        |
        +----------------------------+
        v                            v
JVM/ART Debug Engine            Native Debug Engine
  - JdwpClient                    - LLDB/DAP client
  - BreakpointManager             - lldb-server lifecycle
  - SourcePositionResolver       - ELF Build-ID resolver
  - Thread/Frame/Variable         - symbol/source mapping
        |                            |
        +-------------+--------------+
                      v
              Transport Abstraction
  - ADB JDWP forward / Root stream / Shizuku socket or FD
                      |
                      v
                 Android ART / lldb-server

Separate Control Plane:
  - Shizuku Binder authorization and service lifecycle
  - Root/ADB shell process discovery
  - am start / broadcast / force-stop
  - Host app-specific debug actions
```

原则：一个 JDWP session 只有一个 JDWP reader 和 writer owner。Transport 只提供双向有序字节流；不解析断点，不拥有第二套事件循环。

## 5. JDWP 实现标准性评估

### 5.1 已有正确基础

当前源码存在 JdwpClient、JdwpPacket、JdwpPacketCodec、JdwpPacketReader、CommandSet、CommandCodes、JdwpEvents、JdwpError、Debugger facade、SourceLocator 和 DebugEventBus。基础 packet header 布局与 JDWP 基本结构相符：length 4 字节、id 4 字节、flags 1 字节；command packet 后接 commandSet/command；reply packet 后接 errorCode。

但这些类存在不等于所有命令 payload、IDSizes、事件复合包、错误码、ART 扩展都正确。

### 5.2 必须核查的协议问题

**Handshake 只能执行一次。** 部分连接 attach 阶段执行 AidlJdwpProtocol.performHandshakeAndVersionProbe，随后 ConnectionBackedDebugger.run() 把 socket 交给 JdwpClient.connect(socket)，而后者又调用 handshake。若同一流已握手，这会破坏后续 packet 流。Transport 应只建链；唯一 JdwpClient 执行 handshake、VM.Version 和后续所有命令。

**禁止多个 reader 抢同一 InputStream。** 源码注释已记录 connection read loop 与 JdwpClient reader 同时读取导致字节拆分/丢失，后来关闭了 connection read loop。这是正确方向，但应由架构强制保证。

**必须严格区分 command/reply/event。** Reply header 的后两字节是 errorCode；VM event 是 VM 发来的 command packet，需要按 Composite Event 解析 suspendPolicy、event count 和每种 event payload。要覆盖未知 event、VM_DEATH、disconnect、partial read、非法长度和大 packet。

**必须查询 VirtualMachine.IDSizes。** objectID、referenceTypeID、methodID、fieldID、frameID 等宽度应由 VM 协商，不应假设固定 8 字节。

**断线重连不能复用旧 VM IDs。** 旧 threadID/objectID/classID/methodID/frameID/eventRequestID 在新 VM session 中可能失效。重连必须重新查询 capabilities、类、位置和 event request；旧 frame/variable 必须失效。

**SuspendPolicy 决定读取时机。** SUSPEND_NONE / SUSPEND_EVENT_THREAD / SUSPEND_ALL 决定能否安全读取栈帧和变量。必须确保 UI 取值时 VM 仍处于相应 suspend 状态。

**命中事件关联必须使用 requestID + 真实 Location。** Composite Event 中 requestID、threadID、location（typeTag/classID/methodID/index）是运行时权威信息，不能只按 sourceFile + line 猜断点。

**VM.Dispose 不应等同普通 detach。** detach 通常应清理 IDE 创建的 EventRequest 并关闭 transport；VM.Dispose 可能结束目标 VM 调试会话或影响宿主应用。必须区分 detach、terminate 和 disconnect。

### 5.3 标准断点流程

1. 保存 IDE 断点：stable UUID、project/module/source URI、行列、enabled、condition、log expression、hit condition、suspend policy。
2. 建立 JDWP transport，唯一 JdwpClient 完成 handshake。
3. 查询 VirtualMachine.Version、VirtualMachine.IDSizes、VirtualMachine.CapabilitiesNew。
4. 订阅 CLASS_PREPARE，并检查已加载类。
5. 通过 ReferenceType/Method/LineTable 定位可执行 Location。
6. EventRequest.Set(BREAKPOINT)，保存 VM 返回 requestID 和实际绑定位置。
7. 收到 Composite Event 后按 requestID 找回 IDE breakpoint，保留 threadID、Location、suspendPolicy。
8. VM suspend 时读取 frames/variables/objects。
9. Step 时创建/清理 step request，确保暂停/恢复状态正确。
10. Disable/remove 清理 EventRequest；detach 清理本 session 的请求。

JDWP 标准 EventRequest modifier 没有通用 Java 表达式条件 modifier。条件断点通常由 IDE 在命中并暂停后执行 evaluator；false 时恢复 VM。不能把自定义 CONDITIONAL modifier 当成标准 JDWP modifier。

## 6. ADB、Root、Shizuku 连接路径

### 6.1 ADB：应作为默认 golden path

Android 标准方式是 ADB 发现设备并转发目标进程 JDWP：adb forward tcp:<host-port> jdwp:<pid>。它使用现有 ART JDWP endpoint，按 PID 选择进程，IDE 侧可得到普通 TCP Socket，不需要宿主 app 内再启动第二个 JDWP server。USB 和 Wireless ADB 应是同一 ADB transport 的不同设备链路。

### 6.2 Root：当前集成不完整

RootConnection 使用 RootJdwpStream/InputStream/OutputStream，并在 attachedSocket() 明确抛 UnsupportedOperationException。ConnectionBackedDebugger 只接受 Socket，因此 Root 即使 resolve/connect/attach 成功，最终仍不能交给 JdwpClient。

建议建立统一 JdwpTransport：input、output、close、device/process metadata。Socket、Android LocalSocket、Root process pipe、Shizuku FD 都实现同一接口。不要把 java.net.Socket 作为唯一 transport 类型。

Root 也不保证所有设备都可调试：还受 SELinux、ART JDWP socket 权限、Android 版本、厂商内核和 debuggable 状态影响。

### 6.3 Shizuku：权限代理/控制面，不是调试协议

Shizuku 应负责授权执行受限命令、启动 helper/user service、转交 FD 或建立 socket bridge、发现 PID 和权限。断点数据仍应是 JDWP 原始字节流。

ShizukuConnection 有 InHostPlugin 和 Socks 两类子路径。InHostPlugin 保存 Android LocalSocket，Socks 保存 java.net.Socket；但 attachedSocket() 只返回普通 socket 字段。因此 InHostPlugin LocalSocket 不能通过 ConnectionBackedDebugger 接入 JDWP engine。Socks 路径可继续验证，但仍需解决重复 handshake 和 socket ownership。

### 6.4 宿主反连不是唯一 attach 入口

HostAttachAgentBootstrap + HostBridgeServer 可用于宿主启动后通知 IDE，但不应取代 ADB 主动发现进程和 attach。主路径应由 IDE 选择设备/package/process/PID，再通过 ADB forward 连接；HELLO 只作 ready signal。

## 7. HostAttachAgent endpoint 和 HELLO 协议

HostAttachAgent.kt 的 openLocalAbstractJdwpSocket() 实际调用 connectToIdeLocalServer("jdwp")，即抽象 socket 名称写死为 jdwp，没有 PID 参数。而 Android 官方 AOSP/ADB 的 JDWP forwarding 形式按 PID 选择目标，例如 adb forward tcp:12345 jdwp:<PID>。因此不能把固定 jdwp 名称当成已验证的 ART JDWP endpoint。必须核实目标 Android 版本的 adbd/ART socket 命名，并把 PID 作为必要参数；最稳妥方案是复用 ADB JDWP forward。

当前 HELLO 文本是 ZeroStudio 自定义控制协议，不属于 JDWP 标准。HELLO 后继续在同一 socket 传输 JDWP，必须保证 IDE 完整消费 HELLO 行后移交 socket 所有权，HELLO reader 不预读 JDWP bytes，没有其他 read loop，并携带设备 serial、package、processName、PID、UID、session nonce 和协议版本。更推荐 HELLO 控制面与 JDWP 数据面分离。

HostBridgeServer 已解析 processName，并在 processName != packageName 时记录 warning；但 warning 不能修复 PID 选择。:remote/:debug 进程本身也可能可调试，不能一律拒绝，也不能假定 Provider PID 就是目标进程。必须列出 package 的所有进程，检查 JDWP 可用性，允许用户选择，并处理 PID 复用和进程重启。

## 8. BreakpointInjectionCodeGen 方向

BreakpointInjectionCodeGen 描述生成宿主 Kotlin 代码，在宿主内自行连接 JDWP、安装断点、监听事件，再通过 ad-hoc 协议回传 IDE。这会形成 IDE 端 JdwpClient 和宿主生成代码中的第二个 JDWP client。两者可能争用同一个 ART debugger endpoint、EventRequest 和 suspend/resume 控制权。

建议停用这条重复控制路径。宿主 Agent 只负责把目标 JDWP socket 原样桥接给 IDE；不解析断点、不发 EventRequest、不生成第二套 SuspendInfo。宿主特定业务事件如确有必要，应另建 Host Debug Control API，不能伪装成 JDWP。

## 9. 断点信息格式与 IntelliJ/Android Studio 对齐

当前 API 包含 Breakpoint、SourceLocation、MappedSourceLocation、SuspendInfo、ThreadInfo、StackFrameInfo、VariableInfo、EvalResult、MemoryReadResult。这些是合理的内部 DTO 起点，但不等于 IntelliJ 平台完整断点模型。IntelliJ debugger 是 BreakpointManager、平台 breakpoint API、JDI、process handler、position manager、suspend manager、evaluation context 和 debug session 的协同体系，不是一个 JSON schema。

建议拆分三层：

**IDE 持久化意图层：** stable breakpoint UUID、project/module/source-root identity、source URI、line/column/offset、enabled/temporary、condition/log/hit condition、suspend policy、source mapping、group metadata。

**VM 运行时绑定层：** session ID、device serial、process PID + process start identity、JDWP eventRequestID、classID/methodID/codeIndex、resolved source/line、Pending/Verified/Invalid/Disabled/Obsolete、VM version/IDSizes/classloader identity。

**VM 命中事件层：** requestID、event kind、suspend policy、threadID、location、exception object/catch location、timestamp/sequence number、VM/thread suspend state。

不能只用 sourceFile + line 作为断点主键。同名文件、多模块、多个 classloader、Kotlin multifile、匿名类、lambda 和混淆映射都可能冲突。

### Kotlin/Java 源码映射差距

当前 SourceLocator 有 JavaParser、ASM 和 Kotlin class fallback，方向合理，但还需 Kotlin SourceDebugExtension/SMAP、inline call-site 行号、FooKt facade、@JvmName、多文件 facade、suspend continuation、lambda/匿名类/synthetic bridge、Gradle variant/source set/generated sources、R8/ProGuard mapping、多 classloader 和 Dex/ART 运行位置映射。basename 猜 class signature 只能作为 fallback，必须保持 Pending/Unverified，不能显示 Verified。

### 对照仓库边界

用户给出的 msmt2018/intellij-community 是 JetBrains IntelliJ Community Edition / IntelliJ Platform 源码 fork。它适合作为 JVM debugger/JDI/平台调试器参考，但不等于完整 Android Studio Android debugger 源码。JetBrains java/debugger 的分层包括 engine、requests、events、suspend manager、JDI proxies、attach providers、remote connections、position manager 和 evaluation。Android Studio 还需要 ADB/DDMLib、Android process discovery、ART JDWP、NDK/LLDB、Gradle variant、APK/Dex/source mapping。

合理目标是复用/对齐 IntelliJ 平台 JVM debugger 语义，并实现 Android 专属 attach/transport；不能把自研 JDWP socket client 当作完整 Android Studio debugger。Android Studio 官方文档明确区分 Java/Kotlin debugger 与 C/C++ LLDB；Dual 模式分别 attach 两个 debugger 并协调 session：https://developer.android.com/studio/debug

## 10. 断点命中后控制 Activity/UI/Intent

必须拆分两种职责。

**JDWP Debug Execution Plane：** 暂停/恢复 VM 或线程、单步、读取栈帧/变量/对象/字段、在暂停上下文执行受限表达式/方法调用、读取异常和线程信息。

**Android Debug Control Plane：** ADB shell am start/am broadcast/am force-stop/cmd package；Shizuku 授权 shell/系统服务；Root shell；宿主 Debug Control Agent 的 Binder/AIDL 或独立 socket API；UiAutomator/instrumentation/Espresso。

建议使用 typed command：LaunchActivity(component, typed extras, flags)、SendBroadcast(action, component, typed extras)、ForceStop(package)、InvokeHostDebugAction(actionId, typedArgs)、CaptureUiHierarchy、CaptureScreenshot。执行前校验 package、权限、参数类型、action allowlist 和用户确认策略；结果通过独立 ControlResult 回传。不要把任意 shell 字符串或 Intent 塞进 JDWP packet。

## 11. 当前集成缺陷汇总

| 问题 | 源码证据 | 影响 |
|---|---|---|
| Socket-only adapter | ConnectionBackedDebugger.run() 只调用 attachedSocket() | Root/LocalSocket 无法进入 JDWP engine |
| Root 不支持 Socket | RootConnection.attachedSocket() 抛 UnsupportedOperationException | Root attach 最终失败 |
| Shizuku LocalSocket 不兼容 | InHostPlugin 用 localSocket；attachedSocket() 只返回 socket | InHostPlugin attach 最终失败 |
| 重复 handshake | attach 阶段 AidlJdwpProtocol probe，JdwpClient.connect() 再 handshake | JDWP 流破坏风险 |
| 固定 jdwp endpoint | HostAttachAgent.openLocalAbstractJdwpSocket() 使用固定名称 | 不能保证连接目标 PID |
| HELLO 同流 | HostBridgeServer 先 readLine，随后同 socket 用作 JDWP | 必须严控 reader handoff |
| 多进程仅 warning | processName != packageName 仅 log.warn | 容易 attach 错误 PID |
| 第二个宿主 JDWP client | BreakpointInjectionCodeGen 生成独立 JDWP client | 可能争用 VM debugger endpoint |
| VM.Dispose detach | 多个连接 detach 发送 VM.Dispose | 可能错误终止 VM，而非普通 detach |

## 12. 必须补齐的测试矩阵

协议层：packet 与 JDWP 样本逐字节对照；partial read/write；reply/event 乱序；command ID 并发、回绕、超时和晚到 reply；VM_DEATH/disconnect；unknown event/modifier；动态 IDSizes；多线程命令；close 时 pending future 结束；重连后旧 VM IDs 失效。

设备层：Emulator debuggable app；USB 真机多设备/unauthorized/offline；Wireless ADB 断连重连；Root shell/PID/endpoint/stream；Shizuku Binder 生命周期/授权撤销/FD bridge；多进程 app；force-stop/restart/PID 变化；Android 版本矩阵；厂商 ROM SELinux 差异。

断点语义：无可执行位置；同行多方法；class 未加载；多 classloader；Kotlin inline/suspend/lambda/multifile；condition true/false/eval error；logpoint 不暂停；hit count/temporary；disable/remove 清理 EventRequest；命中后 frame/variable 可读；多线程命中；Step Into/Over/Out；detach 后 app 继续运行；process death 后 UI 复位。

## 13. 修复路线（从底层开始）

### P0：Transport 和 session ownership
1. 建立统一 JdwpTransport：InputStream、OutputStream、close、device serial、package、process、PID、endpoint metadata。
2. 移除 attachedSocket() 作为唯一入口；Socket、LocalSocket、Root process stream、Shizuku FD 适配同一接口。
3. 所有 connection attach 阶段只建链，不执行 JDWP handshake/VM.Version。
4. 唯一 JdwpClient 拥有 handshake、reader、writer 和 packet parser。
5. ADB JDWP forward 作为首条 golden path，完成全流程设备验证。
6. 修正 HostAttachAgent endpoint，必须绑定真实目标 PID；优先复用 ADB JDWP forward。
7. Root/Shizuku 在统一 transport 后再接入。

### P1：宿主发现和生命周期
8. DebugTarget identity 增加 device serial、package、processName、PID、UID、process start identity。
9. 多进程支持列表和用户选择；HELLO 只作 ready signal。
10. HELLO 控制面与 JDWP 数据面分离。
11. 统一 DebugSessionManager 状态机：Connecting → Handshaking → VMReady → Configuring → Running/Suspended → Detaching/Terminated。
12. 正确区分 detach、terminate、VM.Dispose。

### P1：标准断点
13. 以 EventRequest.Set/Clear 和 Composite Event 为唯一断点执行链。
14. 移除/停用第二套宿主 JDWP client。
15. 建立 stable breakpoint UUID ↔ requestID ↔ JDWP Location 映射。
16. 统一 Pending / Verified / Disabled / Invalid / Removed 状态。
17. 补齐 Kotlin SMAP、inline、suspend、multifile、generated source mapping。
18. 建立设备级条件断点、日志断点、hit count 测试。

### P2：IDE 级能力
19. 对照 IntelliJ java/debugger 的 engine/requests/events/suspend manager/JDI/position manager/evaluation 分层。
20. 若目标是深度复刻 IDEA 体验，优先复用 IntelliJ 平台 debugger API，而非继续自研全部 JDI 语义。
21. Native 调试独立使用 LLDB + lldb-server + DAP。
22. Java + Native 由 session coordinator 协调两个 debugger session。
23. Activity/Intent/UI 控制独立实现 DebugControl API。

## 14. 最终判断

- **方向正确的部分：** JDWP 用于 Java/Kotlin VM 调试；ADB/Root/Shizuku 作为权限与 transport 入口；IDE 端集中持有 JDWP client；宿主 helper 做原始字节桥；源码行映射到 class/method/LineTable；UI 消费 SuspendInfo。
- **错误或未完成的部分：** Socket-only adapter 与 LocalSocket/Root stream 冲突；重复 handshake；固定 jdwp socket 名称未按 PID 选择；HELLO 与 JDWP 数据流耦合；宿主代码生成器重复实现 JDWP；多进程发现仅 warning；把断点事件当作 Activity/Intent 控制。
- **是否达到 IDEA/Android Studio 标准：** 否。当前更接近 JDWP 原型和 Android 接入层，尚未达到成熟 IDE debugger 的协议覆盖、源码映射、attach 兼容性、断点生命周期和多调试器协调能力。
- **无法稳定使用的首要原因：** transport-to-engine 适配失败和 JDWP endpoint/handshake 生命周期不一致。
- **最优路线：** 先让 ADB JDWP forward 完成 handshake → VMReady → set breakpoint → hit event → stack/variables → resume/step → detach 闭环；再把 Root/Shizuku 做成透明 transport adapters；最后独立开发 LLDB 与宿主 UI 控制面。

## 15. 源码证据索引

### ZeroStudio
- debugger/Breakpoint-debugger/BREAKPOINT_DEBUGGER_INVESTIGATION.md
- debugger/Breakpoint-debugger/ide-debugger/README.md
- debugger/Breakpoint-debugger/ide-debugger-host/README.md
- debugger/Breakpoint-debugger/ide-debugger/src/main/java/com/zerostudio/debugger/jdwp/JdwpClient.java
- debugger/Breakpoint-debugger/ide-debugger/src/main/java/com/zerostudio/debugger/jdwp/JdwpPacketCodec.java
- debugger/Breakpoint-debugger/ide-debugger/src/main/java/com/zerostudio/debugger/api/Debugger.java
- debugger/Breakpoint-debugger/ide-debugger/src/main/java/com/zerostudio/debugger/api/Breakpoint.java
- debugger/Breakpoint-debugger/ide-debugger/src/main/java/com/zerostudio/debugger/api/SuspendInfo.java
- debugger/Breakpoint-debugger/ide-debugger/src/main/java/com/zerostudio/debugger/model/SourceLocator.java
- debugger/Breakpoint-debugger/ide-debugger-host/src/main/java/com/itsaky/androidide/zerostudio/ide/debugger/host/HostAttachAgent.kt
- debugger/Breakpoint-debugger/ide-debugger-host/src/main/java/com/itsaky/androidide/zerostudio/ide/debugger/host/HostAttachAgentBootstrap.java
- core/app/src/main/java/com/itsaky/androidide/debugger/connection/IDebugConnection.kt
- core/app/src/main/java/com/itsaky/androidide/debugger/connection/ConnectionBackedDebugger.kt
- core/app/src/main/java/com/itsaky/androidide/debugger/connection/host/HostBridgeServer.kt
- core/app/src/main/java/com/itsaky/androidide/debugger/connection/host/BreakpointInjectionCodeGen.kt
- core/app/src/main/java/com/itsaky/androidide/debugger/connection/impl/RootConnection.kt
- core/app/src/main/java/com/itsaky/androidide/debugger/connection/impl/ShizukuConnection.kt
- core/app/src/main/java/com/itsaky/androidide/debugger/connection/impl/AdbForwardConnection.kt

### 对照资料
- JetBrains IntelliJ Community: https://github.com/JetBrains/intellij-community/tree/master/java/debugger
- Android Studio debugger: https://developer.android.com/studio/debug
- Android Studio for Platform debugger: https://developer.android.com/studio/platform/debug
- AOSP debugger / JDWP forwarding: https://source.android.com/docs/core/tests/debug/gdb
- JDWP specification: https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/jdwp-spec.html
- JDWP transport: https://docs.oracle.com/en/java/javase/11/docs/specs/jdwp/jdwp-transport.html

> 审计限制：本报告没有把静态审计包装成设备验证。JDWP abstract socket 命名、不同 Android 版本 SELinux/权限行为、宿主多进程 attach，必须在 emulator 和真实设备上逐项验证。