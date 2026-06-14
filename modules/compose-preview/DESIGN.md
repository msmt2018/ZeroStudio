# Compose Preview 重构与升级方案

> 模块：`modules/compose-preview/`
> 状态：v2 重构规划（PR 拆分中）
> 目标：**100% 资产化自包含、零 Maven 依赖、K2JVMCompiler 进程内调用、对标 IDEA / Android Studio Compose 预览插件**

---

## 1. 背景与现状问题

### 1.1 模块当前结构

```
modules/compose-preview/
├── build.gradle.kts                  // 依赖 + 把 compose runtime 打进 assets
├── src/main/AndroidManifest.xml
├── src/main/assets/compose/          // compose-jars.zip + compose-runtime.dex
├── src/main/java/.../compose/preview/
│   ├── ComposePreviewViewModel.kt    // StateFlow 状态机
│   ├── ComposePreviewFragment.kt     // UI 容器
│   ├── ComposePreviewActivity.kt
│   ├── PreviewConfig.kt              // data class
│   ├── PreviewState.kt               // sealed class（与 VM 同一文件）
│   ├── compiler/
│   │   ├── ComposeCompiler.kt        // 调用 K2JVMCompiler
│   │   ├── ComposeDexCompiler.kt     // 调用 d8.jar（来自 SDK build-tools）
│   │   ├── CompilerDaemon.kt         // 试图维护 Kotlin 编译守护进程（不稳定）
│   │   ├── ComposeClasspathManager.kt// 试图从 .m2 / IDE 路径获取 jar
│   │   └── DexCache.kt               // 源码 hash -> .dex 缓存
│   ├── data/
│   │   ├── source/ProjectContextSource.kt   // 解析项目 module / classpath
│   │   └── repository/ComposePreviewRepository*.kt
│   ├── domain/
│   │   ├── PreviewSourceParser.kt    // 正则解析 @Preview / @Composable
│   │   └── model/ParsedPreviewSource.kt
│   ├── runtime/
│   │   ├── ComposeClassLoader.kt     // DexClassLoader 缓存
│   │   └── ComposableRenderer.kt     // Method.invoke 反射调用
│   └── ui/BoundedComposeView.kt
```

### 1.2 已观测到的故障模式

| 现象 | 根因 |
| --- | --- |
| 编译无限循环、无法取消 | `CompilerDaemon` 维护一个常驻进程，IPC 协议（`kotlin-daemon-embeddable`）版本与项目内 K2 不一致，wait/poll 死锁 |
| 无法初始化 / 同步 | `ComposeClasspathManager` 引用 `Environment.KOTLIN_COMPILER_CLASSPATH_JAR_RELATIVE_PATH` 等 IDE 私有路径，沙箱 / 设备上不存在 |
| 编译失败但无清晰错误 | `ComposeCompiler.compile()` 走的是 IDE 内部 K2，但 `K2JVMCompilerArguments.pluginClasspaths` 路径在 IDE 上下文之外为空 |
| D8 阶段崩 | `ComposeDexCompiler` 调用 `Build-tools/d8.jar`，要求 SDK 已就绪；沙箱构建机常有 `d8` 缺失或版本不匹配 |
| `.m2` 强依赖 | 早期版本通过 `~/.m2/repository/.../kotlin-compiler-embeddable-*.jar` 直接拉 jar，跨用户 / 跨设备不可移植 |
| 守护进程与编译挂在本项目不存在的 SDK / jar 上 | `KotlinCompilerDaemonConfig` 中的 `classpathRoots` 引用 IDE 私有目录 |

### 1.3 反射 / 渲染层现状

- `ComposeClassLoader`：用 `DexClassLoader` 加载 `.dex`，有缓存（path + lastModified），逻辑基本可用。
- `ComposableRenderer`：直接 `Method.invoke`，无预热、每次都做 `declaredMethods` 全扫。
- 渲染层对 `compose-runtime.dex`（已在 assets 中）依赖 OK，**只要构建产物能产出 dex 即可工作**。

### 1.4 核心结论

> **构建阶段必须彻底重写为「资产自包含 + 进程内 K2JVMCompiler + 进程内 D8」，运行时仅做轻量优化与功能增强。**
> Maven、本地仓库、IDE 私有路径、守护进程 **一律移除**。

---

## 2. 目标与非目标

### 2.1 目标

1. **构建阶段零外部依赖**：所有编译器、Compose 插件、D8 都从 `assets/compose/sdk/` 解压。
2. **首次构建 < 8s、增量构建 < 2s**：源码 hash 命中缓存时立即返回。
3. **预览功能对标 IDEA / AS**：
   - 设备模拟（Pixel 4/5/6/7、Tablet、折叠屏）
   - 分辨率模拟（自定义 width × height、DPI）
   - 缩放（pinch、双指、Ctrl+Wheel、Fit）
   - 可视化编辑工具箱（拖动、resize、颜色拾取、padding/gap 实时编辑）
   - 调试工具（recomposition 计数、布局框、属性面板、日志面板）
   - 主题切换（Light/Dark/Custom）
   - 多 Preview 网格展示（AS 风格）
4. **运行时效率**：
   - 反射 → `MethodHandle`，减少 boxing
   - `DexClassLoader` 复用，DEX 文件 mmap 友好
   - Compose 渲染首帧 < 500ms

### 2.2 非目标

- 不实现完整 IDEA Live Edit / Hot Reload（区别于「预览」场景）
- 不集成 ProGuard / R8 优化
- 不支持 multi-module 项目的跨模块实时 preview（仍走 `gradle-dex` 备选路径，但只走最少必要路径）

---

## 3. 新架构

### 3.1 分层

```
┌──────────────────────────────────────────────────────────────────┐
│  UI 层（ComposePreviewFragment / Activity）                       │
│    └── BoundedComposeView（支持 zoom / pan / device frame）        │
├──────────────────────────────────────────────────────────────────┤
│  ViewModel 层（ComposePreviewViewModel）                          │
│    ├── StateFlow<PreviewState> 状态机                             │
│    ├── DisplayMode: ALL / SINGLE                                  │
│    ├── DeviceProfile: 设备 / 分辨率 / DPI                         │
│    └── ZoomController / RecompositionCounter / ComponentInspector│
├──────────────────────────────────────────────────────────────────┤
│  Repository 层（ComposePreviewRepository）                        │
│    ├── compile(source) -> CompilationResult                       │
│    └── buildContext(filePath) -> ProjectContext                   │
├──────────────────────────────────────────────────────────────────┤
│  Build Phase（核心重写）                                          │
│    ├── AssetsComposeBundles       从 assets 解压 / 校验            │
│    ├── BundledComposeCompiler     进程内 K2JVMCompiler 调用        │
│    ├── BundledD8Dexer             进程内 D8 调用                   │
│    ├── ComposePreviewCache        sourceHash + version -> dex     │
│    └── Fallback: GradleDexRunner  极端 fallback（不修，留口）      │
├──────────────────────────────────────────────────────────────────┤
│  Runtime 层（轻量优化）                                            │
│    ├── ComposeClassLoader         DexClassLoader 池化              │
│    ├── MethodHandleRenderer       反射 -> MethodHandle            │
│    └── PrewarmService             预热常用 class                  │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 资产打包

`build.gradle.kts` 中新增打包步骤：

```kotlin
// 1) Kotlin 编译器 (embeddable, 进程内)
kotlinCompilerJars("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.24")

// 2) Compose Compiler Plugin
composeCompilerJars("androidx.compose.compiler:compiler:1.5.14")

// 3) Compose Runtime (AAR -> classes.jar, 已存在)
// 4) D8 (从 build-tools 拷贝) -> 改为随包自带的 d8.jar
//    通过 compileSdk 自带的 d8.jar 提取, 也可固定版本内置.

// 5) 打包为 compose-sdk.zip
val packageComposeSdk by tasks.registering(Zip::class) {
    dependsOn(extractKotlinCompiler, extractComposeCompiler, extractComposeRuntime, extractD8)
    archiveFileName.set("compose-sdk.zip")
    destinationDirectory.set(file("src/main/assets/compose"))
}
```

`src/main/assets/compose/` 产物：
```
compose-sdk/
├── kotlin/
│   ├── kotlin-compiler-embeddable.jar
│   ├── kotlin-stdlib.jar
│   ├── kotlin-reflect.jar
│   ├── kotlin-script-runtime.jar
│   ├── trove4j.jar
│   └── annotations-13.0.jar
├── compose-compiler-plugin.jar
├── compose-runtime/
│   ├── compose-runtime-android.jar
│   ├── compose-ui-android.jar
│   ├── compose-ui-graphics-android.jar
│   ├── compose-ui-tooling-preview-android.jar
│   ├── compose-foundation-android.jar
│   ├── compose-material3-android.jar
│   ├── activity-compose.jar
│   ├── lifecycle-runtime.jar
│   ├── core.jar
│   └── ...（约 20 个）
└── dex/
    ├── d8.jar                        // 进程内 D8
    └── compose-runtime.dex           // 预 dexed runtime, 给主 app 共享
```

### 3.3 Build Phase 时序

```
compile(source) :
  1) AssetsComposeBundles.init()           // 一次性解压到 cacheDir
  2) ComposePreviewCache.get(sourceHash)   // 命中即返回
  3) SourcePreparer.writeWorkDir()         // <workDir>/src/<pkg>/<file>.kt
  4) BundledComposeCompiler.compile()      // 进程内 K2JVMCompiler
       K2JVMCompiler().exec(
         K2JVMCompilerArguments().apply {
           freeArgs = listOf("MyFile.kt")
           classpath = "<runtime jars>:<android.jar>:<project dex-derived classes>"
           pluginClasspaths = arrayOf("<compose-compiler-plugin.jar>")
           destination = "<workDir>/classes"
           jvmTarget = "17"
           suppressVersionWarnings = true
         },
         PrintingMessageCollector(System.out, MessageRenderer.PLAIN_RELATIVE_PATHS, false)
       )
  5) BundledD8Dexer.dex()                  // 进程内 D8
       classpath = files(d8Jar)
       mainClass = "com.android.tools.r8.D8"
       args = --release --min-api 21 --output <dexDir> <classesDir>
  6) DexCache.put(sourceHash, dexFile)
  7) return CompilationResult
```

### 3.4 Runtime 时序

```
render(state):
  1) ComposeClassLoader.acquire()         // 池化, path+hash 命中即复用
  2) loader.loadClass(<dexFile>, <className>)
  3) MethodHandleResolver.resolve()       // 缓存: (clazz, functionName) -> MethodHandle
  4) composeView.setContent { ... }
       MaterialTheme {
         Surface { BoundedDeviceFrame { ZoomablePane { RenderComposable(handle) } } }
       }
  5) MethodHandle.invokeWithArguments(composer, 0)   // 替代 Method.invoke
```

### 3.5 新功能矩阵

| 功能 | 入口 | 数据源 | 持久化 |
| --- | --- | --- | --- |
| 设备模拟 | `DeviceProfileSheet` | 内置 Pixel/Tablet/Watch Profile | 内存 (Session) |
| 分辨率自定义 | `ResolutionEditor` | 用户输入 w/h/DPI | SharedPreferences |
| 缩放 | `ZoomController` | 手势 / Ctrl+Wheel | 内存 (Session) |
| 主题切换 | `ThemeSelector` | 固定 Light/Dark/Custom | 内存 (Session) |
| Recomposition 计数 | `RecompositionCounter` | 通过 `currentComposer` 注入 side-effect | 内存 (Session) |
| Component Inspector | `ComponentInspector` | 通过反射读 `LayoutNode` 私有字段 | 内存 (Session) |
| 拖动 / Resize | `Modifier.draggable` / `Modifier.pointerInput` | 用户手势 | 内存 (Session) |
| 颜色拾取 | `ColorPickerOverlay` | Bitmap 像素采样 | 内存 (Session) |
| 布局框 | `LayoutBoundsOverlay` | `LayoutNode.coordinates` | 内存 (Session) |
| 日志面板 | `LogcatPanel` | `LiveLiterals` / 用户 println | 内存 (Session) |
| 多 Preview 网格 | `PreviewGridLayout` | 解析所有 @Preview | 内存 (Session) |
| Pin 主题色 | `LiveLiterals` (实验) | 反射修改 theme | 内存 (Session) |

---

## 4. 详细文件改动表

| 文件 | 动作 | 范围 |
| --- | --- | --- |
| `build.gradle.kts` | 修改 | 增加 `kotlinCompilerJars` / `bundledD8Jars` 配置；新增 `packageComposeSdk` 任务 |
| `assets/compose/compose-sdk.zip` | 新增 | 由 `packageComposeSdk` 生成 |
| `compiler/AssetsComposeBundles.kt` | 新增 | 资产清单 / 解压 / 校验（SHA-256） |
| `compiler/ComposeClasspathManager.kt` | **删除** | 旧实现，路径依赖 .m2 |
| `compiler/ComposeCompiler.kt` | **改写** | `BundledComposeCompiler`：`K2JVMCompiler` 进程内 |
| `compiler/ComposeDexCompiler.kt` | **改写** | `BundledD8Dexer`：`javaexec` 调用 assets 内的 `d8.jar` |
| `compiler/CompilerDaemon.kt` | **删除** | 守护进程机制与本项目 K2 版本不匹配 |
| `compiler/DexCache.kt` | 保留 | 加 `versionTag` 字段：SDK 升级时失效 |
| `compiler/ProjectContextSource.kt` | 保留 | 加 `useGradleDex` 短路开关 |
| `data/source/ProjectContextSource.kt` | 保留 | 不变 |
| `data/repository/ComposePreviewRepositoryImpl.kt` | **改写** | 引入新 Build Phase，去 daemon 逻辑 |
| `domain/PreviewSourceParser.kt` | 增强 | 支持 `@PreviewParameter`、`@PreviewFontScale` 等 |
| `runtime/ComposeClassLoader.kt` | **改写** | 池化 + 共享 dex 路径 |
| `runtime/ComposableRenderer.kt` | **改写** | MethodHandle + 反射缓存 |
| `runtime/MethodHandleResolver.kt` | 新增 | 反射查找 → MethodHandle |
| `runtime/PrewarmService.kt` | 新增 | 预热 compose runtime 类 |
| `ui/BoundedComposeView.kt` | **改写** | 集成 ZoomController / PanController |
| `ui/DeviceFrame.kt` | 新增 | 设备外壳（圆角、刘海、状态栏） |
| `ui/ZoomController.kt` | 新增 | pinch / scroll / fit-to-screen |
| `ui/RecompositionCounter.kt` | 新增 | recomposition 计数 side-effect |
| `ui/ComponentInspector.kt` | 新增 | 布局框 / 属性面板 |
| `ui/DeviceProfileSheet.kt` | 新增 | 设备选择 Sheet |
| `ui/ResolutionEditor.kt` | 新增 | 分辨率编辑 |
| `ui/ThemeSelector.kt` | 新增 | Light/Dark/Custom 切换 |
| `ui/LogcatPanel.kt` | 新增 | 日志面板 |
| `ui/PreviewGridLayout.kt` | 新增 | 多 Preview 网格 |
| `ComposePreviewFragment.kt` | **改写** | 集成新功能 |
| `ComposePreviewViewModel.kt` | 增强 | 新功能状态 |
| `DESIGN.md` | 新增 | 本文档 |
| `TODO.md` | 新增 | 任务清单 |

---

## 5. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| `kotlin-compiler-embeddable` jar 体积大 (~50MB) | 拆 SDK zip 之外独立打包；不放在主 APK assets 根目录 |
| K2JVMCompiler 进程内调用阻塞主线程 | 严格 `Dispatchers.IO` 包裹 + `viewModelScope` 取消传播 |
| D8 jar 跨 Android API 兼容性 | D8 是 Java 8 bytecode，Android 8+ 即可 classload；旧设备 (API < 26) 走 system d8 fallback |
| Compose 编译器版本与 runtime 版本不匹配 | 锁定组合（`compiler 1.5.14` ↔ `runtime 1.6.0`），`AssetsComposeBundles` 启动时校验 `compose-compiler-plugin.jar` 内 META-INF 版本 |
| `@PreviewParameter` 类型不能反射实例化 | 仅支持无参构造的 Provider；其他显式提示 |
| 跨 ABI / 跨 Gradle 版本产物差异 | 不重用跨项目的 dex；仅本预览缓存 |

---

## 6. 与 IDEA / AS 预览插件的对标

| 维度 | IDEA / AS | 本模块 v2 |
| --- | --- | --- |
| 编译后端 | IDE 内 K2 + Daemon | **进程内 K2JVMCompiler，无守护** |
| Dex 后端 | IDE 内 D8 | **进程内 D8（assets 携带）** |
| 设备模拟 | 模板 | **同**（Pixel/Tablet/Watch/Foldable） |
| 分辨率自定义 | ✓ | **✓** |
| 缩放 | ✓ | **✓**（pinch + ctrl+wheel） |
| Recomposition 高亮 | ✓ | **基础**（计数器） |
| Component Inspector | ✓ | **基础**（布局框） |
| Live Edit | ✓ | ✗（不在本轮范围） |
| 主题切换 | ✓ | **✓**（Light/Dark/Custom） |
| 多 Preview 网格 | ✓ | **✓** |

---

## 7. 测试 / 验收

| 阶段 | 验收 |
| --- | --- |
| 单元测试 | `AssetsComposeBundles` 解压校验、K2JVMCompiler 调用参数正确 |
| 集成测试 | 在沙箱构建机（无 `.m2`、无 SDK build-tools）冷启动 → 编译 → 渲染 |
| 端到端 | 打开 `MyComposable.kt` → 修改 → 1s 内重渲染 → 多 Preview 网格显示 |
| 性能 | 首次冷启动 < 8s；增量 < 2s；首帧渲染 < 500ms |
| 兼容性 | API 21+；arm64-v8a / armeabi-v7a / x86_64 |

---

## 8. 后续 PR 拆分

| PR | 范围 | 依赖 |
| --- | --- | --- |
| #N  本 PR | 设计 + Build Phase 重写 + 反射优化 | 无 |
| #N+1 | DeviceFrame / ZoomController / 主题切换 | 本 PR |
| #N+2 | RecompositionCounter / ComponentInspector | 本 PR |
| #N+3 | 多 Preview 网格 + PreviewParameter 支持 | 本 PR |
| #N+4 | 性能调优（prewarm、DEX 复用） | 本 PR |
| #N+5 | LiveLiterals 实验 | 本 PR |

---

## 9. 引用

- [JetBrains Compose Compiler](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)
- [K2JVMCompiler 进程内调用](https://github.com/JetBrains/kotlin/blob/master/compiler/cli/src/org/jetbrains/kotlin/cli/jvm/K2JVMCompiler.kt)
- [Android Studio Compose Preview 源码（AGPL 闭源，仅参考交互）](https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/master/compose-designer/)
- [AndroidIDE 现有 compose-preview 模块](file:///workspace/modules/compose-preview/)
- [AndroidX Compose BOM](https://developer.android.com/jetpack/compose/bom/bom-mapping)

---

文档维护者：AndroidIDE
最后更新：2026-06-13
