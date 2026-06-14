# Compose Preview v2.1 TODO 清单

> 与 [`DESIGN.md`](./DESIGN.md) 配套。
> **任务编号规范：`P?-XX-YY` —— `?` 优先级、`XX` 模块代号、`YY` 序号。**
>
> 模块代号：
> - `BLD` Build Phase（含 K2 / D8 / ASM）
> - `RT`  Runtime
> - `UI`  UI / Interaction
> - `DEV` 真实设备模拟（含 cutout / 状态栏 / 铰链）
> - `FE`  Feature
> - `TS`  Test
> - `DOC` 文档
>
> **优先级说明**：
> - **P0**：本轮 PR #N 必修（设计与基础框架）
> - **P1**：下一轮 PR（功能完整化）
> - **P2**：再下一轮（性能与可观测性）
> - **P3**：探索（LiveLiterals / Hot Reload）
> - **P4**：体验打磨（动画、辅助功能）

---

## 0. 总体里程碑

| 里程碑 | 内容 | 状态 |
| --- | --- | --- |
| M0：v2 完成 | Build Phase 重写 + 反射 → MethodHandle + 基础 UI | ✅ 已合并 (f8227aa) |
| **M1：v2.1 设计落地（本轮 PR）** | 真实设备模拟（30+ Profile）+ SystemBars + Cutout 几何 + 全 Compose UI 升级 + DESIGN/TODO 重写 | 🚧 当前 |
| M2：调试工具（PR #N+1） | DebugDrawer + ComponentInspector + Recompose + Logcat | — |
| M3：可视化编辑（PR #N+2） | 拖动 / Resize / ColorPicker / LayoutBounds | — |
| M4：ASM 加速（PR #N+3） | 字节码 binder + dead code strip | — |
| M5：折叠屏（PR #N+4） | 铰链 + 内/外屏切换 | — |
| M6：性能调优（PR #N+5） | Prewarm + DEX mmap 共享 + 埋点 | — |
| M7：多 Preview + 参数（PR #N+6） | PreviewGridLayout + @PreviewParameter/FontScale/LightDark | — |
| M8：LiveLiterals（PR #N+7） | 实验 | — |

---

## P0 — 本轮 PR #N（必须完成）

### BLD 构建阶段

- [ ] `P0-BLD-01` 改造 `build.gradle.kts`：
  - 增加 `bytecodeAcceleratorJars` Configuration（`org.ow2.asm:asm:9.7` + `asm-commons` + `asm-tree`）
  - 调整 `packageComposeSdk` 任务，把 ASM jar 一并打包到 `compose-jars.zip/asm/`
  - 锁定 compose-compiler ↔ kotlin 兼容对（1.5.10 ↔ 1.9.22）
- [ ] `P0-BLD-02` 增强 `compiler/AssetsComposeBundles.kt`：
  - 暴露 `asmJars: List<File>` 字段
  - 校验 zip 内 ASM jar 完整性；缺失时 `init()` 返回 `false` 但不抛
  - 增加 `BytecodeCacheDir`（`<workDir>/bytecode/`）
- [ ] `P0-BLD-03` 增强 `compiler/BundledComposeCompiler.kt`：
  - 实现自定义 `MessageCollector` 子类，捕获 K2 真实 `Diagnostic`（含文件/行/列/严重等级）
  - 真正写回 `CompileDiagnostic` 列表
  - 输出 `BuildPhaseTimings` 子段（k2CompileMs）
- [ ] `P0-BLD-04` 增强 `compiler/BundledD8Dexer.kt`：
  - 支持传入多个 class 目录（user classes + ASM 生成的 binder）
  - 输出 dex 路径返回 `BuildPhaseTimings`（d8DexMs）
  - 失败时给出 **可恢复** 错误信息（含 D8 实际 stdout/stderr）
- [ ] `P0-BLD-05` 增强 `compiler/CompileModels.kt`：
  - 新增 `data class BuildPhaseTimings`（7 段耗时 + totalMs）
  - 在 `CompileResult` 中带 `timings: BuildPhaseTimings` 字段
- [ ] `P0-BLD-06` 增强 `data/repository/ComposePreviewRepositoryImpl.kt`：
  - 串联 `k2Compile → asmTransform → d8Dex` 三段计时
  - 错误统一包成 `CompilationException(message, diagnostics, cause)`，保留 cause 链

### DEV 真实设备模拟（v2.1 核心）

- [ ] `P0-DEV-01` 新增 `data/device/CutoutGeometry.kt`：
  - `sealed class CutoutGeometry` + `Notch` / `PunchHole` / `WaterfallCurve` 子类
  - `enum Anchor { TOP_CENTER, TOP_LEFT, TOP_RIGHT, LEFT_CENTER, RIGHT_CENTER }`
- [ ] `P0-DEV-02` 新增 `data/device/PhysicalKey.kt`：
  - `sealed class PhysicalKey` + `Power` / `VolumeUp` / `VolumeDown` / `Camera` 子类
  - 含 `positionDp` 相对屏幕位置
- [ ] `P0-DEV-03` 重写 `ui/DeviceProfile.kt`：
  - 增加字段：`manufacturer`, `model`, `osVersion`, `formFactor`, `screenGeometry`, `cutout`, `bezels`, `chassisColor`, `physicalKeys`, `statusBarHeightDp`, `navigationBarHeightDp`, `isRound`, `hasNotch`, `hasPunchHole`, `hasWaterfall`, `isFoldable`
  - 旧字段（`widthPx`/`heightPx`/`densityDpi`/`frameStyle`/`isCustom`）保留
  - 保留 `DeviceProfiles.PIXEL_4/5/6/7` 等老数据，新字段用合理默认
- [ ] `P0-DEV-04` 新增 `data/device/DeviceCatalog.kt`：
  - 至少 30 个真实设备 profile（详见 DESIGN 6.3 表）
  - `DeviceCatalog.builtinProfiles` / `DeviceCatalog.byFormFactor(...)` / `DeviceCatalog.byId(...)` API
  - 自定义 profile 合并到 list 顶部
- [ ] `P0-DEV-05` 重写 `ui/DeviceFrame.kt`：
  - 实现 `Bezels`（上下左右 dp）渲染
  - 根据 `formFactor` 切换渲染：PHONE / FOLDABLE_INNER / FOLDABLE_OUTER / TABLET / WATCH
  - WATCH 模式：圆表，圆形 `Canvas` 遮罩
  - 折屏：内屏无 cutout，外屏 punch-hole
  - 移除旧的简单 `NotchOverlay` 内联实现
- [ ] `P0-DEV-06` 新增 `ui/CutoutOverlay.kt`：
  - 接收 `CutoutGeometry` 渲染：
    - `Notch` → 顶部圆角矩形
    - `PunchHole` → 圆形（按 anchor 定位）
    - `WaterfallCurve` → 两侧 Bezier 路径
  - 颜色用黑色 0.95 alpha（与设备一体感）
- [ ] `P0-DEV-07` 新增 `ui/FoldableHingeOverlay.kt`：
  - 接收 `hingeWidthDp`，画中间阴影 + 折痕
  - 默认 4dp 宽阴影 + 60dp 折痕区
- [ ] `P0-DEV-08` 新增 `ui/SystemBarsOverlay.kt`：
  - `enum class SystemBarsTheme { AUTO, LIGHT, DARK, TRANSLUCENT_LIGHT, TRANSLUCENT_DARK }`
  - 状态栏内容：时间（系统时区 / 10s 刷新）、电池（4 格 + 百分比）、Wi-Fi、信号、通知红点
  - 导航栏：返回 / Home / 最近；支持手势导航横杠
  - 高度按 `profile.statusBarHeightDp` / `profile.navigationBarHeightDp`
  - 提供 `SystemBarsTheme` 持久化（SharedPreferences key=`preview.systemBarsTheme`）

### UI 集成（接入 Activity）

- [ ] `P0-UI-01` 新增 `ui/PreviewToolbar.kt`：
  - 顶栏 Composable：设备切换 / 主题切换 / 缩放控制 / 调试开关 chip
  - 与 `ComposePreviewViewModel` 各 StateFlow 联动
- [ ] `P0-UI-02` 重写 `ComposePreviewActivity.kt`：
  - 全 Compose UI：顶栏（PreviewToolbar）+ 中间（ZoomablePane + DeviceFrame + SystemBars + RenderTarget）+ 底部（占位抽屉）
  - 状态分发：Loading / Error / Empty / NeedsBuild / Ready
  - 错误页：保持 XML 版的错误详情可滚动
- [ ] `P0-UI-03` 增强 `ui/ZoomController.kt`：
  - 真正接入 Activity：双击切换 Fit/100%；Ctrl+Wheel 缩放
  - 与 `PreviewToolbar` 的缩放 chip 联动
- [ ] `P0-UI-04` 增强 `ui/ResolutionEditor.kt`：
  - 增加 cutout 选择（None / Notch / PunchHole / Waterfall）
  - 实时显示预览尺寸
- [ ] `P0-UI-05` 重写 `ui/DeviceProfileSheet.kt`：
  - 按 formFactor 分组：Phone / Foldable / Tablet / Watch / Custom
  - 每行显示：设备名、分辨率、宽 dp、关键 cutout 标识
  - 真实缩略图（用 `Canvas` 画设备外形，标 cutout 位置）

### ViewModel 状态

- [ ] `P0-UI-06` 增强 `PreviewConfig.kt`：
  - 增字段 `fontScale: Float?` / `isLightDark: Boolean = false` / `parameterProviderName: String?`
- [ ] `P0-UI-07` 增强 `PreviewState.kt`：
  - 增 `data class DeviceConfig`（profile / systemBarsTheme / showStatusBar / showCutout / showChassis）
  - 增 `data class ViewportState`（zoom / offsetX / offsetY / fitMode）
  - 在 `Ready` 中携带 `deviceConfig` / `viewport`
- [ ] `P0-UI-08` 重写 `ComposePreviewViewModel.kt`：
  - 加 StateFlow：`deviceConfig` / `viewport` / `displayMode` / `selectedPreview` / `availablePreviews` / `systemBarsTheme` / `debugEnabled` / `buildTimings`
  - 提供方法：`selectDevice(profile)` / `setZoom(scale)` / `setSystemBarsTheme(theme)` / `resetViewport()` / `toggleDebug()`
  - 序列化/恢复 `ViewportState` 到 `SavedStateHandle`（配置变化时保留）

### Domain 解析

- [ ] `P0-DOM-01` 增强 `domain/PreviewSourceParser.kt`：
  - 支持 `@PreviewParameter(provider = ...)` → 解析 provider 类名
  - 支持 `@PreviewFontScale = 1.5f` → 解析 fontScale
  - 支持 `@PreviewLightDark` → 标记 isLightDark
  - 全部走纯正则（不上 PSI），K2 真实解析在后续 PR

### DOC 文档

- [ ] `P0-DOC-01` 编写 / 更新 `DESIGN.md`（v2.1 完整重写）✅
- [ ] `P0-DOC-02` 编写 / 更新 `TODO.md`（本文件）✅
- [ ] `P0-DOC-03` PR body 引用本文件章节

### TS 测试

- [ ] `P0-TS-01` 单元测试：`CutoutGeometry` 各子类渲染参数
- [ ] `P0-TS-02` 单元测试：`DeviceCatalog.byFormFactor` / `byId` 查询
- [ ] `P0-TS-03` 单元测试：`BuildPhaseTimings.totalMs` 计算
- [ ] `P0-TS-04` 端到端：沙箱构建机冷启动 → 编译 → 渲染（手动）
- [ ] `P0-TS-05` 设备模拟视觉回归：5 种形态截图对比

---

## P1 — 下一轮 PR（功能完整化）

### 调试面板

- [ ] `P1-UI-01` 新增 `ui/DebugDrawer.kt`：
  - 底部 ModalBottomDrawer
  - 标签页：Inspector / Recompose / Logcat / Stats
  - 默认折叠，点击展开
- [ ] `P1-UI-02` 重写 `ui/ComponentInspector.kt`：
  - 实现 `LayoutNodeInspector` 反射读 `LayoutNode.coordinates` / `width` / `height` / `x` / `y`
  - 画边界框（1dp 红色矩形 + 节点名 overlay）
  - 属性面板：bounds / parent / modifier chain（简化）
- [ ] `P1-UI-03` 增强 `ui/RecompositionCounter.kt`：
  - 真实挂载到 `RenderComposable`：每次 recompose `tick()`
  - 在 `DebugDrawer` 显示每个 Composable 的计数
  - 提供 "高亮重排" 开关（> 5 次的 Composable 角标变红）
- [ ] `P1-UI-04` 新增 `ui/LogcatPanel.kt`：
  - 实现 `PreviewPrintStream` 替换 `System.out`
  - 捕获 `println` / `Log.d` / `Log.w` / `Log.e`
  - 显示级别 / 时间 / 内容
  - 支持过滤 / 折叠 / 清空
- [ ] `P1-UI-05` 性能 Stats 标签页：
  - 显示 `BuildPhaseTimings` 各阶段耗时
  - 显示 dex 缓存命中率
  - 显示 `MethodHandleResolver` 缓存大小

### 物理键 / 折叠完善

- [ ] `P1-DEV-01` 在 `DeviceFrame` 渲染 `PhysicalKey`：
  - 电源键 / 音量 ± / 相机键（按设备）
  - 简单矩形 + 文字（不做 3D 仿真）
- [ ] `P1-DEV-02` 折叠屏内 / 外屏自动切换：
  - 检测 `Jetpack WindowManager` 的 `FoldingFeature`
  - 模拟器场景：手动按钮切换 inner/outer

### 预览源解析增强

- [ ] `P1-DOM-01` `@PreviewFontScale` 完整支持：
  - 解析 `fontScale = 1.5f`
  - 渲染时包 `CompositionLocalProvider(LocalDensity provides ...)` 注入
- [ ] `P1-DOM-02` `@PreviewLightDark` 支持：
  - 同时生成两张 preview：Light + Dark
  - 网格模式下并排展示
- [ ] `P1-DOM-03` `@PreviewParameter` 支持（无参构造 Provider）：
  - 解析 `provider = MyProvider::class`
  - 反射 `MyProvider()` 无参构造
  - 调用 `provider.values`（Sequence）取第一项
  - 注入为 Composable 最后一个参数

### 多 Preview 网格

- [ ] `P1-UI-06` 新增 `ui/PreviewGridLayout.kt`：
  - AS 风格卡片网格：自适应列数（按屏宽）
  - 每张卡片：设备缩略图 + Composable 名 + 缩放按钮
  - 单击进入 SINGLE 模式

### 主题

- [ ] `P1-UI-07` 增强 `ui/ThemeSelector.kt`：
  - Light / Dark / Custom
  - Custom 时弹 `ColorSchemeEditor`（Material3 ColorScheme 全字段）
  - 持久化到 SharedPreferences

### UI 集成细节

- [ ] `P1-UI-08` 弃用 `ComposePreviewFragment.kt`：
  - 标记 `@Deprecated`
  - 把 ComposePreviewFragment 的代码迁到 Activity

---

## P2 — 性能与字节码加速（PR #N+3 / #N+5）

### 字节码加速（核心 P2）

- [ ] `P2-BLD-01` 新增 `compiler/AsmComposeBinder.kt`：
  - 用 ASM 9.7 + asm-commons 读 `.class`
  - Pass 1：扫描 `Method` 上的 `@Composable` 注解（注解在 `kotlin/Metadata` 里）
  - Pass 2：对每个含 `(Composer, int)` 形参的 `@Composable fun`，在 `binder/` 包下生成 `Binder$FunName.class`：
    ```java
    public final class Binder$MyComposable {
      private static final MethodHandle HANDLE;
      static {
        HANDLE = MethodHandles.lookup().unreflect(
          PreviewKt.class.getDeclaredMethod("MyComposable", Composer.class, int.class)
        );
      }
      public static void invoke(Composer c, int changed) {
        HANDLE.invokeWithArguments(c, changed);
      }
    }
    ```
  - Pass 3：把 binder `.class` 输出到 `<workDir>/binder/`
- [ ] `P2-BLD-02` 新增 `compiler/BytecodeCache.kt`：
  - key = `sourceHash + asmVersion`
  - value = `<workDir>/binder/` 目录
  - 缓存命中时跳过整个 ASM pass
- [ ] `P2-BLD-03` 增强 `BundledD8Dexer`：
  - 接受 `binderDir` 作为额外输入
  - 产物：`preview.dex` + `binders.dex`（与运行时 `<bundle>` 合并）
- [ ] `P2-RT-01` 新增 `runtime/AsmBinderInvoker.kt`：
  - 提供 `tryGetBinder(dexFile, className, functionName): MethodHandle?`
  - 优先于 `MethodHandleResolver`
- [ ] `P2-RT-02` 增强 `runtime/ComposableRenderer.kt`：
  - 渲染时优先查 `AsmBinderInvoker`
  - binder 命中 → `binder.invoke(c, 0)`
  - binder 未命中 → fallback `MethodHandleResolver`
- [ ] `P2-BLD-04` Dex layout 优化（可选）：
  - 用 ASM `ClassWriter` 重排 method 顺序，让 binder 排在最前
  - 提升 ICache 命中率

### 运行时优化

- [ ] `P2-RT-03` 增强 `runtime/ComposeClassLoader.kt`：
  - 实现 LRU 驱逐（最多 10 个 loader）
  - 增加 `releaseEldest()` 钩子
  - 提供 `loadShared(dexBytes: ByteArray): ClassLoader` 用 `InMemoryDexClassLoader` 共享 dex
- [ ] `P2-RT-04` 新增 `compiler/ComposeRuntimePrewarm.kt`：
  - 启动时 `Class.forName(name, false, classLoader)` 预热 30+ 关键类
  - 写入 `BuildPhaseTimings` 一段
- [ ] `P2-BLD-05` 新增 `compiler/DexSharedLibraryLoader.kt`：
  - 把 `compose-runtime.dex` 用 `InMemoryDexClassLoader` 加载到主进程
  - ComposeClassLoader 复用，避免重复 dex

### 性能埋点

- [ ] `P2-FE-01` 完善 `BuildPhaseTimings`：
  - 7 段耗时都真实采集
  - 在 `DebugDrawer.Stats` 标签页展示
  - 写一行结构化日志（JSON）便于后续分析

### 可视化编辑

- [ ] `P2-UI-09` 新增 `ui/ColorPickerOverlay.kt`：
  - 在 Composable 截图上叠加 color picker
  - 取像素 → 显示 HEX / RGB
  - 写入剪贴板
- [ ] `P2-UI-10` 新增 `ui/LayoutBoundsOverlay.kt`：
  - 反射读 `LayoutNode` 画 1dp 红色矩形
  - 开关：默认关
- [ ] `P2-UI-11` `Modifier.editable`：
  - 拖动 Composable
  - 8 控制点 resize
  - 实时修改 padding / size
- [ ] `P2-UI-12` `Modifier.inspector`：
  - 点击节点高亮
  - 在 DebugDrawer 展示该节点的 properties

---

## P3 — 探索（PR #N+7+）

- [ ] `P3-FE-01` LiveLiterals 实验：
  - 反射修改 `LiveLiterals$*` 类的 `IntState` / `StringState` 值
  - Compose 编译器需开启 `-P plugin:androidx.compose.compiler.plugins.kotlin:liveLiterals=true`
  - 最小可用版本
- [ ] `P3-FE-02` Hot Reload（incremental）：
  - 监听 `.kt` 文件变更
  - 增量重编（只编变更文件）→ 重新 dex → 刷新 ComposeView
- [ ] `P3-FE-03` 远程调试：
  - 预留端口供 `adb forward tcp:xxx tcp:xxx`
  - 外部 IDE 可直连 Preview 进程 dump 状态
- [ ] `P3-FE-04` K2 PSI 解析（取代正则）：
  - 集成 `kotlin-compiler-embeddable` 的 PSI 入口
  - 准确解析 `@Preview` 全部参数（含 KDoc / annotation arguments）
  - 解析 `@Composable fun` 真实签名

---

## P4 — 体验打磨

- [ ] `P4-UI-01` 工具栏动画：
  - 设备切换时缩放动画
  - 主题切换时颜色渐变
- [ ] `P4-UI-02` 设备缩略图：
  - 用 `Canvas` 画真实设备外形（基于 `DeviceProfile`）
  - 包含 cutout 标识
- [ ] `P4-UI-03` 键盘快捷键：
  - `Ctrl+1/2/3`：切换主题
  - `Ctrl+D`：切换设备 Sheet
  - `Ctrl+L`：切换 DebugDrawer
  - `F` 键：Fit；`1` 键：100%
- [ ] `P4-UI-04` 辅助功能（a11y）：
  - TalkBack 描述（设备名 / 分辨率 / 主题）
  - 高对比度模式
- [ ] `P4-UI-05` 右键菜单：
  - 单 Composable 复制路径
  - 导出当前 Preview 截图
  - 在 Finder 打开 class
- [ ] `P4-DEV-01` 国际化：
  - 工具栏文字（zh / en / ja / ko）
  - 设备名（保留原名）
- [ ] `P4-FE-01` 设备自定义：
  - 用户可上传 JSON 自定义设备
  - 持久化到 `assets/user_devices.json`

---

## 验收清单（每个 PR 必跑）

- [ ] 模块编译通过：
      `./gradlew :modules:compose-preview:assembleDebug`
- [ ] Lint 通过：
      `./gradlew :modules:compose-preview:lintDebug`
- [ ] 单元测试通过：
      `./gradlew :modules:compose-preview:testDebugUnitTest`
- [ ] 沙箱无 `.m2` 环境下冷启动可渲染
- [ ] 5 种设备形态（Phone/Notch/PunchHole/Waterfall/Foldable）视觉回归通过
- [ ] PR 描述引用本 TODO 章节
- [ ] `DESIGN.md` 同步更新（如有架构变动）
- [ ] 至少 1 张截图（Phone + Foldable 形态）

---

## 文件改动总览（v2.1 全部 PR）

| 类别 | 新增 | 重写 | 增强 / 保留 |
| --- | --- | --- | --- |
| Build | 4 (AsmComposeBinder, BytecodeCache, ComposeRuntimePrewarm, DexSharedLibraryLoader) | 0 | 6 |
| Runtime | 1 (AsmBinderInvoker) | 0 | 3 |
| UI / 交互 | 13 (PreviewToolbar, DebugDrawer, ColorPickerOverlay, LayoutBoundsOverlay, LogcatPanel, PreviewGridLayout, ModifierInspector, PreviewContextMenu, PreviewGridLayout 等) | 5 (DeviceFrame, DeviceProfile, DeviceProfileSheet, ComponentInspector, ComposePreviewActivity) | 8 |
| Data | 3 (DeviceCatalog, CutoutGeometry, PhysicalKey) | 0 | 1 |
| Domain | 0 | 0 | 2 (PreviewSourceParser, ParsedPreviewSource) |
| 入口 | 0 | 1 (ComposePreviewActivity) | 0 |
| 资源 | 6 (device_profiles.xml, cutout_geometries.xml, strings_preview.xml, 3 类 drawable) | 0 | 0 |
| 文档 | 0 | 2 (DESIGN.md, TODO.md) | 0 |

总计：**新增 ~27 个文件，重写 ~8 个文件，增强 ~20 个文件。**

---

最后更新：2026-06-14
