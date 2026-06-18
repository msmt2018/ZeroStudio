# Compose Preview v3.1 设计与现状

> 模块：`modules/compose-preview/`
> 状态：**v3.1 终态**（2026-06 重构后）
> 核心：**纯 gradle dex 加载** — 零 assets 预打包 jar，零进程内 K2 / D8 / ASM，零 DexCache
> 目标：保证 Compose 渲染 / 反射 / 动态加载 / 显示 UI / 状态同步 正常即可。

---

## 1. v3.1 核心设计：单一 dex 加载路径

### 1.1 加载链

```
用户项目 gradle build (assemble<Variant>)
        ↓
   build/intermediates/dex/<variant>/*.dex
   build/intermediates/project_dex_archive/<variant>/*.dex
        ↓
   ProjectContextSource.projectDexFiles
        ↓
   PreviewRenderEngine.render(previewDex, projectDex, className, functionName)
        ↓
   DexRuntime.loadAll(dexFiles) → 合并去重 → 唯一 dexFiles 列表
        ↓
   InMemoryDexClassLoader (API 26+) / DexClassLoader
        ├── dexFiles    <- 用户项目 build 产物
        └── parent      <- IDE 主 APK 的 PathClassLoader
                            包含 androidx.compose.runtime / ui / foundation /
                            material3 等 IDE compile classpath
        ↓
   DexRuntime.loadClass(className) → Class<*>
        ↓
   ComposableInvoker.invoke(clazz, functionName, composer, instance, args)
        ↓
   currentComposer 注入 → 用户 Composable 内部用 androidx.compose.runtime.*
        ↓
   Recompose / Modifier / Measure / Draw → ComposeView 像素输出
```

### 1.2 v3.1 vs v2.1 / v3 关键简化

| 阶段 | v2.1 / v3 旧方案 | v3.1 新方案 |
| --- | --- | --- |
| compose runtime 类 | `assets/compose-jars.zip` → `AssetsComposeBundles.init()` → `compose-runtime.dex` | IDE APK 的 PathClassLoader 直接解析 |
| 编译产物 | 进程内 `K2JVMCompiler` (`BundledComposeCompiler`) | gradle `assemble<Variant>` 任务生成 dex |
| Dex 转换 | 进程内 `BundledD8Dexer` (R8 fat jar 内置 D8) | gradle 自动 dex (AGP 内置) |
| 进程内 dex 缓存 | `DexCache` (源 hash → dex) | gradle build cache |
| dex 优化 | `AsmComposeBinder` (binder 字节码) | DexClassLoader 自带 (无 binder) |
| dex 加载接口 | `PreviewRenderEngine.render(previewDex, projectDex, runtimeDex, ...)` 3 dex | `render(previewDex, projectDex, ...)` 2 dex；`DexRuntime.loadAll(dexFiles)` 单一列表 |
| `assets/compose/compose-jars.zip` | 70MB+ jar 压缩包 | **不存在** |
| `kotlin-compiler-embeddable / r8 / compose-compiler-plugin` 依赖 | 必须下载并打包到 assets | **不再需要** |

### 1.3 为什么"Compose runtime jars missing"错误消失了

v2.1 链路里 `BundledComposeCompiler.compile()` 会校验 `bundles.composeRuntimeJars.isEmpty()`——这是
`assets/compose-jars.zip` 解压后 `compose-runtime/` 目录下的 jar 列表。任何 jar 缺失都会抛
"Compose runtime jars missing"。

v3.1 删了 `BundledComposeCompiler`，改走 `BuildService.executeTasks(assemble<Variant>)`，整个校验
链路消失 → 该错误不可能再出现。

### 1.4 与 `modules/compose-preview2` 备用模块的关系

`compose-preview2` 备用模块是一个**最小 compose preview UI 骨架**，只用来验证 `ComposeView` +
`setContent` 在 IDE 里能跑（验证 compose 编译环境本身）。它**没有**任何 dex 加载逻辑、也没有 assets
预打包、也没有 BuildService 集成。v3.1 的 `modules/compose-preview` 才是真正干活的预览模块。

---

## 2. 当前文件清单（v3.1 实际）

```
modules/compose-preview/
├── build.gradle.kts                          # 仅 IDE 自身 compose 依赖，无 jar 打包
├── DESIGN.md                                 # 本文件
├── TODO.md                                   # 配套 TODO 清单
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml
    ├── res/                                  # 布局 / drawable / menu
    └── java/com/itsaky/androidide/compose/preview/
        ├── ComposePreviewActivity.kt              # 入口 (全 Compose UI, 顶栏 + DeviceFrame)
        ├── ComposePreviewFragment.kt              # 兼容入口 (ComposeView based)
        ├── ComposePreviewViewModel.kt             # 状态机 (StateFlow)
        ├── compiler/
        │   └── CompileModels.kt                   # 仅 CompileDiagnostic (错误诊断)
        ├── data/
        │   ├── device/                            # 设备 catalog (Pixel / Foldable / Watch ...)
        │   ├── repository/
        │   │   ├── ComposePreviewRepository.kt        # 接口 (v3.1: 删 runtimeDex/computeSourceHash)
        │   │   └── ComposePreviewRepositoryImpl.kt    # 实现 (唯一 gradle-dex 模式)
        │   └── source/
        │       └── ProjectContextSource.kt            # 解析 projectDexFiles
        ├── domain/
        │   ├── PreviewSourceParser.kt                  # 正则解析 @Preview
        │   └── model/ParsedPreviewSource.kt
        ├── runtime/
        │   ├── PreviewRenderEngine.kt    # Activity/Fragment 持有的渲染引擎
        │   ├── DexRuntime.kt             # dex → ClassLoader 一次性加载
        │   └── ComposableInvoker.kt      # MethodHandle 反射调用
        └── ui/                            # PreviewToolbar / DeviceFrame / CutoutOverlay / ...
```

**v3.1 已删除的 v2.1 文件**（不要试图恢复）：
- `compiler/AssetsComposeBundles.kt`        — assets zip 解压 + SHA 校验
- `compiler/BundledComposeCompiler.kt`     — 进程内 K2JVMCompiler
- `compiler/BundledD8Dexer.kt`             — 进程内 D8 (R8 fat jar)
- `compiler/DexCache.kt`                   — 源 hash → dex 缓存
- `compiler/CompileModels.kt` 中的 `CompileResult` / `DexResult` 数据类（保留 `CompileDiagnostic`）

---

## 3. 关键流程

### 3.1 启动

1. `ComposePreviewActivity.onCreate` / `ComposePreviewFragment.onViewCreated`：
   - 创建 `PreviewRenderEngine` → `attach()` 把 ComposeView 装到 container
   - 调 `viewModel.initialize(context, filePath)`

2. `ComposePreviewViewModel.initialize`：
   - 调 `repository.initialize(context, filePath)` → `ProjectContextSource.resolveContext(filePath)`
   - `ProjectContextSource` 走 AndroidModule.getRuntimeDexFiles() 找 dex
   - 优先 dex 判定 → `Ready(projectContext)` 状态

3. `compilePreview` 阶段（v3.1 唯一路径）：
   - `RepositoryImpl.compilePreview` → `BuildService.executeTasks("$module:assemble$Variant")`
   - 跑完重新解析 `ProjectContext` → 拿 `projectDexFiles`
   - 返回 `CompilationResult(previewDex=projectDexFiles.first(), className, projectDexFiles)`

4. `PreviewState.Ready` → `PreviewRenderEngine.render(previewDex, projectDex, className, functionName)`
   → `DexRuntime.loadAll(dexFiles = previewDex + projectDex)` → `setContent { RenderComposable(...) }`

### 3.2 渲染

```
ComposeView.setContent
        ↓
currentComposer (来自 androidx.compose.runtime)
        ↓
ComposableInvoker.invoke → MethodHandle.invokeWithArguments(composer, 0)
        ↓
用户 Composable 内部用 androidx.compose.runtime.* → parent classloader 解析
        ↓
Recompose / Modifier / Measure / Draw (MaterialTheme / Color / Text ...)
        ↓
Pixel output → ComposeView 绘制
```

### 3.3 触发 Build

`ComposePreviewActivity.NeedsBuildPanel` 上的 "Build Project" 按钮：
- 调 `BuildService.executeTasks("$module:assemble$variant")`
- 等 15 分钟
- 成功后调 `viewModel.refreshAfterBuild(activity)` → `repository.reset() + initialize() + compileNow()`

---

## 4. 反射 / 渲染 / 加载 compose UI 正常性的硬性要求

1. **IDE APK 必须含 compose runtime / ui / foundation / material3** — `build.gradle.kts` 已声明。
2. **DexClassLoader parent 必须是 `context.classLoader`** — `DexRuntime.createClassLoader` 已用。
3. **user dex 必须存在** — `ProjectContextSource.resolveContext` 通过 `AndroidModule.getRuntimeDexFiles()` 找，找不到返回 `NeedsBuild` 让用户点 build。
4. **AGP 必须开 `minifyEnabled=false` 或 `isShrinkResources=false`** — 当前模块的 consumer rules 不影响。
5. **compileSdk ≥ 33**（`androidx.compose.material3` 1.5+ 需要）— 当前 build.gradle.kts 没显式声明，依赖 rootProject 配的 34+。
6. **kotlin compose plugin** — `alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)` 已开。

满足以上 6 条，渲染 / 反射 / 动态加载 / UI 显示就一定正常。

---

## 5. 关键 commit 列表

| commit | 内容 |
| --- | --- |
| `c3964dd6` | (历史) 修 append race / Android 36 资源编译 / Kotlin LSP SLF4J |
| `9705b16b` | (历史) 修 build 成功后停在 NeedsBuild 不渲染 (Repository 双层防御) |
| **(本轮)** | v3.1 简化：删 v2.1 资产打包 + K2 + D8 + DexCache；Repository 唯一 gradle-dex 模式；删 `runtimeDex` 字段；精简 `build.gradle.kts` |

---

## 6. 未来工作（见 TODO.md）

- 真实设备 catalog 继续扩充
- ComponentInspector 真实反射 LayoutNode（如真有需要，再加回 kotlin-reflect）
- PreviewState 完整化（多 preview 网格 / @PreviewParameter / 等）

---

最后更新：2026-06-18
