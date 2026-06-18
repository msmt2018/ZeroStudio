# Compose Preview v3.1 TODO 清单

> 与 [`DESIGN.md`](./DESIGN.md) 配套
> **v3.1 终态**：纯 gradle dex 加载路径，零 assets 预打包 jar，零进程内 K2 / D8 / ASM

---

## 0. 里程碑

| 里程碑 | 内容 | 状态 |
| --- | --- | --- |
| M0：v1 | 进程内 K2 + D8 编译 + assets 预打包 jar | ✅ 已废弃 |
| M1：v2 | 反射 → MethodHandle + 基础 UI | ✅ 已废弃 |
| M2：v2.1 | AssetsBundles + BundledK2 + BundledD8 + DexCache（混合） | ✅ 已废弃 |
| **M3：v3 / v3.1（本轮）** | 完全切到 gradle dex 加载路径，删干净 v2.1 残留 | ✅ **本轮 PR #400 完成** |
| M4：v3.2+ | 真实设备 catalog 扩充 + ComponentInspector 实用化 | 🚧 下一轮 |

---

## ✅ 本轮 PR #400 已完成

### 架构简化（v3.1）

- [x] 删 `compiler/AssetsComposeBundles.kt`（v2.1 assets zip 解压 + SHA 校验）
- [x] 删 `compiler/BundledComposeCompiler.kt`（v2.1 进程内 K2JVMCompiler）
- [x] 删 `compiler/BundledD8Dexer.kt`（v2.1 进程内 D8 / R8 fat jar）
- [x] 删 `compiler/DexCache.kt`（v2.1 源 hash → dex 缓存）
- [x] `CompileModels.kt` 精简：只保留 `CompileDiagnostic`，删 `CompileResult` / `DexResult`

### Repository 简化

- [x] `InitializationResult.Ready` 删 `runtimeDex` 字段
- [x] `CompilationResult` 删 `runtimeDex` 字段
- [x] 删 `ComposePreviewRepository.computeSourceHash()`（不再有进程内 dex 缓存）
- [x] `compilePreview` **唯一走 gradle-dex 模式**：`BuildService.executeTasks(assemble<Variant>)`
- [x] 删 `useGradleDex` 注释标志（v3.1 唯一模式，不再需要 opt-in）
- [x] 删 `bundles / compiler / dexer / dexCache / runtimeDex / workDir` 字段

### 渲染引擎简化

- [x] `PreviewRenderEngine.render` 删 `runtimeDex` 参数
- [x] `DexRuntime.loadAll` 接受单一 `dexFiles` 列表（合并 preview + project）
- [x] 删 `PreviewState.Ready.runtimeDex` 字段

### build.gradle.kts 简化

- [x] 删 `composeCompilerJars` / `composeAarsForPreview` / `kotlinCompilerJars` / `bundledD8Jars` 4 个 Configuration
- [x] 删 `copyComposeCompilerPlugin` / `extractComposeClasses` / `copyKotlinCompilerJars` / `copyBundledD8Jars` / `compileRuntimeDex` / `packageComposeJars` 6 个 Task
- [x] 删 `compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.22")` 依赖
- [x] 删 `implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22")` 依赖
- [x] 删 `preBuild.dependsOn(packageComposeJars)` 链路
- [x] 删 `assets/compose/compose-jars.zip` 依赖（如有残留）
- [x] 保留 `material-icons-extended`（PreviewToolbar extended-only icon 需要）

### 配套修复

- [x] 修 `build 成功后停在 NeedsBuild / Build Project 按钮页`（commit `9705b16b`）
- [x] 双层防御：Repository 优先 `projectDexFiles.isNotEmpty()` 判定 + ViewModel 强制 `compileNow` 兜底

### 文档同步

- [x] `DESIGN.md` 重写为 v3.1 实际状态
- [x] `TODO.md` 重写为 v3.1 实际状态

---

## 🚧 下一轮候选（按优先级）

### P0-1 真实设备 catalog 扩充

- [ ] `data/device/CutoutGeometry.kt` sealed class (Notch / PunchHole / WaterfallCurve)
- [ ] `data/device/PhysicalKey.kt` sealed class (Power / VolumeUp / VolumeDown / Camera)
- [ ] `data/device/DeviceCatalog.kt` 30+ 真实设备 (Pixel / iPhone / Mate / Foldable / Watch)
- [ ] `ui/DeviceFrame.kt` 按 formFactor 切渲染 (PHONE / FOLDABLE_INNER / OUTER / TABLET / WATCH)

### P0-2 系统状态栏真实化

- [ ] `ui/SystemBarsOverlay.kt`：时间 + 电池 + Wi-Fi + 信号
- [ ] `ui/CutoutOverlay.kt`：Notch / PunchHole / Waterfall 渲染
- [ ] `ui/FoldableHingeOverlay.kt`：铰链阴影

### P1-1 多 Preview 网格

- [ ] `ui/PreviewGridLayout.kt`：AS 风格卡片网格
- [ ] `domain/PreviewSourceParser.kt`：支持 `@PreviewParameter(provider = ...)`
- [ ] `domain/PreviewSourceParser.kt`：支持 `@PreviewFontScale = 1.5f` / `@PreviewLightDark`

### P1-2 调试工具

- [ ] `ui/DebugDrawer.kt`：底部 ModalBottomDrawer
- [ ] `ui/ComponentInspector.kt` 真正反射 LayoutNode（如做这个**才**重新加回 `kotlin-reflect`）
- [ ] `ui/RecompositionCounter.kt` 真实挂载 RenderComposable
- [ ] `ui/LogcatPanel.kt`：拦截 println / Log.d

### P2-1 性能

- [ ] DexClassLoader LRU 驱逐（最多 10 个）
- [ ] `InMemoryDexClassLoader` (API 26+) 共享 dex
- [ ] BuildPhaseTimings 完整埋点

### P3-1 探索

- [ ] LiveLiterals 实验
- [ ] Hot Reload（incremental）

---

## ❌ 已废弃（不要做）

- 进程内 K2JVMCompiler 编译
- 进程内 D8 / R8 dex
- assets 预打包 compose-jars.zip / kotlin-compiler-embeddable / r8.jar
- ASM binder 生成（`AsmComposeBinder`）
- DexCache（源 hash → dex 缓存，gradle build cache 已替代）
- `runtimeDex` 字段（compose runtime 走 PathClassLoader parent 委托）
- 任何需要把 jar 下载 / 打包到 assets 的逻辑

---

## 验收清单

- [x] `./gradlew :modules:compose-preview:assembleDebug` 通过
- [x] build.gradle.kts 不再下载 / 打包任何 jar 到 assets
- [x] `cacheDir/compose-sdk/` / `cacheDir/compose_dex_cache/` / `codeCacheDir/compose_preview_opt/` 等 v2.1 残留被清理
- [x] build 成功后不再停在 NeedsBuild
- [x] "Compose runtime jars missing" 错误不可能再出现（v2.1 链路彻底删除）
- [x] `compilePreview` 唯一走 `BuildService.executeTasks(assemble<Variant>)`
- [ ] 真实设备 catalog 30+（下一轮）

---

最后更新：2026-06-18
