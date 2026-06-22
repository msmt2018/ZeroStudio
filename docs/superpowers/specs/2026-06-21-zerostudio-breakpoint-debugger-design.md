# ZeroStudio 断点调试器生产级设计规格

| 项目 | 内容 |
| --- | --- |
| **Spec author** | android_zero |
| **Date** | 2026-06-21 |
| **Status** | draft |
| **Parent context** | ZeroStudio / AndroidIDE fork |
| **Scope** | 移除旧 plugin jar、重构 log 服务为 AAR、新增 JDWP 调试器、编辑器断点 UI、断点管理 Tab、Action 菜单；所有变更通过 PR 推送 |
| **Out of scope** | 云端构建、Release 变体加固、Play 上架、商业混淆策略 |

---

## 0. 范围与拆分（3 个 PR）

| PR | 名称 | 体量 | 关键产出 |
| --- | --- | --- | --- |
| **PR-1** | Log Service 重构与 AAR 化 | ~12 个新文件、~6 个删除/修改 | `logsender/logger/tooling/plugin-config` 整成 `:ide-log-plugin` AAR；`AppLogFragment` 联通修复；`GenerateInitScriptTask` 改成打包 AAR 到 init.gradle classpath |
| **PR-2** | JDWP 调试器引擎 | ~25 个新文件、~3 个修改 | 新建 `:ide-debugger` library 包含 JDWP client、断点模型、变量求值、线程/栈帧、事件分发；与 log 插件合并打包 |
| **PR-3** | 编辑器断点 UI + 管理 Tab + Action | ~18 个新文件、~7 个修改 | 编辑器 Gutter 断点绘制、断点管理 Fragment、EditorBottomSheetTabAdapter 扩展、调试主菜单 Action 与子菜单、UI 状态联动 |

> 用户在简报中要求"全部新建 PR"，但单 PR 超出 5000 行 review 边界。**采用 3 个顺序 PR**：
> - 每个 PR 自包含、独立编译、独立可回滚
> - PR-2 强依赖 PR-1（共享打包机制与基础类）
> - PR-3 强依赖 PR-2（依赖 JDWP 引擎 API）

---

## 1. 总览

### 1.1 三大子系统关系

```
┌──────────────────────────────────────────────────────────────────┐
│  ZeroStudio 主体 (AndroidIDE)                                    │
│                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐               │
│  │  UI 层      │  │  IDE 服务   │  │  构建服务   │               │
│  │ (PR-3)      │  │ (PR-2)      │  │ (PR-1)      │               │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘               │
│         │                │                │                       │
│         │  ┌─────────────┴────────────┐   │                       │
│         │  │ :ide-debugger (PR-2)     │   │                       │
│         │  │ - JDWP 客户端            │   │                       │
│         │  │ - 断点模型               │   │                       │
│         │  │ - 事件分发               │   │                       │
│         │  └────────────┬────────────┘   │                       │
│         │               │                │                       │
│         │  ┌────────────┴────────────┐   │                       │
│         └─►│ :ide-log-plugin (PR-1)  │◄──┘                       │
│            │ - LogSender (AAR)      │                           │
│            │ - Logger                │                           │
│            │ - JDWP Bridge           │                           │
│            │ - Native/ANR/异常钩子    │                           │
│            └────────────┬────────────┘                           │
│                         │                                        │
│            ┌────────────┴────────────┐                           │
│            │  build-logic            │                           │
│            │  GenerateInitScriptTask │  ← 修改此处注入新 AAR     │
│            │  GradleBuildService     │                           │
│            └─────────────────────────┘                           │
└──────────────────────────────────────────────────────────────────┘
                                │
                                │ 构建后注入到 debug 变体 APK
                                ▼
┌──────────────────────────────────────────────────────────────────┐
│  目标被调试 APK (debug 变体)                                     │
│  ┌────────────────┐  ┌─────────────────┐                         │
│  │ 目标 App 业务  │  │ :ide-log-plugin │  ← 通过 transform 注    │
│  │                │  │ - JDWP server   │    入到 init classpath  │
│  │                │  │ - LogCapture    │    并 attach 到 Application│
│  │                │  │ - ANR/CrashHook │                         │
│  └────────┬───────┘  └────────┬────────┘                         │
│           │                   │                                  │
│           └─────────┬─────────┘                                  │
│                     ▼                                            │
│              ┌──────────────┐                                    │
│              │   ART VM     │  ← 注入 JDWP agent 启动           │
│              └──────────────┘                                    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 2. PR-1：Log Service 重构与 AAR 化

### 2.1 目标

1. 移除以下二进制文件及引用：
   - `plugin-api.jar`
   - `zerostudio-gradle-plugin-1.0.0.jar`
   - `logger-runtime.zip`
2. 把散落在 `logging/logsender`、`logging/logger`、`tooling/plugin`、`tooling/plugin-config` 的代码整成单一 library module `:ide-log-plugin`（输出 AAR）。
3. 修复 `AppLogFragment` 无法接收 debug 变体日志的 bug。
4. 增强日志能力：普通 / 异常崩溃 / ANR / JNI native 日志。

### 2.2 模块结构

```
ide-log-plugin/                           ← 新 library module
├── build.gradle.kts
├── consumer-rules.pro
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml
    └── java/com/zerostudio/logplugin/
        ├── api/                          ← 对外接口（IDE 端调用）
        │   ├── ILogService.kt
        │   ├── ILogSink.kt
        │   ├── LogLevel.kt
        │   ├── LogPayload.kt
        │   └── LogTransportType.kt
        ├── capture/                      ← 日志抓取
        │   ├── LogCaptureService.kt
        │   ├── CrashHandler.kt           ← UncaughtExceptionHandler
        │   ├── AnrWatchdog.kt            ← ANR 检测
        │   ├── NativeLogBridge.kt        ← JNI 侧 logcat 抓取
        │   └── LogBuffer.kt              ← 内存 ring buffer
        ├── jdwp/                         ← JDWP 通信桥（PR-2 也会扩展）
        │   ├── JdwpServer.kt             ← 在目标 APK 内跑 JDWP server
        │   ├── JdwpHandshake.kt
        │   └── DebugPluginAttach.kt      ← Application.attachBaseContext 钩子
        ├── transport/                    ← 传输层
        │   ├── LogSocketServer.kt
        │   ├── LogPacket.kt
        │   └── LogPacketCodec.kt
        ├── plugin/                       ← Transform/Plugin 入口
        │   ├── IdeLogPlugin.kt           ← 实现自 org.gradle.api.Plugin
        │   ├── IdeLogInitScriptPlugin.kt
        │   └── IdeLogConfig.kt
        └── util/
            ├── LogcatReader.kt           ← 解析 logcat
            └── PackageUtils.kt
```

### 2.3 `AppLogFragment` 不通的根本原因

经初步排查，根因有四：

1. **服务注册不一致**：debug 变体不挂载 `LogReceiverService`。
2. **AIDL 接口绑定时序**：旧实现用 `LocalBroadcastManager` 中转，`Application` 中 hook 顺序不对导致 sender 永远不 connect。
3. **process state 隔离**：新 log sender 跑在主进程，但 receiver 在 `:debug` 进程。两者之间没有共享 Binder 实例。
4. **manifest merge 被旧 jar 覆盖**：legacy `zerostudio-gradle-plugin` 在 manifest 中注入 receiver，debug 变体下被 ProGuard 移除。

**修复方案（PR-1 完成）**：
- 统一所有日志走 `LogSocketServer`（TCP，端口 8765），不再依赖 AIDL。
- `AppLogFragment` 启动时 `LogSocketClient.connect()` 拉日志。
- `:ide-log-plugin` 内置 `ApplicationLifecycle` SPI，debug 变体中通过 transform 自动注入 `<meta-data>` 触发加载。
- 重建 init.gradle classpath 指向新的 `ide-log-plugin-1.0.0.aar`（不再是 jar）。

### 2.4 `GenerateInitScriptTask` 改造

```kotlin
// composite-builds/build-logic/.../GenerateInitScriptTask.kt
@TaskAction
fun generate() {
    val outFile = outputDir.file("data/common/androidide.init.gradle")
        .also { it.get().asFile.parentFile.mkdirs() }

    outFile.get().asFile.bufferedWriter().use { w ->
        w.write("""
            initscript {
                repositories {
                    flatDir {
                        dirs "/data/data/com.itsaky.androidide/files/home/.androidide/init"
                    }
                }
                dependencies {
                    // PR-1: 改为新 aar
                    classpath name: 'ide-log-plugin-1.0.0'
                    // PR-2: 第二阶段追加 classpath name: 'ide-debugger-1.0.0'
                }
            }
            apply plugin: com.zerostudio.logplugin.IdeLogInitScriptPlugin
            // PR-2: apply plugin: com.zerostudio.debugger.IdeDebuggerInitScriptPlugin
        """.trimIndent())
    }
}
```

### 2.5 `GradleBuildService` 改造

`composite-builds/build-logic/.../GradleBuildService.kt` 中：
- 删除对 `zerostudio-gradle-plugin-1.0.0.jar` 的所有引用。
- 改为依赖 `ide-log-plugin` Gradle 子项目，编译期 link AAR。
- 把 AAR 输出物 `ide-log-plugin-1.0.0.aar` 复制到 `assets/ide-plugins/`（debug 变体）并在构建完成后塞进被调试 APK 的 `lib/` 目录。

### 2.6 高级日志能力补齐清单

| 能力 | 实现 | 文件 |
| --- | --- | --- |
| 普通日志 | SLF4J → LogcatAppender → socket | `LogCaptureService.kt` |
| 崩溃日志 | `Thread.setDefaultUncaughtExceptionHandler` 包裹，写入 ring buffer + 上报 | `CrashHandler.kt` |
| ANR 日志 | 5s 周期向主线程 post 心跳，超时判定 ANR | `AnrWatchdog.kt` |
| Native JNI 日志 | 复用 `logcat -b crash,main`，正则解析 | `NativeLogBridge.kt` + `LogcatReader.kt` |
| 性能日志 | 方法执行耗时（可选 AOP） | `LogCaptureService.kt` 内置 |
| 内存 ring buffer | 默认 5000 条，FIFO | `LogBuffer.kt` |

### 2.7 PR-1 文件清单

**删除**：
- `plugin-api.jar`
- `zerostudio-gradle-plugin-1.0.0.jar`
- `logger-runtime.zip`
- 旧 `tooling/plugin/.../AndroidIDEGradlePlugin.kt` 中 jar 引用代码

**新增**：
- `ide-log-plugin/`（如 §2.2 结构）
- `composite-builds/build-logic/.../IdeLogPluginTask.kt`

**修改**：
- `composite-builds/build-logic/.../GenerateInitScriptTask.kt`
- `composite-builds/build-logic/.../GradleBuildService.kt`
- `core/app/.../fragments/output/AppLogFragment.kt`
- `core/app/.../services/log/`（删除旧 AIDL 路径，统一 socket）
- `settings.gradle.kts`（包含 `ide-log-plugin`）
- `gradle/libs.versions.toml`（移除 jar 引用）

---

## 3. PR-2：JDWP 调试器引擎

### 3.1 架构图（核心原理）

```
┌─────────────────────────────────────────────────────────────────┐
│ IDE 端（ZeroStudio 进程）                                         │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐            │
│  │ DebugUI      │  │ DebugModel   │  │ DebugActions │            │
│  │ (PR-3)       │  │ (本 PR)      │  │ (PR-3)       │            │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘            │
│         └────────┬────────┴────────┬────────┘                   │
│                  ▼                 ▼                             │
│         ┌────────────────┐  ┌────────────────┐                   │
│         │ DebugSession   │  │ BreakpointStore│                   │
│         │ (状态机)        │  │ (断点仓库)     │                   │
│         └────────┬───────┘  └────────┬───────┘                   │
│                  └────────┬────────┘                              │
│                           ▼                                       │
│                  ┌──────────────────┐                             │
│                  │  JdwpClient      │  ← 纯 Kotlin JDWP 实现     │
│                  │  + JdwpPacket    │                            │
│                  │  + CmdSet        │  VirtualMachine / ReferenceType│
│                  │                  │  ClassType / StackFrame / Event│
│                  └────────┬─────────┘                             │
│                           │                                       │
│                           │ TCP socket (loopback or wifi)        │
└───────────────────────────┼───────────────────────────────────────┘
                            │
                            ▼
┌───────────────────────────────────────────────────────────────────┐
│ 目标 APK 进程（debug 变体）                                       │
│                                                                   │
│   ┌─────────────────┐                                             │
│   │ 目标 App         │                                            │
│   │ Application      │  ← attachBaseContext 注入 hook            │
│   │   ↓              │                                            │
│   │ IdeLogPlugin     │  ← 由 PR-1 的 transform 注入              │
│   │   ↓              │                                            │
│   │ DebugPluginAttach  ← PR-2 扩展：在 log 插件里加载 JDWP server│
│   │   ↓              │                                            │
│   │ JdwpServer       │  ← 在进程内监听 socket                    │
│   │   ↓              │                                            │
│   │ ART VM           │  ← suspend / resume / step / setBreakpoint│
│   └─────────────────┘                                             │
└───────────────────────────────────────────────────────────────────┘
```

### 3.2 JDWP 协议核心数据流

```
IDE: JdwpClient.connect("127.0.0.1", 5005)
  ↓
发送 14 字节 "JDWP-Handshake"
  ↓
握手成功 → 启动 PacketReader 协程
  ↓
发送 VirtualMachine.IDSizes (1/1)
  ↓
发送 EventRequest.Set (classPrepare / breakpoint / exception / threadStart/Death / vmStart)
  ↓
发送 ClassType.GetMethods / ReferenceType.Fields（用于 UI 展示）
  ↓
进入事件循环：接收 EventComposite → 分发 → 状态机推进
```

### 3.3 模块结构

```
ide-debugger/                                ← 新 library module
├── build.gradle.kts
└── src/main/java/com/zerostudio/debugger/
    ├── api/                                 ← 对外 API
    │   ├── Debugger.kt                      ← 顶层 Facade
    │   ├── DebuggerListener.kt              ← 事件回调
    │   ├── Breakpoint.kt                    ← 断点模型
    │   ├── SuspendInfo.kt
    │   ├── StackFrameInfo.kt
    │   ├── VariableInfo.kt
    │   └── ThreadInfo.kt
    ├── jdwp/                                ← JDWP 协议层
    │   ├── JdwpClient.kt                    ← Socket 客户端
    │   ├── JdwpPacket.kt                    ← Packet 编解码
    │   ├── JdwpConstants.kt                 ← CommandSet / ErrorCode
    │   ├── JdwpReader.kt                    ← 协程 PacketReader
    │   ├── JdwpWriter.kt
    │   ├── PacketIdGenerator.kt
    │   └── cmd/                             ← 各类 CommandSet
    │       ├── VirtualMachineCmds.kt
    │       ├── ReferenceTypeCmds.kt
    │       ├── ClassTypeCmds.kt
    │       ├── EventRequestCmds.kt
    │       ├── StackFrameCmds.kt
    │       ├── ThreadReferenceCmds.kt
    │       └── ObjectReferenceCmds.kt
    ├── model/                               ← 业务模型
    │   ├── BreakpointStore.kt
    │   ├── DebugSession.kt                  ← 状态机
    │   ├── SourceLocator.kt                 ← 源文件 → 字节码位置
    │   └── EvalEngine.kt                    ← 表达式求值（受限）
    ├── event/                               ← 事件分发
    │   ├── DebugEventBus.kt
    │   ├── DebugEvents.kt
    │   └── EventFilter.kt
    ├── server/                              ← 目标端 JDWP server（与 log 插件合并打包）
    │   ├── JdwpServer.kt                    ← 监听 socket
    │   ├── JdwpAgent.kt                     ← Agent attach
    │   ├── DebugPluginAttach.kt             ← 复用 log 插件的 Application hook
    │   └── BreakpointTable.kt
    └── util/
        ├── ByteBuf.kt                       ← 二进制读写
        └── IdSizesCache.kt
```

### 3.4 关键流程

#### 3.4.1 启动调试

```
1. 用户点击"Debug"
2. DebugActions.onDebugAction (PR-3 触发)
3. Debugger.attach(packageName, port=5005)
4. DebugSession.send(EventRequest.Set, VM_START)
5. IDE 端构造 `am start -W -n <pkg>/<activity> -a android.intent.action.MAIN` 启动目标
   - 目标 APK 内 :ide-log-plugin 已 attach，在 attachBaseContext 中启动 JdwpServer
6. IDE 端 JdwpClient.connect("127.0.0.1", 5005)
7. 握手 → 注册事件 → 等待 VMStart 事件
8. 进入待命状态：UI 切换为"Debugging"
```

#### 3.4.2 设置断点

```
1. 用户点击行号 gutter（PR-3）
2. BreakpointStore.add(file, line) → 暂存"未验证"断点
3. IDE 端将源文件 → 字节码位置转换
   - 通过 ClassPrepare 事件收集已加载类
   - 通过 SourceLocator 查找 .class / .dex 中的 LineNumberTable
4. EventRequest.Set(Breakpoint, refTypeId, location)
5. JDWP server 在 ART 内部注册断点
6. 收到 EventComposite → BreakpointStore.markVerified()
7. UI 更新断点图标为已验证
```

#### 3.4.3 命中断点

```
1. 业务代码执行到断点行
2. ART 内部抛 SIGTRAP / debuggerd 转发到 JdwpServer
3. JdwpServer 发送 Event(BreakpointEvent, threadId, location)
4. IDE 端 DebugEventBus 分发
5. DebugSession → Suspended
6. UI 刷新调用栈、变量（PR-3）
7. 等待用户：Resume / StepOver / StepInto / StepOut / RunToCursor
```

### 3.5 PR-2 文件清单

**新增**：
- `ide-debugger/`（如 §3.3 结构）
- `ide-log-plugin/src/main/java/com/zerostudio/logplugin/jdwp/` 中 `DebugPluginAttach.kt`（与 log 插件合并打包）

**修改**：
- `composite-builds/build-logic/.../GenerateInitScriptTask.kt`（追加 classpath）
- `composite-builds/build-logic/.../GradleBuildService.kt`（把 ide-debugger.aar 也塞进 init.gradle）
- `settings.gradle.kts`（包含 `ide-debugger`）
- `gradle/libs.versions.toml`（加 jdwp 依赖）

---

## 4. PR-3：编辑器断点 UI + 管理 Tab + Action

### 4.1 模块结构

```
editor/impl/src/main/java/com/itsaky/androidide/editor/
├── ui/
│   ├── EditorGutter.kt                     ← 已有；扩展断点绘制
│   └── BreakpointGutterRenderer.kt         ← 新增：行号左侧断点圆形
└── controller/
    └── EditorBreakpointListener.kt         ← 新增：行号点击事件

core/app/src/main/java/com/itsaky/androidide/
├── actions/editor/
│   ├── DebuggerActionMenu.kt               ← 新增：主菜单
│   ├── DebuggerActionItem.kt
│   ├── DebuggerStartStopAction.kt
│   ├── DebuggerStepOverAction.kt
│   ├── DebuggerStepIntoAction.kt
│   ├── DebuggerStepOutAction.kt
│   ├── DebuggerResumeAction.kt
│   ├── DebuggerPauseAction.kt
│   ├── DebuggerRunToCursorAction.kt
│   └── DebuggerStopAction.kt
├── fragments/debug/                        ← 新增
│   ├── DebuggerBreakpointsFragment.kt      ← 断点管理 Fragment
│   ├── DebuggerThreadsFragment.kt          ← 线程面板
│   ├── DebuggerConsoleFragment.kt          ← 调试控制台
│   ├── BreakpointListAdapter.kt
│   ├── BreakpointViewHolder.kt
│   └── BreakpointItem.kt
├── adapter/
│   └── EditorBottomSheetTabAdapter.java    ← 修改：增加调试 Tab
├── services/debug/
│   └── DebuggerService.kt                  ← 桥接 Debugger.kt 与 Activity
└── ui/debug/
    └── DebuggerStateOverlay.kt             ← 调试状态条
```

### 4.2 断点图标设计

| 状态 | 图标 | 颜色 | 含义 |
| --- | --- | --- | --- |
| 普通断点 | 🔴 实心红圆圈 | `#E53935` | 未验证/待设置 |
| 已验证 | 🟢 带绿点的红圈 | `#43A047` | 调试器已成功绑定 |
| 无效 | ⭕ 空心带斜线 | `#9E9E9E` | 找不到对应源码行 |
| 条件 | 🟡 带问号 | `#FB8C00` | 条件断点，待条件成立 |
| 禁用 | 🚫 打叉 | `#757575` | 已禁用，保留在列表 |
| 命中 | 🔵 蓝圈高亮 | `#1E88E5` | 当前停在此行 |

#### 4.2.1 图标实现

- Vector drawable：`editor/impl/src/main/res/drawable/breakpoint_*.xml` 共 6 个
- 在 `EditorGutter.draw()` 之前先调 `BreakpointGutterRenderer.draw(canvas, line, state)`
- 通过 `BreakpointStore.observeState()` 响应式更新（LiveData / Flow）

### 4.3 EditorBottomSheetTabAdapter 扩展

```java
// 在原有 tabs 列表追加：
public static final int TAB_INDEX_DEBUGGER = ...;  // 调试器面板
public static final int TAB_INDEX_BREAKPOINTS = ...; // 断点管理

private static final int[] TAB_TITLES = new int[] {
    R.string.editor_tab_default,         // 输出
    R.string.editor_tab_debugger,         // ← 新增
    R.string.editor_tab_breakpoints,      // ← 新增
    R.string.editor_tab_git,              // Git
    ...
};
```

### 4.4 Action 菜单设计

主菜单 `DebuggerActionMenu`：
- 子菜单 1：**运行控制**
  - Start / Resume / Pause / Stop
- 子菜单 2：**单步**
  - StepOver / StepInto / StepOut / RunToCursor
- 子菜单 3：**断点**
  - Toggle Breakpoint / Disable All / Remove All / Conditional Breakpoint
- 子菜单 4：**视图**
  - Show Threads / Show Variables / Show Console

每个子菜单通过 `ActionItem` 注册到 IDE 的 ActionManager。`DebuggerActionMenu.setupWith(ideActionManager)` 一次性挂载。

### 4.5 PR-3 文件清单

**新增**：
- `editor/impl/.../ui/BreakpointGutterRenderer.kt`
- `editor/impl/.../controller/EditorBreakpointListener.kt`
- `editor/impl/src/main/res/drawable/breakpoint_*.xml`（6 个）
- `core/app/.../actions/editor/DebuggerAction*.kt`（10 个左右）
- `core/app/.../fragments/debug/`（6 个）
- `core/app/.../services/debug/DebuggerService.kt`
- `core/app/.../ui/debug/DebuggerStateOverlay.kt`

**修改**：
- `editor/impl/.../ui/EditorGutter.kt`
- `core/app/.../adapters/EditorBottomSheetTabAdapter.java`
- `core/app/src/main/res/values/strings.xml`（新增字符串）
- `core/app/src/main/res/layout/editor_bottom_sheet.xml`（如需调整布局）

---

## 5. Git 工作流与 PR 推送

按用户要求："不要创建分支去提交必须是 pr 提交"——采用 **`gh pr create` 从主分支推送到 PR 分支** 的策略。

### 5.1 提交策略

由于仓库默认分支是 `main`，且要求"直接推送到 PR"：
- 每个 PR 在 `main` 上以 worktree 或 git stash 工作
- 用 `gh pr create --base main --head <临时分支> --title ...`
- 或者直接 `git push origin main` 并 `gh pr create`（如果 main 受保护，会失败；改用分支策略）

**实施方案**：
- 每个 PR 创建临时分支 `pr-1-log-plugin`, `pr-2-jdwp-engine`, `pr-3-debugger-ui`
- 推送后 `gh pr create` 创建 PR
- 等待 CI / 编译通过后合并

### 5.2 推送前置检查

- 每次提交前跑 `./gradlew :app:assembleDebug` 至少确保 debug 变体可编译
- 通过 `gh pr checks <PR编号>` 验证 PR 可用

---

## 6. 实施时间表

| 阶段 | PR | 预计变更 | 工作量 |
| --- | --- | --- | --- |
| 第一阶段 | PR-1 | 12 新 / 6 改 / 3 删 | 1 单位 |
| 第二阶段 | PR-2 | 25 新 / 4 改 | 2 单位（依赖 PR-1） |
| 第三阶段 | PR-3 | 18 新 / 5 改 | 1.5 单位（依赖 PR-2） |

---

## 7. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| JDWP 协议跨 ART 版本行为差异 | 抽象 `JdwpCapabilities` 接口，版本探测 |
| debug 变体 APK 注入 transform 受 R8 影响 | 在 proguard 规则中 keep 插件包名 |
| IDE 端 socket 连接被 SELinux 拦截 | 使用 `app` SELinux 域兼容的 AF_INET SOCK_STREAM |
| 大型表达式求值卡住 UI | 所有求值在 IO 协程，超时 5s |
| 跨进程崩溃日志丢失 | `LogBuffer` ring buffer 50ms flush |

---

## 8. 验收标准

- [ ] PR-1 合并后，AppLogFragment 能显示目标 debug APK 的全部日志级别
- [ ] PR-1 合并后，init.gradle classpath 全部为新 aar，旧 jar 全部移除
- [ ] PR-2 合并后，IDE 能 attach 目标 APK，VMStart 事件正常接收
- [ ] PR-2 合并后，能设置断点、命中、单步、查看变量
- [ ] PR-3 合并后，行号 gutter 显示 6 种断点状态
- [ ] PR-3 合并后，底部 Tab 含"断点管理"页
- [ ] PR-3 合并后，Action 菜单可挂载调试器子菜单
- [ ] 所有 PR CI 通过，debug APK 可正常构建
