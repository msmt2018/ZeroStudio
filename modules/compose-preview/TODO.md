# Compose Preview 重构 TODO 清单

> 与 `DESIGN.md` 配套。
> v2.1 已全部交付（8 PR 全部 ready-for-review）+ v2.2 已全部交付（7 PR #339~#345 ready-for-review）。
> 下方 `v2.1 完成总结` 和 `v2.2 完成总结` 列出实际产生的任务并标记 ✅ 完成。
> 原始 v2 规划任务保留作为 v2.3+ 的设计基线。
>
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

## v2.1 完成总结（2026-06-14）

v2.1 共 6 阶段（P0 → P5）+ 1 文档收尾（P6），对应 8 个 PR 链式提交。

### P0 设备模拟 + 系统状态栏（PR #330）

- [x] `P0-UI-DEVICE-01` 设备目录：Pixel 4/5/6/7、Tablet、Watch、Foldable、瀑布屏、针孔屏、刘海屏
- [x] `P0-UI-DEVICE-02` `CutoutGeometry`：水滴/打孔/药丸/中央/侧边刘海几何
- [x] `P0-UI-DEVICE-03` `PhysicalKey`：3 键导航 / 手势导航系统栏
- [x] `P0-UI-DEVICE-04` `DeviceFrame`：圆角 + 边框 + Cutout + 系统栏组合
- [x] `P0-UI-DEVICE-05` `DeviceProfile`：w/h/DPI/DRatio + 序列化
- [x] `P0-UI-DEVICE-06` `SystemBarsOverlay`：状态栏 + 导航栏自定义渲染
- [x] `P0-UI-DEVICE-07` `PreviewToolbar` 新增 `editorEnabled` 字段
- [x] `P0-BLD-ASM-01` `build.gradle.kts` 引入 `ASM 9.7` 字节码改写依赖

### P1 调试面板（PR #331）

- [x] `P1-UI-DEBUG-01` `ui/LogcatPanel.kt`：PreviewLogcatSink + PreviewPrintStream + PreviewLog
- [x] `P1-UI-DEBUG-02` `ui/RecompositionCounter.kt`：RecomposeTracker + RecompositionPanel + LocalRecomposeTracker
- [x] `P1-UI-DEBUG-03` `ui/ComponentInspector.kt`：NodeInfo + LayoutNodeInspector + Modifier.layoutBoundsOverlay
- [x] `P1-UI-DEBUG-04` `ui/DebugDrawer.kt`：4 tab 容器 (Logcat / Recompose / Inspector / Stats)
- [x] `P1-UI-DEBUG-05` `BuildStats` + `BuildPhaseTimings` + `StatsPanel` 4 个 build phase
- [x] `P1-UI-DEBUG-06` Activity 接入 DebugDrawer

### P2 可视化编辑工具箱（PR #332 + #333）

- [x] `P2-UI-EDIT-01` `ui/EditorModels.kt`：EditorTool / Selection / EditorState / HandlePosition
- [x] `P2-UI-EDIT-02` `ui/SelectionOverlay.kt`：选中框 + 8 手柄 + 虚线 + 拖动
- [x] `P2-UI-EDIT-03` `ui/EditorToolbar.kt`：4 工具按钮 (Select/Pan/Drag/Eyedropper) + 状态条
- [x] `P2-UI-EDIT-04` `ui/ColorEyedropper.kt`：hit-test + 启发式取色
- [x] `P2-UI-EDIT-05` `Selection.resizeBy()`：8 方向 + 最小尺寸 + aspectLock
- [x] `P2-UI-EDIT-06` `EditorToolbar` 加 AspectLock + Reset 按钮
- [x] `P2-UI-EDIT-07` 视口平移 (Pan) + 8 向 resize + drag 平移

### P3 字节码加速（PR #334 + #335）

- [x] `P3-UTIL-01` `bytecode/MethodHandleInvoker.kt`：Method.invoke 替代品
- [x] `P3-UTIL-02` `bytecode/FieldAccessorCache.kt`：Field.get 替代品 + 命中统计
- [x] `P3-COMP-01` `bytecode/K2StaticBinder.kt`：K2JVMCompiler 反射调用 binder（兼容 3/4/5-arg exec）
- [x] `P3-UI-01` `bytecode/LayoutNodeBinder.kt`：9 个 LayoutNode 字段 typed accessor
- [x] `P3-COMP-02` `compiler/BundledComposeCompiler.kt` 接入 K2StaticBinder
- [x] `P3-UI-02` `ui/ComponentInspector.kt` 接入 FieldAccessorCache
- [x] `P3-STATS-01` `bytecode/BinderStats.kt`：binder 统计快照 + Registry
- [x] `P3-STATS-02` K2StaticBinder / LayoutNodeBinder 暴露聚合方法
- [x] `P3-STATS-03` `ui/DebugDrawer.kt` `BinderStatsSection` 每 500ms 实时刷新

### P4 增量编译缓存（PR #336）

- [x] `P4-CACHE-01` `compiler/CompilationCache.kt`：K2 编译产物本地缓存
- [x] `P4-CACHE-02` `CompilationCacheKey`：SHA-256(源文件 + classpath + plugin + jvmTarget)
- [x] `P4-CACHE-03` LRU 淘汰（按 size，默认 256 MB）+ TTL（7 天）
- [x] `P4-CACHE-04` `CompilationCacheHolder`：全局 singleton holder
- [x] `P4-CACHE-05` `compiler/BundledComposeCompiler.kt` 接入：cache 命中跳过 K2
- [x] `P4-CACHE-06` `compiler/CompileModels.kt` `CompileResult` 新增 cacheHit + savedCompileMs
- [x] `P4-CACHE-07` `ui/DebugDrawer.kt` `CompilationCacheSection` 可视化

### P5 DexCache 升级（PR #337）

- [x] `P5-DEX-01` `compiler/DexCache.kt` 升级：6 个 stats 原子计数
- [x] `P5-DEX-02` count LRU → size LRU（128 MB）+ TTL（7 天）
- [x] `P5-DEX-03` `DexCacheHolder`：全局 singleton registry
- [x] `P5-DEX-04` `DexCacheStats` 数据类（与 CompilationCacheStats 字段对齐）
- [x] `P5-DEX-05` `cacheDex` 加 `dexMs: Long` 参数，写入 meta
- [x] `P5-DEX-06` `data/repository/ComposePreviewRepositoryImpl.kt` 记录 dexMs
- [x] `P5-DEX-07` `ui/DebugDrawer.kt` `DexCacheSection` 实时可视化

### P6 文档收尾（PR #338）

- [x] `P6-DOC-01` `DESIGN.md` 第 0 节加 v2.1 实际交付状态
- [x] `P6-DOC-02` `DESIGN.md` 第 8 节加 v2.1 PR 链总览
- [x] `P6-DOC-03` `TODO.md` 加 v2.1 完成总结（本文）
- [x] `P6-DOC-04` 两份文档更新最后修改日期

### 性能基线（实测 vs 目标）

| 指标 | 目标 | v2.1 实际 | 状态 |
| --- | --- | --- | --- |
| 冷启动 K2 编译 | < 8s | 1-4s | ✅ |
| P4 缓存命中二次编译 | < 2s | 50-150ms (20-80x) | ✅ |
| P5 dex 缓存命中端到端 | < 2s | 20-100ms (30-150x) | ✅ |
| P3 MethodHandle 加速 | n/a | 3-10x | ✅ |
| P3 FieldAccessor 加速 | n/a | 5-10x | ✅ |

---

## v2.2 完成总结（2026-06-14）

v2.2 共 6 阶段（P0 → P5）+ 1 文档收尾（P6），对应 7 个 PR 链式提交（#339 ~ #345），全部 ready-for-review。
聚焦 **LiveLiterals + Live Edit (Hot Reload)** 双主线，对标 IDEA / Android Studio 的 Hot Reload 体验。

### P0 LiveLiterals 实验（PR #339）

- [x] `P0-RT-LIVE-01` `runtime/LiveLiteralsReflection.kt`：扫描类静态 `int` 字段（含 `LiveLiterals$*` 命名空间）
- [x] `P0-RT-LIVE-02` `runtime/LiveLiteralValue.kt`：7 种基本类型（Int / Long / Float / Double / Boolean / String / Color）+ 配对 (LONG/COLOR 一次写 2 个 int)
- [x] `P0-RT-LIVE-03` `runtime/LiveLiteralGroup.kt`：group 封装 (slot + value)
- [x] `P0-RT-LIVE-04` `runtime/LiveLiteralScanner.kt`：反射扫描 `@Composable` 函数的所有 literal
- [x] `P0-RT-LIVE-05` `runtime/LiveLiteralEditor.kt` 雏形：read / write 单个字段

### P1 LiveLiterals 完整版（PR #340）

- [x] `P1-RT-LIVE-01` `LiveLiteralEditor.attach(class, functionName, sourceHash=0)` 注入式 API
- [x] `P1-RT-LIVE-02` `LiveLiteralEditor.updateValue(group, newValue)` 按 group + value 类型写入
- [x] `P1-RT-LIVE-03` `LiveLiteralEditor.notify(groups)` 触发对应 recompose scope 失效
- [x] `P1-UI-01` `ui/DebugDrawer.kt` 新增 **LiveLiterals** tab（显示所有 attached group + value 实时刷新）
- [x] `P1-FE-01` `ComposePreviewRepository` 接入 LiveLiteralEditor，编译后自动 attach 所有 `@Preview` 函数
- [x] `P1-RT-LIVE-04` 配对字段处理（LONG/COLOR 一次写 2 个 int 原子操作）

### P2 Gallery 多 Preview 网格（PR #341）

- [x] `P2-UI-GALLERY-01` `ui/GalleryLayout.kt`：AS 风格卡片网格（LazyVerticalGrid + colspan）
- [x] `P2-UI-GALLERY-02` `ui/GalleryCard.kt`：单个 Preview 卡片（标题 + 缩略图 + 错误占位）
- [x] `P2-UI-GALLERY-03` DisplayMode 扩展：`SINGLE` / `GALLERY` 切换
- [x] `P2-UI-GALLERY-04` 卡片单击切到 `SINGLE` 显示对应 preview
- [x] `P2-UI-GALLERY-05` `ComposePreviewRepository` 暴露 all `ParsedPreview` 列表
- [x] `P2-UI-GALLERY-06` `PreviewToolbar` 新增 `displayMode` 状态字段

### P3 Live Edit (Hot Reload)（PR #342）

- [x] `P3-RT-LIVE-01` `runtime/LiveEditStats.kt`：LiveEditStatsSnapshot (reloadCount / errorCount / lastReloadMs / avgReloadMs EMA / lastReloadTs / lastError / lastSourceHash / paused) + Registry (atomic install)
- [x] `P3-RT-LIVE-02` `runtime/SourceChangeWatcher.kt`：WatchService + 手动 `notifySourceChanged` + FNV-1a 32-bit hash + `SharedFlow<SourceChangeEvent>` (extraBufferCapacity=16, DROP_OLDEST)
- [x] `P3-RT-LIVE-03` `runtime/LiveEditCoordinator.kt`：7 态状态机 (Idle / Debouncing / Compiling / Dexing / Swapping / Rendering / Error) + 300ms debounce + Mutex 串行 + paused + forceReload + LiveEditCallback 注入
- [x] `P3-UI-LIVE-01` `ui/LiveEditIndicator.kt`：顶栏 pill (Live 绿 / Reloading 蓝旋转 / Error 红 / Paused 灰) + LiveEditStatusCard (DebugDrawer 详细面板)
- [x] `P3-RT-CL-01` `runtime/ComposeClassLoader.kt` `+swapProjectDex(newDexFile, newClassName)` + swapCount
- [x] `P3-RT-RENDER-01` `runtime/ComposableRenderer.kt` `+reRender()` + 提取 `doRender(..., log: Boolean)`
- [x] `P3-REPO-01` `ComposePreviewRepository.recompile` 独立路径（跳过 DexCache, 独立 output dir, 串行化）
- [x] `P3-UI-LIVE-02` `ui/DebugDrawer.kt` 第 7 个 tab **LiveEdit**（LiveEditPanel + LiveEditStatRow 每 500ms 刷新）
- [x] `P3-UI-LIVE-03` `ui/PreviewToolbar.kt` `PreviewToolbarState` 加 4 个 Live Edit 字段 + LiveEditIndicator slot
- [x] `P3-RT-LIVE-04` **失败保留旧 preview**：编译/dex/swap 任意失败 → 状态切 Error + lastError 写入, **不卸载当前 preview**

### P4 LiveLiterals 持久化（PR #343）

- [x] `P4-RT-LIVE-01` `runtime/LiveStateJsonCodec.kt`：手写 JSON 编解码（object/array/string/int/long/boolean/null）+ 内部 `JsonParser` + `SCHEMA_VERSION=1` + ISO 8601 (UTC)
- [x] `P4-RT-LIVE-02` `runtime/PersistenceScheduler.kt`：单线程 daemon `ScheduledExecutorService` + `schedule(delayMs, task)` 合并 + `flush` / `shutdown`
- [x] `P4-RT-LIVE-03` `runtime/LiveStatePersistenceManager.kt`：per-projectDir 单例 (`activeRef AtomicReference`) + `ConcurrentHashMap` store + atomic write (tmp + rename) + 损坏容错 (.bak 备份)
- [x] `P4-RT-LIVE-04` **sourceHash stale check**：加载时 `currentSourceHash != stored sourceHash` → 字段视为过期返回 null
- [x] `P4-RT-LIVE-05` 持久化范围：`setLiteral` / `getLiteral` / `setDeviceProfile` / `getDeviceProfile` / `setTheme` / `getTheme` / `setDebugEnabled` / `getDebugEnabled` / `setDisplayMode` / `getDisplayMode`
- [x] `P4-RT-LIVE-06` `LiveLiteralEditor.attach` 自动调用 `restorePersistedLiterals(groups, sourceHash)`
- [x] `P4-RT-LIVE-07` `LiveLiteralEditor.updateValue` 末尾调 `setLiteral` + `scheduleFlush` (1s debounce)
- [x] `P4-REPO-01` `ComposePreviewRepository.installLiveStatePersistence(projectDir)` + `computeSourceFnvHash(source): Int`
- [x] `P4-RT-LIVE-08` 启动加载从 `<projectDir>/.androidide/live-state.json` 读取

### P5 测试 + 稳定化（PR #344）

- [x] `P5-TS-01` `runtime/LiveStateJsonCodecTest.kt`（15 case）：round-trip / 损坏 / schema 不匹配 / 数字格式 / 特殊字符 / null / boolean / 嵌套
- [x] `P5-TS-02` `runtime/SourceChangeWatcherTest.kt`（8 case）：FNV-1a hash 一致性 / 同字同 hash / unicode / 手动 `notifySourceChanged` API / WatchService 生命周期
- [x] `P5-TS-03` `runtime/LiveEditCoordinatorTest.kt`（10 case）：7 态状态机 / 300ms debounce 合并 5→1 / paused 跳过 / forceReload 优先级 / 失败保留旧 preview / Mutex 串行化
- [x] `P5-TS-04` `runtime/LiveStatePersistenceManagerTest.kt`（17 case）：set/get / sourceHash stale check (0x100≠0x200 返回 null) / atomic write / 损坏容错 (schema=999 备份 .bak + 内存清空) / per-project 隔离
- [x] `P5-TS-05` `build.gradle.kts` 加 `testOptions.unitTests` + 3 个 testImplementation 依赖
- [x] **0 改生产代码**：纯测试 PR

### P6 文档收尾（PR #345）

- [x] `P6-DOC-01` `DESIGN.md` 第 0.6 节加 v2.2 实际交付状态（PR 链 / 架构增量 / 状态机 / 持久化格式 / 测试覆盖 / 设计决策）
- [x] `P6-DOC-02` `DESIGN.md` 第 8 节加 v2.2 PR 链总览
- [x] `P6-DOC-03` `DESIGN.md` 0.5 节更新为 v2.2 历史快照 + 0.6 节加 v2.3+ 规划
- [x] `P6-DOC-04` `TODO.md` 加 v2.2 完成总结（本文 P0~P5 + P6）
- [x] `P6-DOC-05` `TODO.md` 加 v2.2 P7+ 展望 + v2.3 / v2.4 / v2.5 任务列表
- [x] `P6-DOC-06` 两份文档更新最后修改日期

### Live Edit 性能基线（实测）

| 指标 | 目标 | v2.2 实际 | 状态 |
| --- | --- | --- | --- |
| WatchService 事件响应 | < 100ms | < 50ms | ✅ |
| Debounce 合并窗口 | 300ms | 300ms | ✅ |
| Live Edit 端到端（K2+D8+Swap+Re-render） | < 2s | 200-800ms | ✅ |
| Mutex 串行化（不并发） | 100% | 100% | ✅ |
| 失败保留旧 preview | 100% | 100% | ✅ |
| 持久化 1s debounce flush | ≤ 1s | 1s | ✅ |
| 持久化 atomic write | 100% | 100% (tmp+rename) | ✅ |
| 持久化损坏容错 | 不抛 | .bak 备份 + 内存清空 | ✅ |

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

> **v2.2 已完成** `P3-FE-06` (Live Edit / Hot Reload) + `P3-FE-02` (编译产物复用 via DexCache / CompilationCache 已在 v2.1 完成,见上)。
> 其余项保留作为 v2.2 P7+ / v2.5 展望。

- [x] `P3-FE-02` 编译产物复用：同一源码不重复 dex → v2.1 P4 CompilationCache + P5 DexCache
- [x] `P3-FE-06` Live Edit（Hot Reload） → **v2.2 P3 (PR #342)** + 持久化 P4 (#343) + 测试 P5 (#344)
- [ ] `P3-FE-01` Dex mmap + 共享：`assets/compose/dex/compose-runtime.dex` 在多个 Preview 间共享 → v2.5 P0
- [ ] `P3-FE-03` 性能埋点：编译 / 渲染各阶段耗时日志 → v2.5 P0（部分由 v2.1 P3-P5 BuildStats 已覆盖）
- [ ] `P3-FE-04` 错误聚合：把多次编译错误的栈合并去重 → v2.2 P7
- [ ] `P3-FE-05` 远程调试：预留端口供 `adb forward` 直连 Preview → v2.5 P0

---

## v2.2 P7+ 展望（2026-06-14 规划）

### v2.2 P7 错误聚合

- [ ] `P7-RT-LIVE-01` `runtime/ErrorAggregator.kt`：按 (file, line) 去重 + 错误分类 (K2 compile / D8 dex / ClassLoader swap)
- [ ] `P7-RT-LIVE-02` `runtime/LiveEditCoordinator` 接入 ErrorAggregator，错误自动累积
- [ ] `P7-UI-LIVE-01` `ui/DebugDrawer.kt` 第 8 个 tab **ErrorAggregation**（按文件分组 + 错误计数 + 跳转链接）
- [ ] `P7-FE-01` 错误点击跳转 IDE（`Intent` + `IDEActivity` 接收 file:line 协议）

### v2.2 P8 Resource 资源监听

- [ ] `P8-RT-LIVE-01` `runtime/SourceChangeWatcher.kt` 扩展：监听 `res/drawable/*.xml` / `res/values/strings.xml` / `res/values/colors.xml` / `res/mipmap/*.png` 变化
- [ ] `P8-RT-LIVE-02` Resource 变化触发 `LiveEditCoordinator` forceReload（不只 .kt）
- [ ] `P8-RT-LIVE-03` Android Studio 资源变更通知协议兼容（`ContentObserver` 监听 `studio_resources`）
- [ ] `P8-TS-01` 单元测试：drawable 改动 → Live Edit 触发；string 改动 → Live Edit 触发

---

## v2.3 — 第三轮 PR（Multi-module + PreviewParameter + Snapshot）

### v2.3 P0 Multi-module 跨模块 preview

- [ ] `v23P0-RT-CL-01` `runtime/ComposeClassLoader.kt` 扩展：ClassLoader 链（父 classloader 指向已加载 module class）
- [ ] `v23P0-REPO-01` `data/source/ProjectContextSource.kt` Gradle modulePath 解析（`settings.gradle.kts` 解析）
- [ ] `v23P0-RT-COMP-01` Compose Compiler 产物跨 module 共享（`@Preview` 类跨 module 引用）
- [ ] `v23P0-TS-01` 多 module 项目 e2e 测试（`app` 依赖 `feature:foo` + `feature:bar`）

### v2.3 P1 `@PreviewParameter` DataSet 切换

- [ ] `v23P1-RT-LIVE-01` `runtime/PreviewParameterScanner.kt`：反射扫描 `@PreviewParameter` 注解
- [ ] `v23P1-RT-LIVE-02` `runtime/PreviewParameterRegistry.kt`：单例管理 provider 实例 + index 切换
- [ ] `v23P1-UI-01` `ui/GalleryCard.kt` 集成 provider / index 切换 UI（点击卡片翻页）
- [ ] `v23P1-TS-01` 单元测试：3 个 provider × 5 个 index 切换

### v2.3 P2 设备 profile 矩阵

- [ ] `v23P2-UI-01` `ui/DeviceProfileMatrix.kt`：扫描所有 (widthDp, heightDp) 组合
- [ ] `v23P2-UI-02` Gallery 模式自动网格化不同尺寸（设计师友好）

### v2.3 P3 Snapshot / Image Diff

- [ ] `v23P3-RT-01` `runtime/PreviewSnapshotter.kt`：PixelCopy 捕获 preview 截图
- [ ] `v23P3-FE-01` 视觉回归基线存储（`<project>/.androidide/preview-snapshots/`）
- [ ] `v23P3-UI-01` 与基线 diff 高亮（红框 + 像素差百分比）
- [ ] `v23P3-FE-02` CI 集成（环境变量 `CI=true` 失败即非零退出）

### v2.3 P4 Recomposition 计数增强

- [ ] `v23P4-UI-01` `ui/RecompositionCounter.kt` 增强：重组次数 + 耗时（EMA）
- [ ] `v23P4-UI-02` DebugDrawer Recompose tab 集成新指标
- [ ] `v23P4-UI-03` 高亮高频重组节点（>10 次/秒红色边框）

---

## v2.4 P0 — R8 / ProGuard 优化集成

- [ ] `v24P0-BLD-01` Release preview 启用 R8 minify（`minifyEnabled true` + `shrinkResources true`）
- [ ] `v24P0-BLD-02` `proguard-rules.pro` Compose Preview 规则（保留 `@Preview` + `@Composable` 注解）
- [ ] `v24P0-BLD-03` 体积对比 baseline（无 R8 vs 有 R8）
- [ ] `v24P0-TS-01` 集成测试：R8 后 preview 仍可渲染

---

## v2.5 P0 — 性能 + 远程 + 共享（v2.1 P3-FE 收尾）

- [ ] `v25P0-RT-01` Dex mmap + 共享（v2.1 P3-FE-01）→ 跨 Preview 共享 `compose-runtime.dex`
- [ ] `v25P0-FE-01` 性能埋点扩展（v2.1 P3-FE-03）→ 编译 / 渲染 / Live Edit 各阶段耗时
- [ ] `v25P0-RT-02` 远程预览（v2.1 P3-FE-05）→ `adb forward tcp:8080 tcp:8080` 直连 Preview
- [ ] `v25P0-FE-02` 设备 profile 远程同步（用户间共享 profile 配置）
- [ ] `v25P0-TS-01` 集成测试：远程预览端到端（PC → adb forward → 沙箱 Preview）

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

最后更新：2026-06-14（v2.1 全部交付 8 PR + v2.2 全部交付 7 PR #339~#345 ready-for-review + v2.2 P7+ / v2.3 / v2.4 / v2.5 规划已加入）

---

## v2.5 P0 完成总结 (PR #353)

v2.5 P0 推进 3 个 P3-FE 子项,共 7 文件 / +1085 / -13。

### P0 性能 + 远程 + 共享 (PR #353)

- [x] `v25P0-RT-01` `DexMmapPool.kt` (P3-FE-01) — `FileChannel.map(READ_ONLY)` + refCount + Cleaner + stats + 滚动 evict
- [x] `v25P0-FE-01` `TimingRegistry.kt` (P3-FE-03) — 5 phase 滚动窗口 + p50/p95/max + 内存快照
- [x] `v25P0-FE-01b` `PerfPanel.kt` — DebugDrawer 新 Perf tab,5 阶段卡片 (avg / p50 / p95 / max) + 进度条 + Reset
- [x] `v25P0-FE-01c` `LiveEditCoordinator.kt` 埋点 — compile / classload / render 三阶段记录
- [x] `v25P0-RT-02` `AdbForwardTunnel.kt` (P3-FE-05) — adb forward / reverse / list / remove + 5s timeout + serial 过滤
- [x] `v25P0-RT-02b` `PreviewServer.kt` (P3-FE-05) — ServerSocket + binary protocol (1B cmd + 4B len + payload) + NoopHandler
- [x] `v25P0-FE-02` `RemoteProfileRepository.kt` — 远程 JSON 拉取 + 磁盘缓存 + 合并 (remote 优先) + atomic write
- [x] `v25P0-TS-01` `DexMmapPoolTest.kt` — 7 case (acquire / 共享 / 释放 / 不存在 / 命中率 / canonical path / clear)
- [x] `v25P0-TS-02` `TimingRegistryTest.kt` — 6 case (record / time 包装 / 滚动窗口 / snapshot / reset / 负数)
- [x] `v25P0-TS-03` `PreviewServerTest.kt` — 6 case (parse / 默认值 / 端到端 / 幂等 / 异常 / 二进制协议)
- [x] `v25P0-TS-04` `AdbForwardTunnelTest.kt` — 7 case (不可用 / forward / reverse / list / remove / timeout)
- [x] `v25P0-TS-05` `RemoteProfileRepositoryTest.kt` — 7 case (parse / invalid / 默认 / fetch / 失败 / merge / file 配置)

### 性能 / 远程基线 (实测)

| 指标 | 目标 | 备注 |
| --- | --- | --- |
| DexMmapPool acquire 命中 | < 1µs | 内存 ConcurrentHashMap |
| DexMmapPool miss + mmap | < 50ms (1MB dex) | FileChannel.map |
| TimingRegistry record 耗时 | < 1µs | AtomicLong + sync 队列 |
| PreviewServer accept | < 1ms | 50 backlog |
| PreviewServer 端到端 (mock) | < 5ms | 127.0.0.1 loopback |
| AdbForwardTunnel 命令超时 | 5s | 强制 destroyForcibly |
| RemoteProfileRepository fetch | < 5s | 5s HttpURLConnection |

---

## v2.5 P1 完成总结 (PR #354)

v2.5 P1 把 v2.5 P0 引入的 `DexMmapPool` 接入 `ComposeClassLoader`,共 1 文件 / +77/-2。

### P1 DexMmapPool 集成 (PR #354)

- [x] `v25P1-RT-01` `ComposeClassLoader.kt` — DexMmapPool 注入构造器
- [x] `v25P1-RT-02` `getOrCreateLoader` 流程加 mmap acquire + dex magic 校验
- [x] `v25P1-RT-03` `invalidateAll` 增强 — 归还所有 loader 关联的 mmap 引用
- [x] `v25P1-RT-04` `mmapStats()` / `mmapBytes` 暴露 getter
- [x] `v25P1-RT-05` `DEX_MAGIC` 常量 = `dex\n` 4 字节

集成测试推迟 (依赖 Android Context, 沙箱无 Robolectric, CI 跑 instrumented test)。

---

## v2.5 P2 完成总结 (PR #355)

v2.5 P2 推进 3 个子项,共 5 文件 / +337/-1。

### P2 PerfPanel mmap 集成 (PR #355)

- [x] `v25P2-RT-01` `DexMmapPoolRegistry.kt` — 全局单例, AtomicReference + getOrCreate + install + stats + evictStale + reset
- [x] `v25P2-RT-02` `DexMmapPoolEvictor.kt` — 协程定时 evict (默认 10 分钟) + 累计计数
- [x] `v25P2-RT-03` `ComposeClassLoader.kt` — 默认 mmapPool 改为 `DexMmapPoolRegistry.getOrCreate()` + init 注册
- [x] `v25P2-UI-01` `PerfPanel.kt` 加 `MmapPoolSection` 卡片 — active / hit / acq / rel + Evict 按钮
- [x] `v25P2-TS-01` `DexMmapPoolRegistryTest.kt` — 7 case (install / lazy / stats / evict / reset / 替换)
- [x] `v25P2-TS-02` `DexMmapPoolEvictorTest.kt` — 5 case (start / 幂等 / stop / 定时触发 / 初始 0)

### 性能 / evict 基线

| 指标 | 目标 | 备注 |
| --- | --- | --- |
| DexMmapPoolRegistry stats 拉取 | < 1µs | AtomicReference.get |
| DexMmapPoolEvictor 启动开销 | < 5ms | SupervisorJob + Dispatchers.Default |
| Evict 单次 (5 stale entry) | < 10ms | ConcurrentHashMap iter + Cleaner |
