# Compose Preview 重构 TODO 清单

> 与 `DESIGN.md` 配套。**所有任务优先级 P0 为本轮必须完成**；P1 / P2 / P3 在后续 PR 中完成。
> 任务编号规范：`P?-XX-YY` —— `?` 优先级、`XX` 模块代号、`YY` 序号。
>
> 模块代号：
> - `BLD` Build Phase
> - `RT`  Runtime
> - `UI`  UI / Interaction
> - `FE`  Feature
> - `TS`  Test
> - `DOC` 文档

---

## P0 — 本轮必修（PR #1）

### BLD 资产化

- [ ] `P0-BLD-01` 改造 `build.gradle.kts`：
  - 引入 `kotlinCompilerJars` Configuration（`org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.24`）
  - 引入 `bundledD8Jars` Configuration（从 `android-sdk/build-tools/<latest>/lib/d8.jar` 拷贝并打包）
  - 新增 `packageComposeSdk` 任务，产出 `assets/compose/compose-sdk.zip`
- [ ] `P0-BLD-02` 新增 `compiler/AssetsComposeBundles.kt`：
  - `init(context)`：解压 zip 到 `cacheDir/compose-sdk/`
  - 校验 SHA-256（防止资产被改坏）
  - 暴露 `kotlinCompilerClasspath` / `composePluginJar` / `runtimeJars` / `d8Jar` / `androidJar`（若 SDK 缺失则降级）
- [ ] `P0-BLD-03` 改写 `compiler/ComposeCompiler.kt` → `BundledComposeCompiler`：
  - 移除所有 `Environment.*` 路径依赖
  - 进程内调用 `K2JVMCompiler.exec(args, messageCollector)`
  - 入参：`sourceFiles`, `outputDir`, `runtimeJars`, `pluginJar`, `androidJar`
  - 出参：`success` / `errorOutput` / `diagnostics`
- [ ] `P0-BLD-04` 改写 `compiler/ComposeDexCompiler.kt` → `BundledD8Dexer`：
  - 进程内 `javaexec` 调用 `d8.jar`（来自 assets 缓存）
  - 移除 SDK build-tools 强依赖；找不到 d8 时抛 `BundledD8MissingException`，由 Repository 降级到 `useGradleDex` 流程
- [ ] `P0-BLD-05` 删除 `compiler/CompilerDaemon.kt`：
  - 不再维护 Kotlin 编译守护
  - 移出 `ComposePreviewRepositoryImpl` 中所有 `compilerDaemon` 引用
- [ ] `P0-BLD-06` 删除 `compiler/ComposeClasspathManager.kt`：
  - 由 `AssetsComposeBundles` 替代
- [ ] `P0-BLD-07` `compiler/DexCache.kt` 增加 `versionTag`：
  - `versionTag = "composeSdk-<hash>"`
  - SDK 升级时旧缓存自动失效
- [ ] `P0-BLD-08` 改写 `data/repository/ComposePreviewRepositoryImpl.kt`：
  - 移除 `classpathManager` / `compiler` / `compilerDaemon` 字段
  - 改为 `bundles: AssetsComposeBundles` + `compiler: BundledComposeCompiler` + `dexer: BundledD8Dexer`
  - 错误处理：d8 缺失 → 抛 `InitializationResult.Failed`（带明确提示），不无限重试

### RT 运行时优化

- [ ] `P0-RT-01` 新增 `runtime/MethodHandleResolver.kt`：
  - 缓存 `(Class<*>, String) -> MethodHandle`
  - 第一次反射查找 → `MethodHandles.Lookup.unreflect` → 转 `MethodHandle`
- [ ] `P0-RT-02` 改写 `runtime/ComposableRenderer.kt`：
  - 用 `MethodHandle.invokeWithArguments` 替代 `Method.invoke`
  - 静态方法直接 `invokeWithArguments()`；非静态 `invokeWithArguments(instance)`
  - 错误渲染走统一 `ErrorContent` Composable
- [ ] `P0-RT-03` 改写 `runtime/ComposeClassLoader.kt`：
  - 池化：`(dexPath, hash) -> DexClassLoader` 弱引用 map
  - `release()` 仅释放 `optimizedDir`，不删 loader（除非路径变更）
  - 日志统一 `LOG.info`，避免无限循环释放

### UI 集成

- [ ] `P0-UI-01` 增强 `ComposePreviewViewModel`：
  - 增加 `deviceProfile: StateFlow<DeviceProfile>` / `zoom: StateFlow<Float>` / `theme: StateFlow<PreviewTheme>`
  - 增加 `selectedPreview: StateFlow<String?>`（多 Preview 网格时使用）
  - 增加 `useGradleDex: StateFlow<Boolean>` 兜底开关
- [ ] `P0-UI-02` 增强 `ComposePreviewFragment`：
  - 顶栏新增：设备切换、主题切换、缩放控制
  - 加载态 / 错误态 / 兜底态 UI
- [ ] `P0-UI-03` 增强 `ui/BoundedComposeView.kt`：
  - 支持 `onZoomChanged` / `onPanChanged` 回调
  - 与 `ZoomController` 联动

### DOC

- [ ] `P0-DOC-01` 编写 `DESIGN.md`（已完成）
- [ ] `P0-DOC-02` 编写 `TODO.md`（本文件）
- [ ] `P0-DOC-03` PR body 引用本文件

### TS 测试

- [ ] `P0-TS-01` 单元测试：`AssetsComposeBundles` 解压 / 校验
- [ ] `P0-TS-02` 单元测试：`MethodHandleResolver` 缓存命中
- [ ] `P0-TS-03` 端到端：沙箱构建机冷启动 → 编译 → 渲染（手动）

---

## P1 — 下一 PR（功能完整化）

### UI 设备模拟

- [ ] `P1-UI-01` `ui/DeviceFrame.kt`：
  - 圆角、刘海、状态栏样式
  - 内置 Pixel 4 / 5 / 6 / 7、Tablet、Foldable
- [ ] `P1-UI-02` `ui/DeviceProfileSheet.kt`：
  - 底部 Sheet 选择设备
  - 写入 SharedPreferences
- [ ] `P1-UI-03` `ui/ResolutionEditor.kt`：
  - 弹窗编辑 width × height × DPI
  - 实时预览

### UI 缩放

- [ ] `P1-UI-04` `ui/ZoomController.kt`：
  - pinch 手势识别
  - Ctrl + Wheel 缩放
  - 双击切换 Fit / 100%
  - 缩放范围 0.1x ~ 4x

### UI 主题

- [ ] `P1-UI-05` `ui/ThemeSelector.kt`：
  - Light / Dark / Custom 三选
  - Custom 时显示颜色编辑器
  - 反射注入 MaterialTheme / Custom ColorScheme

### UI 多 Preview 网格

- [ ] `P1-UI-06` `ui/PreviewGridLayout.kt`：
  - AS 风格卡片网格
  - 单击切换 SINGLE 模式
- [ ] `P1-DOM-01` `domain/PreviewSourceParser.kt`：
  - 支持 `@PreviewParameter`
  - 支持 `@PreviewFontScale`
  - 支持 `@PreviewLightDark` 同时生成两张

### FE 反射优化

- [ ] `P1-FE-01` `runtime/PrewarmService.kt`：
  - 启动时预热 Compose runtime 关键 class
  - 触发 `Class.forName` 提前验证

---

## P2 — 第三轮 PR（调试工具）

### UI 调试

- [ ] `P2-UI-01` `ui/RecompositionCounter.kt`：
  - 通过 `Modifier.composed` + `currentRecomposeScope` 计数
  - 角标显示在每个 Composable 框上
- [ ] `P2-UI-02` `ui/ComponentInspector.kt`：
  - 反射读 `LayoutNode` 私有字段（`width`, `height`, `x`, `y`）
  - 绘制边界框
  - 单击节点高亮
- [ ] `P2-UI-03` `ui/LogcatPanel.kt`：
  - 拦截用户 `println` / `Log.d`
  - 折叠 / 展开
- [ ] `P2-UI-04` `ui/LayoutBoundsOverlay.kt`：
  - 复用 AS 的 bounds 高亮
  - 可开关

### FE 高级特性

- [ ] `P2-FE-01` 颜色拾取：
  - 截图后采样像素
  - 显示 RGB / HEX
- [ ] `P2-FE-02` LiveLiterals（实验）：
  - 反射修改 `LiveLiterals$*`
  - 需 K2 编译器支持
- [ ] `P2-FE-03` 拖动 / Resize：
  - `Modifier.pointerInput` + `detectDragGestures`
  - 改写 width/height Modifier

---

## P3 — 第四轮 PR（性能与可观测性）

- [ ] `P3-FE-01` Dex mmap + 共享：`assets/compose/dex/compose-runtime.dex` 在多个 Preview 间共享
- [ ] `P3-FE-02` 编译产物复用：同一源码不重复 dex
- [ ] `P3-FE-03` 性能埋点：编译 / 渲染各阶段耗时日志
- [ ] `P3-FE-04` 错误聚合：把多次编译错误的栈合并去重
- [ ] `P3-FE-05` 远程调试：预留端口供 `adb forward` 直连 Preview
- [ ] `P3-FE-06` Live Edit（Hot Reload）：仅在 incremental 模式

---

## 验收清单

每完成一个 PR，需勾选：

- [ ] 模块编译通过 (`./gradlew :modules:compose-preview:assembleDebug`)
- [ ] Lint 通过 (`./gradlew :modules:compose-preview:lintDebug`)
- [ ] 单元测试通过 (`./gradlew :modules:compose-preview:testDebugUnitTest`)
- [ ] 沙箱无 `.m2` 环境下冷启动可渲染
- [ ] PR 描述引用本 TODO 章节
- [ ] `DESIGN.md` 同步更新（如有架构变动）

---

最后更新：2026-06-13
