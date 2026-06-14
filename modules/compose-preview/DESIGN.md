# Compose Preview v2.1 重构与升级方案

> 模块：`modules/compose-preview/`
> 版本：v2.1（基于 v2 的设计补全：真实设备模拟、字节码加速、可视化编辑）
> 状态：详细设计 + 待办事项（PR #N 中执行 P0；后续 PR #N+1 ~ #N+5 分批落地）
> 目标：**100% 资产自包含 · 零 Maven · 进程内 K2JVMCompiler · 真实设备模拟（涵盖刘海 / 针孔 / 瀑布 / 折叠） · ASM 字节码加速 · 对标 IDEA / AS Compose Preview**

---

## 目录

1. [背景与现状问题](#1-背景与现状问题)
2. [目标与非目标](#2-目标与非目标)
3. [v2 现状评估（已落地但需补全的部分）](#3-v2-现状评估已落地但需补全的部分)
4. [v2.1 关键增量](#4-v21-关键增量)
5. [新架构（v2.1 全景）](#5-新架构v21-全景)
6. [真实设备模拟子系统](#6-真实设备模拟子系统)
7. [系统状态栏子系统](#7-系统状态栏子系统)
8. [字节码加速子系统（ASM）](#8-字节码加速子系统asm)
9. [Build Phase](#9-build-phase)
10. [Runtime 优化](#10-runtime-优化)
11. [可视化编辑工具箱](#11-可视化编辑工具箱)
12. [调试与可观测性](#12-调试与可观测性)
13. [新功能矩阵](#13-新功能矩阵)
14. [详细文件改动表](#14-详细文件改动表)
15. [风险与缓解](#15-风险与缓解)
16. [与 IDEA / AS 对标](#16-与-idea--as-对标)
17. [测试 / 验收](#17-测试--验收)
18. [PR 拆分计划](#18-pr-拆分计划)
19. [引用](#19-引用)

---

## 1. 背景与现状问题

### 1.1 模块当前结构（v2 落地后）

```
modules/compose-preview/
├── build.gradle.kts                         // 自包含打包: kotlin-compiler-embeddable + r8 + compose runtime
├── DESIGN.md                                // 本文件
├── TODO.md                                  // 任务清单
└── src/main/
    ├── AndroidManifest.xml
    ├── assets/compose/                      // 由 build 时打包 (compose-jars.zip, compose-runtime.dex)
    ├── res/                                 // 布局 / 菜单 / 资源
    └── java/com/itsaky/androidide/compose/preview/
        ├── ComposePreviewActivity.kt        // 入口 Activity (XML-based 旧 UI)
        ├── ComposePreviewFragment.kt        // Fragment 版入口 (ComposeView based)
        ├── ComposePreviewViewModel.kt       // 状态机 (StateFlow)
        ├── PreviewConfig.kt + PreviewState.kt
        ├── compiler/
        │   ├── AssetsComposeBundles.kt      // 资产自包含 SDK (解压 + 校验)
        │   ├── BundledComposeCompiler.kt    // 进程内 K2JVMCompiler
        │   ├── BundledD8Dexer.kt            // 进程内 D8
        │   ├── CompileModels.kt             // CompileResult / DexResult / Diagnostic
        │   └── DexCache.kt                  // 源 hash -> dex 缓存
        ├── data/
        │   ├── source/ProjectContextSource.kt
        │   └── repository/ComposePreviewRepository(Impl).kt
        ├── domain/
        │   ├── PreviewSourceParser.kt       // 正则解析 @Preview
        │   └── model/ParsedPreviewSource.kt
        ├── runtime/
        │   ├── ComposeClassLoader.kt        // DexClassLoader 池化
        │   ├── ComposableRenderer.kt        // 反射 + MethodHandle 渲染
        │   └── MethodHandleResolver.kt      // 反射 -> MethodHandle 缓存
        └── ui/
            ├── BoundedComposeView.kt        // ComposeView 容器
            ├── ComponentInspector.kt        // 组件检查器 (UI 骨架)
            ├── DeviceFrame.kt               // 设备外壳 (圆角 / 刘海 / 状态栏)
            ├── DeviceProfile.kt             // 设备配置 + 内置 Pixel/Tablet/Watch
            ├── DeviceProfileSheet.kt        // 设备选择 Sheet
            ├── RecompositionCounter.kt      // recompose 计数
            ├── ResolutionEditor.kt          // 自定义分辨率
            ├── ThemeSelector.kt             // Light / Dark / Custom
            └── ZoomController.kt            // 缩放 + pan
```

### 1.2 已观测到的故障模式（v2 已经修了大部分，但需补全）

| 现象 | v2 处理 | v2.1 还要做 |
| --- | --- | --- |
| 编译无限循环 / 守护进程死锁 | 已删除 `CompilerDaemon`，K2 进程内调用 | — |
| `ComposeClasspathManager` 引用 IDE 私有路径 | 删除，由 `AssetsComposeBundles` 替代 | — |
| D8 强依赖 SDK build-tools | 改用 R8 fat jar 自带 D8 入口 | — |
| `.m2` 强依赖 | 全部资产化 | — |
| 反射渲染慢 | `MethodHandle` 替代 `Method.invoke` | **P2：ASM 字节码改写，跳过反射** |
| 设备模拟太简化（只有圆角 + 简单刘海） | — | **v2.1 P0：真实设备 + 针孔 / 瀑布 / 折叠** |
| 状态栏是单条黑色 Canvas | — | **v2.1 P0：可换主题的 SystemBar（电池 / 信号 / 时钟 / 通知）** |
| 无可视化编辑工具箱 | — | **v2.1 P0：拖动 / Resize / 颜色拾取 / 实时 padding / gap** |
| 缩放 / Pan / 适配 未接入 Activity | — | **v2.1 P0：完整接入 ComposePreviewActivity** |
| 预览解析只识别 `@Preview` height/width | — | **v2.1 P1：支持 `@PreviewParameter` / `@PreviewFontScale` / `@PreviewLightDark`** |
| 反射 / MethodHandle 仍有少量 boxing | — | **v2.1 P2：ASM 生成 binder，零反射** |
| 性能埋点 | 仅有 `LOG.info` | **v2.1 P3：结构化阶段耗时 + Trace** |
| `ComposePreviewActivity` 与 `ComposePreviewFragment` 两套入口 | 同时存在 | **v2.1 P1：统一为 Activity + 内嵌 Compose UI 层** |

### 1.3 核心结论

> **v2 已经把构建链从「不可用」变成「可用」，但 v2.1 要把它从「可用」变成「对标 AS 的生产力工具」**：
>
> 1. 设备模拟必须真实到能看出 Pixel 7 Pro / 华为 Mate 60 Pro / Galaxy Z Fold 5 的区别
> 2. 字节码层要 ASM 加速，避免反射热路径
> 3. 可视化编辑必须支持拖动、resize、color picker 等「所见即所得」能力

---

## 2. 目标与非目标

### 2.1 目标

#### 2.1.1 构建

1. **构建阶段零外部依赖**：Kotlin 编译器、Compose 插件、D8 全部来自 `assets/compose/compose-jars.zip`。
2. **首次构建 < 8s、增量构建 < 2s**：源码 hash 命中缓存时立即返回。
3. **无守护进程**：每次编译独立起 `URLClassLoader`，编译完即销毁。
4. **可取消**：`compiler.cancel()` 在 K2 分析阶段前响应。

#### 2.1.2 真实设备模拟（v2.1 重点）

1. **支持以下形态（每种至少 2 个 Profile）**：
   - **传统 16:9 / 19.5:9 / 20:9 / 21:9 全面屏手机**（Pixel 4/5/6/7/8、华为 P40 / Mate 60 Pro、小米 14 Pro）
   - **刘海屏**（iPhone 13/14/15 风格，刘海深度 30dp / 35dp / 47dp）
   - **针孔屏 / 挖孔屏**（居中 / 左上角 / 右上角，孔径 4dp~6dp）
   - **瀑布屏**（华为 Mate 30 Pro / 荣耀 Magic，88° 曲边 + 侧边显示区域）
   - **折叠屏**（Galaxy Z Fold 5 内屏 + 外屏；Pixel Fold；OPPO Find N 外屏 / 内屏）
   - **平板**（Pixel Tablet 11"、iPad Pro 12.9"）
   - **桌面 / 自由窗口**（Android 14 桌面模式）
   - **Wear OS**（小 / 大 圆表 / 方形表）
2. **设备外壳 (Hardware Chassis)**：
   - 上 / 下 / 左 / 右 边框宽度（按真实手机测量）
   - 圆角（按设备）
   - 物理按键位置（电源 / 音量 ±，按设备）
   - 摄像头凸起（部分机型）
   - 颜色（钛黑 / 雪白 / 银 / 暗紫等）
3. **系统状态栏模拟**：
   - 时间（跟随系统时区）
   - 信号 / Wi-Fi / 电池 图标
   - 通知小红点
   - 浅色 / 深色 / 透明 三种模式
   - 状态栏高度（按设备不同：24dp~50dp）

#### 2.1.3 可视化编辑工具箱

1. 拖动 Composable
2. Resize Composable（角 / 边控制点）
3. 实时 padding / gap 修改
4. 颜色拾取（截图采样像素）
5. Layout Bounds 显示
6. 缩放 / Fit / 100% / 双击切换

#### 2.1.4 调试

1. Recomposition 计数 + 高亮
2. Component Inspector（边界框 + 属性面板）
3. Logcat 面板（拦截 `println` / `Log.d`）
4. 性能埋点（编译 / 编译耗时统计）

#### 2.1.5 字节码加速（v2.1 P2）

1. **ASM 字节码改写**：在 dex 落地之前，对 K2 产物做轻量级变换
2. **生成静态 binder**：把 `@Composable fun Foo(...)` 包成 `staticInvoke$Foo(c, c0, 0)`，反射直接命中
3. **dead code strip**：去掉 `@Preview` 之外的非引用代码
4. **常量折叠**：UI 默认值可在编译期折叠
5. **DexLayoutOptimizer**：调整 dex method order 提升 ICache 命中

### 2.2 非目标

- 不实现完整 IDEA Live Edit / Hot Reload
- 不集成 ProGuard / R8 优化（v2.1 之后再说）
- 不支持 multi-module 项目的跨模块实时 preview（保留 `useGradleDex` 兜底）

---

## 3. v2 现状评估（已落地但需补全的部分）

### 3.1 已经工作的（v2 落地清单）

| 模块 | 现状 | 评价 |
| --- | --- | --- |
| `AssetsComposeBundles` | assets 自包含解压 + SHA-256 校验 | ✅ 可用 |
| `BundledComposeCompiler` | 进程内 K2JVMCompiler 反射调用 | ✅ 可用，但反射路径有性能损耗 |
| `BundledD8Dexer` | fork 子进程跑 R8 内置 D8 | ✅ 可用 |
| `DexCache` | 源 hash -> dex + SDK version 校验 | ✅ 可用 |
| `ComposeClassLoader` | DexClassLoader 池化 | ✅ 可用 |
| `MethodHandleResolver` | 反射 -> MethodHandle 缓存 | ✅ 可用，但首次反射查找仍 cold |
| `DeviceProfile` + `DeviceFrame` | 圆角 / 简单刘海 / 简单状态栏 | ⚠️ 太简陋，不算「真实设备」 |
| `ZoomController` | pinch / pan | ⚠️ 已实现但未接入 Activity |
| `DeviceProfileSheet` / `ResolutionEditor` / `ThemeSelector` | 底部弹窗 / Dialog / Chip | ⚠️ UI 在但未在 Activity 调用 |
| `ComposePreviewActivity` | XML + 旧 View 体系 | ⚠️ UI 集成不完整：没有设备框、没有工具栏 chip、没有调试面板 |
| `ComposePreviewFragment` | ComposeView 容器 | ⚠️ 存在但与 Activity 重叠 |
| `RecompositionCounter` | remember + counter | ⚠️ 单文件，未挂到 RenderComposable |
| `ComponentInspector` | 选中状态 + 占位 UI | ⚠️ 没有真的反射读 `LayoutNode` |

### 3.2 v2.1 要补的核心空白

1. **`ComposePreviewActivity` 的 UI 升级**：从 XML View 切到 Compose 层；顶栏放设备 / 主题 / 缩放 / 调试 chip；中间用 `DeviceFrame` 套住 `ComposeView`；底部放 Inspector / Logcat 抽屉
2. **真实设备数据**：把 `DeviceProfile` 扩到含 bezels、cutout 几何、physical key 位置、机身色
3. **系统状态栏 Composable**：用 `Canvas` 画图标 + 时间，支持 light/dark/translucent
4. **Cutout 几何系统**：用 `CutoutGeometry` sealed class 描述：刘海 / 针孔 / 瀑布 / 圆角盲区
5. **Foldable 铰链模拟**：中间画一条 4dp 阴影 + 60dp 的「折叠状态」状态条
6. **ASM 工具栈**：在 dex 阶段前加一道 pass，输出 `*_optimized.dex`
7. **统一 Bind 入口**：让 `ComposableRenderer` 不再 `MethodHandle.invokeWithArguments(...)`，而是从生成代码里直接调
8. **可观测性**：每次 compile/render 写一行结构化日志（含阶段耗时）

---

## 4. v2.1 关键增量

### 4.1 新增 / 重写文件

```
ui/
├── DeviceFrame.kt             # 重写: 真实设备 + cutout 几何
├── DeviceProfile.kt           # 重写: 含 bezel / cutout / 机身色 / 物理键
├── DeviceProfileSheet.kt      # 重写: 分组 (Phone / Foldable / Tablet / Watch)
├── SystemBarsOverlay.kt       # 新增: 状态栏 + 导航栏
├── CutoutOverlay.kt           # 新增: 刘海 / 针孔 / 瀑布曲线
├── FoldableHingeOverlay.kt    # 新增: 折叠屏铰链
├── ResolutionEditor.kt        # 保留
├── ThemeSelector.kt           # 保留
├── ZoomController.kt          # 保留
├── RecompositionCounter.kt    # 增强: 真实挂到 RenderComposable
├── ComponentInspector.kt      # 重写: 反射读 LayoutNode
├── PreviewToolbar.kt          # 新增: 顶栏 Compose 工具栏
├── DebugDrawer.kt             # 新增: 底部抽屉
├── ColorPickerOverlay.kt      # 新增: 颜色拾取
├── LayoutBoundsOverlay.kt     # 新增: 布局边界
├── LogcatPanel.kt             # 新增: 日志面板
├── PreviewGridLayout.kt       # 新增: 多 Preview 网格
├── ModifierInspector.kt       # 新增: Modifier 链分析
└── PreviewContextMenu.kt      # 新增: 右键菜单
```

### 4.2 新增 Compiler / Runtime

```
compiler/
├── BundledComposeCompiler.kt      # 增强: 收集真实 K2 diagnostic
├── BundledD8Dexer.kt              # 增强: 输出 dex 字节码
├── AsmComposeBinder.kt            # 新增: ASM 字节码改写
├── ComposeRuntimePrewarm.kt       # 新增: 预热 compose runtime
├── DexSharedLibraryLoader.kt      # 新增: dex mmap 共享
├── CompileModels.kt               # 增强: BuildPhaseTimings
└── BytecodeCache.kt               # 新增: ASM 产物缓存

runtime/
├── ComposeClassLoader.kt          # 增强: mmap + 共享 dex
├── ComposableRenderer.kt          # 增强: 优先用 ASM 生成的 binder
├── MethodHandleResolver.kt        # 保留 (作为 fallback)
└── AsmBinderInvoker.kt            # 新增: 调用 ASM 生成 binder
```

### 4.3 新增数据 / 资源

```
data/
├── device/DeviceCatalog.kt        # 新增: 真实设备数据集 (内置 30+)
├── device/CutoutGeometry.kt       # 新增: cutout 几何 sealed
└── device/PhysicalKey.kt          # 新增: 物理键位置

res/values/
├── device_profiles.xml            # 新增: 设备 Profile 列表
└── cutout_geometries.xml          # 新增: cutout 形状配置
```

---

## 5. 新架构（v2.1 全景）

```
┌─────────────────────────────────────────────────────────────────────┐
│  UI 层                                                               │
│    ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                │
│    │ PreviewToolbar│ │DeviceProfile │ │DebugDrawer   │                │
│    │  (设备/主题/  │ │  Sheet       │ │ (Inspector/  │                │
│    │  缩放/调试)  │ │              │ │  Logcat)     │                │
│    └──────────────┘ └──────────────┘ └──────────────┘                │
│    ┌──────────────────────────────────────────────────┐             │
│    │   ZoomablePane                                   │             │
│    │     └── DeviceFrame (Bezels + Cutout + SystemBars)│            │
│    │           └── RenderTarget (ComposeView)         │             │
│    │                 └── MaterialTheme { content }    │             │
│    └──────────────────────────────────────────────────┘             │
├─────────────────────────────────────────────────────────────────────┤
│  ViewModel 层 (ComposePreviewViewModel)                             │
│    ├── PreviewState: Idle/Compiling/Ready/Error                     │
│    ├── DeviceConfig (设备 + cutout + 状态栏)                          │
│    ├── PreviewTheme (Light/Dark/Custom)                            │
│    ├── DebugState (Inspector on / Recompose on / Logcat)            │
│    ├── EditState (drag / resize / color pick)                       │
│    └── ViewportState (zoom / pan / fit)                             │
├─────────────────────────────────────────────────────────────────────┤
│  Repository 层 (ComposePreviewRepository)                           │
│    └── compile(source) -> CompilationResult (含 timings)            │
├─────────────────────────────────────────────────────────────────────┤
│  Build Phase                                                         │
│    ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐       │
│    │ AssetsBundles   │ │BundledCompiler  │ │BundledD8Dexer   │       │
│    │ (解压/校验)     │ │ (K2JVMCompiler) │ │ (D8)            │       │
│    └─────────────────┘ └─────────────────┘ └─────────────────┘       │
│                  ┌─────────────────────────────┐                    │
│                  │ AsmComposeBinder (新)        │                    │
│                  │  - 生成静态 binder            │                    │
│                  │  - dead code strip            │                    │
│                  │  - dex layout optimize        │                    │
│                  └─────────────────────────────┘                    │
├─────────────────────────────────────────────────────────────────────┤
│  Runtime 层                                                          │
│    ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐       │
│    │ ComposeLoader   │ │PrewarmService   │ │AsmBinderInvoker │       │
│    │ (mmap+共享dex) │ │ (Class.forName)  │ │ (binder.invoke) │       │
│    └─────────────────┘ └─────────────────┘ └─────────────────┘       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6. 真实设备模拟子系统

### 6.1 DeviceProfile v2.1 字段

```kotlin
data class DeviceProfile(
    val id: String,                       // "pixel-7-pro", "huawei-mate60-pro"
    val manufacturer: String,             // "Google", "Huawei", "Samsung"
    val model: String,                    // "Pixel 7 Pro"
    val osVersion: String,                // "Android 14"
    val formFactor: FormFactor,           // PHONE / FOLDABLE_INNER / FOLDABLE_OUTER / TABLET / DESKTOP / WATCH
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val screenGeometry: ScreenGeometry,   // 屏幕形状 (见 6.2)
    val cutout: CutoutGeometry?,          // 刘海 / 针孔 / 瀑布 (nullable)
    val bezels: Bezels,                   // 上 / 下 / 左 / 右 边框
    val chassisColor: Color,              // 机身颜色
    val physicalKeys: List<PhysicalKey>,  // 电源 / 音量
    val statusBarHeightDp: Int,
    val navigationBarHeightDp: Int,
    val isRound: Boolean,                 // 手表专用
    val hasNotch: Boolean = false,
    val hasPunchHole: Boolean = false,
    val hasWaterfall: Boolean = false,
    val isFoldable: Boolean = false,
    val isCustom: Boolean = false,
)
```

### 6.2 ScreenGeometry / CutoutGeometry

```kotlin
sealed class ScreenGeometry {
    data class Rectangular(val cornerRadiusPx: Float) : ScreenGeometry()
    data class Waterfall(val sideAngleDeg: Float, val edgeWidthPx: Float) : ScreenGeometry()
    data class FoldedInner(val hingeWidthPx: Float) : ScreenGeometry()
    data class Round(val diameterPx: Float) : ScreenGeometry()
}

sealed class CutoutGeometry {
    abstract val widthPx: Float
    abstract val heightPx: Float
    abstract val anchor: Anchor

    enum class Anchor { TOP_CENTER, TOP_LEFT, TOP_RIGHT, LEFT_CENTER, RIGHT_CENTER }

    data class Notch(override val widthPx: Float, override val heightPx: Float,
                     override val anchor: Anchor) : CutoutGeometry()
    data class PunchHole(override val widthPx: Float, override val heightPx: Float,
                         override val anchor: Anchor) : CutoutGeometry()
    data class WaterfallCurve(val sideAngleDeg: Float, val edgeCurvePx: Float) : CutoutGeometry() {
        override val widthPx = 0f
        override val heightPx = 0f
        override val anchor = Anchor.LEFT_CENTER
    }
}
```

### 6.3 内置设备清单（v2.1 必交付）

| 分组 | ID | 名称 | 关键属性 |
| --- | --- | --- | --- |
| Phone | `pixel-7` | Pixel 7 | 1080×2400@420, PunchHole 居中 4dp |
| Phone | `pixel-7-pro` | Pixel 7 Pro | 1440×3120@560, PunchHole 居中 |
| Phone | `pixel-8-pro` | Pixel 8 Pro | 1344×2992@560, PunchHole 居中 |
| Phone | `huawei-mate60-pro` | Mate 60 Pro | 1260×2720@460, PunchHole 居中 8dp |
| Phone | `xiaomi-14-pro` | Xiaomi 14 Pro | 1440×3200@560, PunchHole 居中 |
| Phone | `samsung-s24-ultra` | S24 Ultra | 1440×3120@560, PunchHole 居中 4dp |
| Phone | `iphone-15-pro` | iPhone 15 Pro | 1179×2556@460, Dynamic Island 126×37dp |
| Phone | `oneplus-12` | OnePlus 12 | 1440×3168@560, PunchHole 左上 4dp |
| Notch | `huawei-p30-pro` | P30 Pro | 1080×2340@480, Waterfall 88° |
| Notch | `iphone-13` | iPhone 13 | 1170×2532@460, Notch 154×30dp |
| Notch | `iphone-14` | iPhone 14 | 1170×2532@460, Notch 154×30dp |
| Foldable | `galaxy-z-fold5-inner` | Galaxy Z Fold 5 (内屏) | 1812×2176@374, 无 cutout |
| Foldable | `galaxy-z-fold5-outer` | Galaxy Z Fold 5 (外屏) | 904×2316@374, PunchHole 居中 |
| Foldable | `pixel-fold-inner` | Pixel Fold (内屏) | 2208×1840@420, 无 cutout |
| Foldable | `pixel-fold-outer` | Pixel Fold (外屏) | 1080×2092@420, PunchHole 边 |
| Tablet | `pixel-tablet` | Pixel Tablet | 1600×2560@320 |
| Tablet | `ipad-pro-12.9` | iPad Pro 12.9" | 2048×2732@264 |
| Watch | `wear-os-small` | Wear OS Small | 384×384@320, Round |
| Watch | `wear-os-large` | Wear OS Large | 454×454@320, Round |
| Custom | `custom` | Custom | 由 ResolutionEditor 创建 |

### 6.4 DeviceFrame 渲染算法

```
render(profile, content):
  1) Outer Box:
     - size = outerWidth × outerHeight
     - shape = profile.screenGeometry.shape  // 圆角 / 瀑布曲线
     - background = profile.chassisColor

  2) Inner Box (屏幕区):
     - size = profile.widthPx × profile.heightPx  (dp)
     - clip = profile.screenGeometry  // 屏幕形状 (瀑布/折叠单独处理)
     - 内部: Box(content)

  3) SystemBarsOverlay:
     - 状态栏: 高度 = profile.statusBarHeightDp, 顶部对齐
     - 导航栏: 高度 = profile.navigationBarHeightDp, 底部对齐
     - 主题: Light / Dark / Translucent (来自 ViewModel.theme)

  4) CutoutOverlay (可选):
     - 根据 profile.cutout 类型:
       - Notch: 顶部矩形 (RoundedCorner)
       - PunchHole: 圆形 (anchor 决定位置)
       - WaterfallCurve: 两侧 Path 裁切

  5) PhysicalKeys (可选):
     - 右侧矩形 (电源) / 左侧矩形 (音量 ±)

  6) FoldableHingeOverlay (foldable):
     - 中间阴影 / 折痕
```

### 6.5 关键 Composable API

```kotlin
@Composable
fun DeviceFrame(
    profile: DeviceProfile,
    modifier: Modifier = Modifier,
    systemBarsTheme: SystemBarsTheme = SystemBarsTheme.Auto,
    showStatusBar: Boolean = true,
    showNavigationBar: Boolean = true,
    showCutout: Boolean = true,
    showChassis: Boolean = true,
    content: @Composable () -> Unit
)

@Composable
fun SystemBarsOverlay(
    profile: DeviceProfile,
    theme: SystemBarsTheme,    // Light / Dark / Translucent / Auto
    modifier: Modifier = Modifier,
    customClock: String? = null,
    batteryPercent: Int? = null,
)

@Composable
fun CutoutOverlay(
    cutout: CutoutGeometry,
    modifier: Modifier = Modifier,
)

@Composable
fun FoldableHingeOverlay(
    hingeWidthPx: Float,
    modifier: Modifier = Modifier,
)
```

---

## 7. 系统状态栏子系统

### 7.1 主题

```kotlin
enum class SystemBarsTheme {
    AUTO,    // 跟随 Composable 主题
    LIGHT,   // 深色图标 / 浅色背景
    DARK,    // 浅色图标 / 深色背景
    TRANSLUCENT_LIGHT,
    TRANSLUCENT_DARK,
}
```

### 7.2 状态栏内容

- **时间**：从系统 ClockProvider 读，10s 刷新
- **电池**：可选图标 + 百分比
- **Wi-Fi / 信号**：默认 4 格满
- **通知小红点**：默认显示
- **高度**：`profile.statusBarHeightDp` (24~50dp)

### 7.3 导航栏

- 高度：`profile.navigationBarHeightDp` (44~48dp)
- 内容：返回 / Home / 最近
- 手势导航：底部一条横杠

### 7.4 持久化

用户选择的 `SystemBarsTheme` 写入 SharedPreferences（key = `preview.systemBarsTheme`）。

---

## 8. 字节码加速子系统（ASM）

### 8.1 设计动机

反射 + `MethodHandle.invokeWithArguments(...)` 每次调用：
- 创建 `Object[]` 数组（args）
- `MethodHandle` 拆箱 / 装箱
- 找不到 JIT 稳定的 inline 点

AS 的解决方案是 **静态 binder**：
- 在 dex 阶段前，对每个 `@Composable fun Foo(...)` 生成一个 `staticInvoke$Foo(composer, changedFlags)` 包装类
- 运行时直接 `StaticInvoke$Foo.invoke(c, 0)`，没有反射、没有数组分配

### 8.2 实现思路

#### 8.2.1 输入

- K2 编译产出的 `.class` 文件（在 `classesDir`）

#### 8.2.2 ASM pass

使用 `org.ow2.asm:asm:9.7` + `asm-commons`，对每个 class 做：

**Pass 1: 找到 `@Composable fun Foo(...)`**
- 扫描 `Lcom/example/PreviewKt;` 的所有 method
- 找到带 `@Composable` 注解 + 含 `Composer` 形参的方法
- 记录 `(className, methodName, descriptor)` 到 `BinderRegistry`

**Pass 2: 生成 binder 类**
- 对每个注册的方法，在 `binder/` 子包下生成 `Binder$Foo$XXX.class`
- 字段: `private static final MethodHandle HANDLE = MethodHandles.lookup().unreflect(...)`
- 方法: `public static void invoke(Composer c, int changed) { HANDLE.invokeWithArguments(c, changed); }`

**Pass 3: dead code strip**
- 删除未引用的 method (除了 `<init>` / binder)
- 简化字符串常量

**Pass 4: dex 落地**
- D8 把 binder 一起 dex
- 产物: `preview_xxx.dex` + `binders.dex`

#### 8.2.3 运行时

`AsmBinderInvoker` 优先于 `MethodHandle`：
- 拿到 `Binder$Foo$XXX.class` 后用 `MethodHandle` 调一次（缓存）
- 之后所有 render 直接走 binder

### 8.3 BytecodeCache

```
cache/binders/<sourceHash>/
  ├── MyComposable.class          # 原始 .class
  ├── Binder$MyComposable.class   # 生成的 binder
  └── version.txt
```

缓存命中条件：`sourceHash` + `binderVersion` (随 ASM 升级失效)。

### 8.4 引入的依赖

```kotlin
// build.gradle.kts
bytecodeAcceleratorJars("org.ow2.asm:asm:9.7")
bytecodeAcceleratorJars("org.ow2.asm:asm-commons:9.7")
bytecodeAcceleratorJars("org.ow2.asm:asm-tree:9.7")
```

打包到 `assets/compose/jars/asm-*.jar`，由 `AsmComposeBinder` 加载。

### 8.5 fallback

- ASM pass 失败 -> 退回 `MethodHandle` 路径
- ASM jar 缺失 -> 跳过整个 pass，行为完全等同 v2

### 8.6 性能预期

| 阶段 | v2 (反射) | v2.1 (binder) | 提升 |
| --- | --- | --- | --- |
| 首次反射查找 | ~50ms | ~3ms (MethodHandle 缓存) | 16x |
| 每次 invoke | ~0.5ms (数组分配) | ~0.05ms (直接 invoke) | 10x |
| 100 个 Composable 渲染 | ~80ms | ~10ms | 8x |

---

## 9. Build Phase

### 9.1 时序

```
compile(source) :
  1) AssetsComposeBundles.init()           // 一次性解压到 cacheDir
  2) ComposePreviewCache.get(sourceHash)   // 命中即返回
  3) SourcePreparer.writeWorkDir()         // <workDir>/src/<pkg>/<file>.kt
  4) BundledComposeCompiler.compile()      // 进程内 K2JVMCompiler
  5) AsmComposeBinder.process()            // 字节码加速 (v2.1 新)
  6) BundledD8Dexer.dex()                  // 进程内 D8
  7) BytecodeCache.put(sourceHash, dexFile)
  8) return CompilationResult(timings)
```

### 9.2 BuildPhaseTimings

```kotlin
data class BuildPhaseTimings(
    val assetsInitMs: Long,
    val cacheLookupMs: Long,
    val sourcePrepareMs: Long,
    val k2CompileMs: Long,
    val asmTransformMs: Long,
    val d8DexMs: Long,
    val cacheWriteMs: Long,
) {
    val totalMs: Long get() = assetsInitMs + cacheLookupMs + ... + cacheWriteMs
}
```

UI 层展示在 DebugDrawer 的「Build」标签页。

### 9.3 详细参数

- **取消机制**：`AtomicBoolean cancelled`，K2 在分析阶段前检查（v2 已实现）
- **超时**：默认 60s（编译超时比 buildService 长，因为不需要 full build）
- **错误聚合**：多次失败保留最近 5 条

---

## 10. Runtime 优化

### 10.1 DexClassLoader 池化（v2 已有，v2.1 增强）

| 增强 | 说明 |
| --- | --- |
| **mmap 共享** | `compose-runtime.dex` 用 `InMemoryDexClassLoader` 共享主 app 的 classloader，不走磁盘 |
| **LRU 驱逐** | 最多 10 个 loader，超出按 LRU 驱逐 |
| **预热** | `PrewarmService` 在首启时 `Class.forName` 关键 compose 类 |

### 10.2 PrewarmService

```kotlin
class ComposeRuntimePrewarm {
    fun prewarm(classLoader: ClassLoader) {
        KEY_CLASSES.forEach { name ->
            runCatching { Class.forName(name, false, classLoader) }
        }
    }

    companion object {
        private val KEY_CLASSES = listOf(
            "androidx.compose.runtime.Composer",
            "androidx.compose.ui.node.LayoutNode",
            "androidx.compose.foundation.layout.Box",
            "androidx.compose.material3.MaterialTheme",
            // ... ~30 个
        )
    }
}
```

### 10.3 ComposableRenderer 增强

```kotlin
class ComposableRenderer(
    private val composeView: ComposeView,
    private val classLoader: ComposeClassLoader,
    private val binderInvoker: AsmBinderInvoker,    // 优先
    private val handleResolver: MethodHandleResolver // fallback
) {
    fun render(dexFile: File, className: String, functionName: String) {
        val binder = binderInvoker.tryGetBinder(dexFile, className, functionName)
        if (binder != null) {
            composeView.setContent { RenderWithBinder(binder) }
        } else {
            // fallback to MethodHandle
            ...
        }
    }
}
```

---

## 11. 可视化编辑工具箱

### 11.1 拖动 / Resize

```kotlin
@Composable
fun Modifier.editable(
    state: EditState,
    onBoundsChange: (Rect) -> Unit,
): Modifier = this.pointerInput(Unit) {
    detectDragGestures(
        onDragStart = { state.dragStart(it) },
        onDrag = { _, delta -> state.drag(delta) },
        onDragEnd = { state.dragEnd(onBoundsChange) }
    )
}
```

v2.1 P0 只做基础版（单 Composable 整体拖动），P1 加 8 控制点 resize。

### 11.2 颜色拾取

```kotlin
@Composable
fun ColorPickerOverlay(
    composableBounds: Rect,
    onPick: (Color) -> Unit,
)
```

实现：
1. 拿到 `composableBounds` 的 `ComposeView` 截图
2. 用户点击位置 -> 计算像素
3. 弹出 Popup 显示 HEX / RGB

### 11.3 Layout Bounds

```kotlin
@Composable
fun LayoutBoundsOverlay(
    visible: Boolean,
    color: Color = Color.Red,
)
```

实现：
- 在每个 Composable 节点周围画 1dp 矩形
- 通过 `Modifier.drawWithContent` + `onGloballyPositioned`

---

## 12. 调试与可观测性

### 12.1 RecompositionCounter 真实挂载

```kotlin
@Composable
fun MyComposable() {
    val counter = rememberRecompositionCounter()
    counter.bind()    // 每次 recompose 触发
    Text("...")
}
```

UI：在 ComponentInspector 面板显示 "Recompositions: 5"。

### 12.2 ComponentInspector 反射读 LayoutNode

```kotlin
class LayoutNodeInspector {
    fun inspect(layoutNode: Any): NodeInfo {
        val coords = readField(layoutNode, "coordinates") as Any
        val bounds = readField(coords, "bounds") as Rect
        val width = readField(layoutNode, "width") as Int
        val height = readField(layoutNode, "height") as Int
        return NodeInfo(bounds, width, height, ...)
    }
}
```

注意：`LayoutNode` 的字段是 internal/private，必须用 `kotlin-reflect` 或 `sun.misc.Unsafe`。

### 12.3 LogcatPanel

```kotlin
class PreviewLogcat {
    private val logs = mutableStateListOf<LogEntry>()

    fun interceptPrintln(msg: String) {
        logs.add(LogEntry("println", msg, System.currentTimeMillis()))
    }
}
```

实现：在 `RenderComposable` 之前 `System.setOut(PreviewPrintStream)`，捕获 `println`。

### 12.4 性能埋点

- `viewModel.compilePreview(source, ...)` 起停计时，写入 `BuildPhaseTimings`
- 每次 `render()` 计时
- DebugDrawer 的 "Stats" 标签页显示

---

## 13. 新功能矩阵

| 功能 | 入口 | 数据源 | v2.1 优先级 |
| --- | --- | --- | --- |
| 真实设备模拟 (Phone/Pixel) | `DeviceFrame(profile=Pixel_7)` | 内置 | P0 |
| 真实设备模拟 (Foldable) | `DeviceFrame(profile=ZFold5_Inner)` | 内置 | P0 |
| 真实设备模拟 (Waterfall) | `DeviceFrame(profile=P30_Pro)` | 内置 | P0 |
| 真实设备模拟 (Notch) | `DeviceFrame(profile=iPhone_14)` | 内置 | P0 |
| 真实设备模拟 (PunchHole) | `DeviceFrame(profile=Mate60_Pro)` | 内置 | P0 |
| 真实设备模拟 (Watch) | `DeviceFrame(profile=Wear_Small)` | 内置 | P1 |
| 真实设备模拟 (Tablet) | `DeviceFrame(profile=Pixel_Tablet)` | 内置 | P0 |
| 设备分组 Sheet | `DeviceProfileSheet` | 内置 | P0 |
| 自定义分辨率 | `ResolutionEditor` | 用户输入 | P0 |
| 系统状态栏 (浅 / 深 / 透) | `SystemBarsOverlay` | 主题 | P0 |
| 物理按键 (电源 / 音量) | `DeviceFrame` | profile | P1 |
| 折叠铰链 | `FoldableHingeOverlay` | profile | P1 |
| 缩放 (pinch / Ctrl+Wheel / Fit) | `ZoomController` | 手势 | P0 |
| 主题切换 (Light/Dark/Custom) | `ThemeSelector` | 固定 | P0 |
| 多 Preview 网格 | `PreviewGridLayout` | 解析 | P0 |
| 拖动 Composable | `Modifier.editable` | 手势 | P1 |
| Resize 8 控制点 | `Modifier.resizable` | 手势 | P1 |
| 颜色拾取 | `ColorPickerOverlay` | 截图 | P1 |
| Layout Bounds | `LayoutBoundsOverlay` | LayoutNode | P1 |
| Recomposition 计数 | `RecompositionCounter` | side-effect | P1 |
| Component Inspector | `ComponentInspector` | 反射 | P1 |
| Logcat 面板 | `LogcatPanel` | println | P1 |
| ASM 字节码加速 | `AsmComposeBinder` | 反射 | P2 |
| Prewarm | `ComposeRuntimePrewarm` | Class.forName | P2 |
| DEX mmap 共享 | `DexSharedLibraryLoader` | InMemoryDexClassLoader | P2 |
| 性能埋点 | `BuildPhaseTimings` | timing | P2 |
| `@PreviewParameter` 支持 | `PreviewSourceParser` | 正则 | P0 |
| `@PreviewFontScale` 支持 | `PreviewSourceParser` | 正则 | P1 |
| `@PreviewLightDark` 支持 | `PreviewSourceParser` | 正则 | P1 |
| LiveLiterals 实验 | `LiveLiteralsService` | K2 AST | P3 |
| Hot Reload (incremental) | — | 文件 watch | P3 |

---

## 14. 详细文件改动表

| 文件 | 动作 | v2.1 范围 |
| --- | --- | --- |
| `build.gradle.kts` | 修改 | 增加 `bytecodeAcceleratorJars` (ASM)；`assets/compose/jars/asm-*.jar` |
| `assets/compose/compose-jars.zip` | 扩展 | 包含 asm-9.7.jar / asm-commons / asm-tree |
| `compiler/AssetsComposeBundles.kt` | 增强 | 暴露 `asmJars: List<File>` |
| `compiler/BundledComposeCompiler.kt` | 增强 | 真实收集 K2 diagnostic；返回 `BuildPhaseTimings` |
| `compiler/BundledD8Dexer.kt` | 增强 | 支持 binder 目录作为输入 |
| `compiler/AsmComposeBinder.kt` | **新增** | ASM pass：binder 生成 / dead code strip |
| `compiler/BytecodeCache.kt` | **新增** | binder + 改写后 class 缓存 |
| `compiler/CompileModels.kt` | 增强 | `BuildPhaseTimings` 数据类 |
| `compiler/ComposeRuntimePrewarm.kt` | **新增** | 预热关键 class |
| `compiler/DexCache.kt` | 保留 | 加 `binderVersion` 失效条件 |
| `data/repository/ComposePreviewRepositoryImpl.kt` | 重写 | 引入 `AsmComposeBinder`；降级路径不变 |
| `data/source/ProjectContextSource.kt` | 保留 | — |
| `data/device/DeviceCatalog.kt` | **新增** | 30+ 真实设备 profile |
| `data/device/CutoutGeometry.kt` | **新增** | sealed class 描述 cutout |
| `data/device/PhysicalKey.kt` | **新增** | 物理键 sealed |
| `domain/PreviewSourceParser.kt` | 增强 | `@PreviewParameter` / `@PreviewFontScale` / `@PreviewLightDark` |
| `domain/model/ParsedPreviewSource.kt` | 增强 | 加 `fontScale` / `isLightDark` / `parameterProvider` |
| `runtime/ComposeClassLoader.kt` | 增强 | mmap + LRU + prewarm hook |
| `runtime/ComposableRenderer.kt` | 增强 | 优先 `AsmBinderInvoker`；fallback `MethodHandle` |
| `runtime/MethodHandleResolver.kt` | 保留 | fallback |
| `runtime/AsmBinderInvoker.kt` | **新增** | 调用 binder 静态方法 |
| `ui/BoundedComposeView.kt` | 增强 | 暴露 `editableBounds: Rect` |
| `ui/DeviceFrame.kt` | **重写** | 真实设备外壳 + cutout 几何 |
| `ui/DeviceProfile.kt` | **重写** | 完整字段 (bezel / chassisColor / physicalKeys / formFactor) |
| `ui/DeviceProfileSheet.kt` | **重写** | 分组 + 真实缩略图 |
| `ui/SystemBarsOverlay.kt` | **新增** | 状态栏 + 导航栏 + 时钟 |
| `ui/CutoutOverlay.kt` | **新增** | Notch / PunchHole / Waterfall 渲染 |
| `ui/FoldableHingeOverlay.kt` | **新增** | 折叠屏铰链阴影 |
| `ui/ResolutionEditor.kt` | 增强 | 支持 cutout 选择 |
| `ui/ThemeSelector.kt` | 保留 | — |
| `ui/ZoomController.kt` | 增强 | Ctrl+Wheel / 双击 Fit |
| `ui/RecompositionCounter.kt` | 增强 | 真实挂载到 RenderComposable |
| `ui/ComponentInspector.kt` | **重写** | 反射读 LayoutNode |
| `ui/ColorPickerOverlay.kt` | **新增** | 颜色拾取 |
| `ui/LayoutBoundsOverlay.kt` | **新增** | 布局边界 |
| `ui/LogcatPanel.kt` | **新增** | 日志面板 |
| `ui/PreviewGridLayout.kt` | **新增** | AS 风格多 Preview 网格 |
| `ui/PreviewToolbar.kt` | **新增** | 顶栏 Compose 工具栏 |
| `ui/DebugDrawer.kt` | **新增** | 底部抽屉 (Inspector / Logcat / Stats) |
| `ui/ModifierInspector.kt` | **新增** | Modifier 链分析 |
| `ui/PreviewContextMenu.kt` | **新增** | 右键菜单 |
| `ComposePreviewActivity.kt` | **重写** | 全 Compose UI；顶栏 + 设备框 + 抽屉 |
| `ComposePreviewFragment.kt` | 弃用 | 保留为兼容入口 |
| `ComposePreviewViewModel.kt` | **重写** | 完整 StateFlow 状态 (设备/主题/调试/编辑) |
| `PreviewConfig.kt` | 增强 | 加 `fontScale` / `parameterProviderName` |
| `PreviewState.kt` | 增强 | 加 `DeviceConfig` / `DebugState` |
| `DESIGN.md` | **重写** | 本文件 |
| `TODO.md` | **重写** | 任务清单 |
| `res/values/device_profiles.xml` | **新增** | 设备列表 |
| `res/values/strings_preview.xml` | **新增** | 工具栏 / 抽屉 文字 |
| `res/drawable/ic_*` | 增强 | 加设备 / cutout / 工具栏图标 |

---

## 15. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| K2 进程内调用阻塞主线程 | 严格 `Dispatchers.IO`；`viewModelScope` 取消传播 |
| D8 跨 Android API 兼容 | D8 bytecode 8+；API < 26 走 system d8 fallback |
| Compose Compiler / runtime 版本不匹配 | 锁定 `compiler 1.5.10 ↔ runtime 1.6.0`；启动校验 `compose-compiler-plugin.jar` META-INF |
| `@PreviewParameter` 类型不能反射实例化 | 仅支持无参构造的 Provider；其他显式提示 |
| 跨 ABI / Gradle 产物差异 | 不重用跨项目的 dex；仅本预览缓存 |
| ASM 改写 class 失败 | 完整 try/catch；失败时 fallback 到 v2 行为 |
| `LayoutNode` 私有字段随 Compose 版本变动 | 加 try/catch 反射；任何字段读不到就降级为公开 API |
| 真实设备数据不准确 | 数据源注明 (Wikipedia / gsmarena / GSMArena 公开数据) |
| Foldable 铰链模拟与实际差异 | 简化为「中间阴影 + 折痕线」，明确标注「视觉示意，非 1:1」 |
| 状态栏图标粗糙 | 矢量 + Unicode 字符组合；明确标注「非真实系统栏」 |
| 折叠屏内屏外屏切换 | v2.1 P0 提供两个独立 profile；自动切换（P1） |
| 多 Preview 渲染卡顿 | P0 只渲染当前可见的；viewModel 维持 pool |
| DEX mmap 共享在 API < 26 不可用 | 用 `PathClassLoader` 替代；记录 warning |

---

## 16. 与 IDEA / AS 对标

| 维度 | IDEA / AS | 本模块 v2.1 |
| --- | --- | --- |
| 编译后端 | IDE 内 K2 + Daemon | **进程内 K2JVMCompiler，无守护** |
| Dex 后端 | IDE 内 D8 | **进程内 D8（assets 携带）** |
| ASM 加速 | Live Edit 用 IR | **binder 生成** |
| 设备模拟 | 模板 | **真实设备 (30+)** |
| 针孔屏 | ✓ | **✓**（Mate60 / S24 / 小米 14） |
| 刘海屏 | ✓ | **✓**（iPhone 13 / 14 / 15） |
| 瀑布屏 | ✓ | **✓**（P30 Pro / 88° 曲边） |
| 折叠屏 | ✓ | **✓**（Z Fold 5 / Pixel Fold 内 + 外） |
| 状态栏 | ✓ | **✓**（Light/Dark/Translucent） |
| 物理按键 | ✗ | **✓**（v2.1 P1 简化版） |
| 分辨率自定义 | ✓ | **✓** |
| 缩放 | ✓ | **✓**（pinch + Ctrl+Wheel） |
| Recomposition 高亮 | ✓ | **基础**（计数器） |
| Component Inspector | ✓ | **基础**（LayoutNode 反射） |
| Live Edit | ✓ | ✗（P3 探索） |
| 主题切换 | ✓ | **✓** |
| 多 Preview 网格 | ✓ | **✓** |
| 颜色拾取 | ✗ | **✓**（v2.1 P1） |
| 拖动 / Resize | ✗ | **✓**（v2.1 P1） |

---

## 17. 测试 / 验收

| 阶段 | 验收 |
| --- | --- |
| 单元测试 | `AssetsComposeBundles` 解压 / 校验；`AsmComposeBinder` 生成 binder 后可调用 |
| 集成测试 | 沙箱构建机（无 `.m2`、无 SDK build-tools）冷启动 → 编译 → 渲染 |
| 设备模拟 | 至少 5 种形态在 ComposePreviewActivity 中可见（Phone/Notch/PunchHole/Waterfall/Foldable） |
| 性能 | 首次冷启动 < 8s；增量 < 2s；首帧渲染 < 500ms；binder 加速后 100 Composable < 15ms |
| 兼容性 | API 21+；arm64-v8a / armeabi-v7a / x86_64 |
| 字节码加速 | binder 命中时 `MethodHandle.invoke` 次数降为 1（缓存） |

---

## 18. PR 拆分计划

| PR | 标题 | 范围 | 依赖 |
| --- | --- | --- | --- |
| **#N（本 PR）** | `feat(compose-preview): v2.1 设计 + 真实设备模拟骨架 + Build Phase 增强` | DESIGN.md + TODO.md 重写；`DeviceFrame` / `DeviceProfile` / `SystemBarsOverlay` / `CutoutOverlay` 完整重写；`DeviceCatalog` 30+ 设备；`ComposePreviewActivity` 切换全 Compose；`ComposePreviewViewModel` 完整 StateFlow | 无 |
| #N+1 | `feat(compose-preview): DebugDrawer + ComponentInspector + RecompositionCounter` | Inspector / Recompose / Logcat / Stats 面板 | #N |
| #N+2 | `feat(compose-preview): 可视化编辑（拖动 / Resize / ColorPicker / LayoutBounds）` | Editable modifier + 8 控制点 + 截图采样 | #N+1 |
| #N+3 | `feat(compose-preview): ASM 字节码加速（binder + dead code strip）` | AsmComposeBinder + BytecodeCache + AsmBinderInvoker | #N |
| #N+4 | `feat(compose-preview): 折叠屏完整模拟（铰链 + 内 / 外屏切换）` | FoldableHingeOverlay + 自动切换 | #N+1 |
| #N+5 | `feat(compose-preview): 性能调优（prewarm + DEX mmap 共享 + 埋点）` | ComposeRuntimePrewarm + DexSharedLibraryLoader | #N+3 |
| #N+6 | `feat(compose-preview): 多 Preview 网格 + @PreviewParameter / FontScale / LightDark` | PreviewGridLayout + Parser 增强 | #N |
| #N+7 | `feat(compose-preview): LiveLiterals 实验` | LiveLiteralsService + K2 AST hook | #N+3 |

---

## 19. 引用

- [JetBrains Compose Compiler](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)
- [K2JVMCompiler 进程内调用](https://github.com/JetBrains/kotlin/blob/master/compiler/cli/src/org/jetbrains/kotlin/cli/jvm/K2JVMCompiler.kt)
- [Android Studio Compose Preview 源码（AGPL 闭源，仅参考交互）](https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/master/compose-designer/)
- [AndroidIDE 现有 compose-preview 模块](file:///workspace/modules/compose-preview/)
- [AndroidX Compose BOM](https://developer.android.com/jetpack/compose/bom/bom-mapping)
- [ASM 字节码工具栈](https://asm.ow2.io/)
- [Foldables / WindowManager Jetpack](https://developer.android.com/guide/topics/large-screens/foldables)
- [Cutout API](https://developer.android.com/reference/android/view/DisplayCutout)
- [GSMArena 设备数据库（屏幕物理参数参考）](https://www.gsmarena.com/)

---

文档维护者：AndroidIDE
最后更新：2026-06-14
