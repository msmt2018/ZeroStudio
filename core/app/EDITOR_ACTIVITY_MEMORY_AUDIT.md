# 编辑器 Activity 启动后进程初始内存偏高排查报告

> 检查日期：2026-11-29  
> 检查分支：`main`  
> 检查提交：`8c01bd143f53265b2023e4aa159166dc411b50a7`  
> 检查范围：`core/app` 编辑器 Activity、Application 启动链、编辑器文件恢复、语言服务与构建服务连接路径。  
> 检查方式：静态源码追踪。当前没有目标设备的 `dumpsys meminfo`、Perfetto 或 heapprofd 实测数据，因此本文不会把源码推断伪装成 500 MB 的实测归因。

## 1. 结论摘要

目前源码中没有发现一个单独、明确分配 500 MB 的语句。**500 MB 不能仅凭源码直接定量归因**，必须先确认该数值对应的是应用进程 PSS、RSS、Java Heap、Native Heap，还是系统统计的多进程/整包内存。

但启动链中存在数个明显值得优先验证的高内存路径：

1. **Application 进程级初始化过早且范围偏大**：每次进程启动都会装配 ChatAI 的 Koin 模块并调用 `QuickJSLoader.init()`，同时初始化 Termux 全局环境、EventBus、颜色方案和调试器自动连接协调器。这些发生在 EditorActivity 创建之前，不是 Gradle 构建任务造成的。
2. **EditorActivity 会恢复上次打开的全部文件标签**：`EditorHandlerActivity.onStart()` 读取 `OpenedFilesCache` 后，对 `cache.allFiles` 逐项调用 `openFile()`。每项都会创建一个完整的 `CodeEditorView`，并启动文件读取/内容初始化。文件多或单文件大时，会形成明显的瞬时内存峰值。
3. **编辑器初始化会同时启用多个语言能力**：Activity 注册 Java、XML、TOML、Clangd 服务；项目初始化时还会主动调用 Kotlin LSP 集成。打开 Kotlin 文件会建立诊断分析、补全/悬停提示等编辑器能力。
4. **Tree-sitter Native 库在 EditorActivity 创建后异步加载**：这会增加应用进程 Native 映射/运行时占用，但仅凭 `loadLibrary()` 不能推断其实际常驻内存大小。
5. **Debug 构建存在额外的 LeakCanary**：`core/app/build.gradle.kts` 通过 `debugImplementation` 引入 LeakCanary。若 500 MB 数值来自 Debug APK，必须与 Release APK 对照，避免把调试诊断开销当成正式版本基线。

因此，优先怀疑顺序是：**恢复多个/大型编辑文件及其编辑器实例 → Application 级 ChatAI/QuickJS 初始化 → 项目级 Kotlin LSP/语言服务初始化 → Tree-sitter Native 加载和 Debug 诊断开销**。这是待设备数据验证的排查优先级，不是已经测出的占比。

## 2. 启动链路与源码证据

### 2.1 Application 在进入编辑器前已经执行的工作

文件：`core/app/src/main/java/com/itsaky/androidide/app/IDEApplication.kt`

`onCreate()` 中可确认以下工作：

- 继承 `TermuxApplication` 并执行 `super.onCreate()`，然后设置 Termux 的全局 Application 引用。
- 通过 `startKoin { ... }` 装配 ChatAI 的 `appModule`、`viewModelModule`、`dataSourceModule`、`repositoryModule`。
- 无条件调用 `QuickJSLoader.init()`，加载 ChatAI 使用的 QuickJS Native 库。
- 安装多个 EventBus 索引并注册 Application 事件订阅者。
- 初始化全局主题/颜色方案；通过 `GlobalScope.launch(Dispatchers.IO)` 执行 `IDEColorSchemeProvider.init()`。
- 另一个后台协程创建并启动 `AppReadyAutoConnect`。

这些初始化属于**应用进程启动成本**，即使用户只是进入编辑器、尚未使用 ChatAI，也会先发生 Koin 容器装配和 QuickJS Native 库初始化。Koin 的模块装配不等于其中每个定义的对象都已实例化；具体对象是否 eager 创建，仍需检查模块定义和运行时对象图，不能仅凭 `startKoin` 推断所有服务都已加载。

需要特别核对：QuickJS 当前是全局启动时加载，而不是用户首次打开 ChatAI/脚本功能时再加载。它是优先级较高的可验证优化点，但必须先测量 Native Heap/PSS，不能假定它单独占用数百 MB。

### 2.2 EditorActivity 的创建阶段

入口：

- `core/app/src/main/AndroidManifest.xml`：声明 `.activities.editor.EditorActivityKt`。
- `core/app/src/main/java/com/itsaky/androidide/activities/editor/EditorActivityKt.kt`：`EditorActivityKt` 继承 `EditorHandlerActivity`，并使用 `@AndroidEntryPoint`。
- `core/app/src/main/java/com/itsaky/androidide/activities/editor/BaseEditorActivity.kt`：`bindLayout()` 调用 `ActivityEditorBinding.inflate(layoutInflater)`，创建完整编辑器布局视图树。

`BaseEditorActivity.onCreate()` 还会：

- 调用 `registerLanguageServers(this)`。
- 初始化编辑器抽屉、Tab、Views、容器和诊断区域。
- 创建内存图表并注册自身进程内存监控。
- 在首次显示时执行底部抽屉引导动画/状态流程。

内存图表本身只为每个被监控进程创建 30 个点的数据集，按当前源码规模不是 500 MB 的合理解释。它不应被当作首要嫌疑。

### 2.3 语言服务注册与 Kotlin LSP 启动

文件：`core/app/src/main/java/com/itsaky/androidide/handlers/LspHandler.kt`

`registerLanguageServers()` 会向注册表注册：

- JavaLanguageServer
- XMLLanguageServer
- TomlServer
- ClangdLanguageServer

Kotlin LSP 在该函数中没有直接启动；源码注释说明它由 `KotlinLspIntegration` 在工作区/类路径准备后启动。

但文件 `core/app/src/main/java/com/itsaky/androidide/activities/editor/ProjectHandlerActivity.kt` 的 `initializeProject()` 会在项目初始化早期主动执行 `KotlinLspIntegration.setup()`。此外，打开 Kotlin/KTS 文件时，`EditorHandlerActivity.openFileAndGetIndex()` 也会调用该方法。

文件 `lsp/kotlin/src/main/java/com/itsaky/androidide/lsp/kotlin/KotlinLspIntegration.kt` 显示该方法会：

- 初始化 Kotlin 文档同步处理器；
- 创建 `KotlinServerProcessManager`；
- 在可用时创建/设置 `KotlinWorkspaceSetup`；
- 调用 `processManager.startServer()`；
- 连接 Kotlin Language Client，并异步等待服务注册、应用设置。

这条链路在项目打开后可能带来较大的额外资源开销。需要进一步区分 Kotlin LSP 服务进程自身内存与 IDE 主进程内存；如果 LSP 是独立子进程，其 PSS 不应直接记入 IDE 主 PID。

### 2.4 最近打开文件恢复：最值得先验证的峰值来源

文件：`core/app/src/main/java/com/itsaky/androidide/activities/editor/EditorHandlerActivity.kt`

`onStart()` 会调用：

```kotlin
editorViewModel.getOrReadOpenedFilesCache(this::onReadOpenedFilesCache)
```

`onReadOpenedFilesCache(cache)` 对缓存中的 `cache.allFiles` 执行：

```kotlin
cache.allFiles.forEach { file -> openFile(File(file.filePath), file.selection) }
```

每次打开新文件，`openFileAndGetIndex()` 都会创建新的 `CodeEditorView(this, file, selection)`，将其加入 `editorContainer`，并加入一个文件 Tab。

文件：`core/app/src/main/java/com/itsaky/androidide/ui/CodeEditorView.kt`

每个 `CodeEditorView` 的初始化会创建：

- `LayoutCodeEditorBinding` 和 Sora 编辑器控件；
- `EditorSearchLayout`；
- 独立的编辑器协程作用域；
- 独立的单线程 `readWriteContext`；
- 光标历史跟踪、内容变化监听和自动保存监听。

之后 `readFileAndApplySelection()` 在协程中调用 `file.readContent()`，得到编辑器 `Content`，再通过 `initializeContent()` 将内容设置到 Sora Editor。该路径没有在 Activity 恢复入口按文件数量、文件大小或总字节数进行限流。

这意味着：如果缓存里保存了很多文件，恢复流程可能在短时间内创建很多编辑器实例并并发读取多个文件。大型源文件会同时产生文件读取缓冲、编辑器内容结构、语法/诊断相关状态等内存成本。**这是当前源码中最直接、最应该优先做冷启动 A/B 验证的候选原因。**

需要注意：源码能证明“逐个创建实例并触发读取”，但不能单凭这段代码证明读取一定同时完成，也不能给出每个编辑器的实际内存成本；这些要用运行时采样确认。

### 2.5 Tree-sitter Native 加载

文件：`core/app/src/main/java/com/itsaky/androidide/activities/editor/EditorHandlerActivity.kt`

`onCreate()` 在注册 Tree-sitter 语言后启动后台任务：

```kotlin
executeAsync {
  CodeEditorView.ensureTreeSitterLoaded()
  IDEColorSchemeProvider.initIfNeeded()
}
```

文件：`core/app/src/main/java/com/itsaky/androidide/ui/CodeEditorView.kt`

`ensureTreeSitterLoaded()` 最终调用 `TreeSitter.loadLibrary()`。这会把 Tree-sitter Native 库加载进 IDE 进程。应通过 Native Heap、PSS、映射区域和 heapprofd 验证其真实成本；不能把 APK 中 Native 库文件大小等同于运行时 RAM 占用。

### 2.6 Gradle Build Service 与 IDE 进程内存的边界

文件：`core/app/src/main/java/com/itsaky/androidide/activities/editor/ProjectHandlerActivity.kt`

编辑器 `onCreate()` 会调用 `startServices()`，通过 `bindService(Intent(this, GradleBuildService::class.java), ..., BIND_AUTO_CREATE or BIND_IMPORTANT)` 绑定服务。随后服务连接时才可能启动 Tooling Server。

Manifest 中的 `GradleBuildService` 没有声明独立的 `android:process`，因此 Service 组件本身默认与应用运行在同一 Android 进程；但 Tooling Server 由 `ToolingServerRunner` 启动，属于另一个进程/运行实体。**CI/GitHub Actions 的构建机器内存与设备上的 IDE 主进程内存完全不是同一个统计对象；Tooling Server 子进程的内存也不能直接算作 IDE 主 PID 的 PSS。**

本报告不把 Gradle 构建服务认定为 500 MB 的根因。为了严格排除它，设备测试时应同时记录 IDE PID、Gradle Tooling PID 和各自 PSS，而不是把进程列表中的总内存归并成一个数字。

## 3. 当前证据强度

| 候选路径 | 源码能确认的行为 | 对 500 MB 的判断 |
|---|---|---|
| 恢复所有上次打开文件 | 缓存文件逐项创建 CodeEditorView 并读取文件内容 | 高优先级候选；需记录恢复文件数、文件大小与分阶段 PSS |
| ChatAI Koin 初始化 | Application 每次启动装配四个 Koin 模块 | 中高优先级；需检查模块 eager 定义及对象实例化 |
| QuickJS Native 初始化 | Application 无条件调用 QuickJSLoader.init() | 中高优先级；需 Native Heap/PSS A/B |
| Kotlin LSP | 项目初始化时启动进程管理器、工作区设置和服务连接 | 项目打开阶段候选；必须分 PID 统计 |
| Java/XML/TOML/Clangd 注册 | EditorActivity 注册四个服务实现 | 中优先级；注册不等于服务全部运行，需查实现构造和首次使用行为 |
| Tree-sitter Native | EditorActivity 创建后后台加载 Native 库 | 中优先级；需测 Native 映射和驻留页 |
| Debug LeakCanary | Debug 变体引入 LeakCanary | 重要测量干扰项；需与 Release 对照 |
| 内存图表/30 个历史点 | 每个被监控进程创建固定 30 个图表点 | 低优先级，不像 500 MB 来源 |

## 4. 设备侧必须采集的数据

请使用同一台设备、同一 Android 版本、相同 APK 变体、相同项目目录和相同热/冷启动条件。每个阶段都记录主进程 PID 和完整 `dumpsys meminfo`，不要只记录一个“内存”数字。

### 4.1 基础采样命令

将包名替换为实际 applicationId：

```bash
PKG=com.itsaky.androidide
adb shell am force-stop "$PKG"
adb shell monkey -p "$PKG" 1
```

启动后取得主进程 PID：

```bash
adb shell pidof "$PKG"
```

对返回的 IDE 主 PID 执行：

```bash
PID=<IDE主进程PID>
adb shell dumpsys meminfo "$PID"
adb shell cat "/proc/$PID/smaps_rollup"
adb shell cat "/proc/$PID/status"
```

同时对 Gradle Tooling、Kotlin LSP 等子进程分别执行 `dumpsys meminfo`，并保存完整 PID 列表：

```bash
adb shell ps -A -o PID,PPID,RSS,NAME
```

重点字段：

- `TOTAL PSS` / `TOTAL RSS`：区分共享页分摊与实际映射驻留；
- `Java Heap`、`Native Heap`：判断 managed/native 分配；
- `Graphics`、`EGL mtrack`、`GL mtrack`：排查图形资源；
- `Private Dirty`、`Private Clean`、`SwapPss`：观察私有驻留页和换出页；
- `Runtime.totalMemory/freeMemory/maxMemory`：只反映 Java Heap，不代表进程总内存。

### 4.2 必须分阶段采样

每阶段至少重复 3 次冷启动，并记录时间点：

1. **S0：force-stop 后启动进程**，在 Splash/MainActivity 首帧后采样，测 Application 基线。
2. **S1：进入 EditorActivity，但不打开项目/不恢复文件**，采样编辑器布局与语言服务注册的增量。
3. **S2：打开项目，尚未恢复任何文件**，采样项目初始化、Kotlin LSP、Tooling Server 连接阶段；分别记录 PID。
4. **S3：恢复文件完成后**，记录恢复文件数量、每个文件大小、总文件字节数及 IDE 主 PID PSS。
5. **S4：空闲 30 秒后**，观察 GC 后是否回落，区分瞬时峰值与常驻内存。

如果当前启动流程无法在 S1/S2 阶段暂停文件恢复，应在测试分支增加只用于诊断的开关/埋点，不要通过删除业务初始化或降低项目功能来伪造对照结果。

### 4.3 最有价值的 A/B 实验

- A：Release APK，清空最近打开文件缓存，冷启动。
- B：Release APK，恢复相同项目但不恢复文件标签。
- C：Release APK，恢复 1 个小文件。
- D：Release APK，恢复原有全部文件标签。
- E：与 A-D 相同条件再用 Debug APK 重复，评估 LeakCanary/调试配置开销。

另外可在诊断分支中分别延迟 QuickJS、Tree-sitter、Kotlin LSP 初始化，单项对照其增量。每次只改变一个变量，否则结果无法归因。

## 5. 建议的优化顺序

以下是根据源码风险提出的建议，**本次没有擅自修改业务实现**：

1. **先治理文件恢复策略**：恢复时只立即创建当前选中的编辑器；其余文件先保留轻量 Tab/文件元数据，用户切换到对应 Tab 时再创建 `CodeEditorView` 和加载文本。对恢复队列设置并发上限，并按文件大小做分批/延迟加载。不要丢弃用户的打开文件记录。
2. **把 QuickJS 改为按功能延迟加载**：只有用户首次进入确实需要 QuickJS 的 ChatAI/脚本能力时才初始化 Native runtime；同时确保并发初始化幂等，并保留功能可用性。
3. **审计 Koin 模块 eager 单例**：检查 `appModule`、`viewModelModule`、`dataSourceModule`、`repositoryModule` 中的 `createdAtStart`、主动解析和大型缓存。仅装配模块不应被误认为全部对象都已实例化。
4. **语言服务按文件类型/项目能力启动**：避免没有 Kotlin 源文件时也提前启动完整 Kotlin 工作区服务；Java/XML/Clangd 等也应核对注册与进程启动的边界。
5. **Tree-sitter 按需加载**：尽可能在首个需要 Tree-sitter 的源文件解析路径加载，而不是无条件在 EditorActivity 创建后加载；保留线程安全和首帧体验。
6. **颜色方案初始化去重**：Application 已经调用 `IDEColorSchemeProvider.init()`，EditorHandlerActivity 又调用 `initIfNeeded()`。检查并保证并发/重复调用不会重复扫描、解析或保留冗余对象。
7. **Debug/Release 分开设基线**：不要用 Debug APK 的进程内存数字直接代表用户安装的 Release APK。

## 6. 下一步需要的运行时证据

本次源码检查已经确定最值得追踪的调用链，但不能给出 500 MB 的精确构成。要把候选原因升级为根因，需要以下任一组数据：

- S0-S4 各阶段的 `dumpsys meminfo` 完整输出及对应 PID；
- 同一启动过程的 Perfetto trace（包含 dalvik/ART、native heap、进程/线程调度、文件 I/O）；
- 若 Native Heap 明显偏高，再采集 heapprofd 分配栈；
- 最近打开文件缓存内容（文件数量、大小；可脱敏路径）。

拿到数据后，应按主 PID 的 Java Heap、Native Heap、Graphics、Other、Private Dirty 和子进程分别归因，再决定修改顺序。**当前最先验证的是文件恢复阶段的内存增量，其次是 Application 启动阶段 QuickJS/Koin 的增量。**
