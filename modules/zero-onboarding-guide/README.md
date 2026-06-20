# zero-onboarding-guide

> 一个 Android Compose 库: 操作引导 + 指引气泡 + 高亮框选 + 操作模拟 (Dribbble 风格).

`zero-onboarding-guide` 是一个独立的 Android Library 模块, 主要用于在应用内任何位置
显示**操作引导气泡**, **高亮框选遮罩**, 以及**操作模拟动画**.

## 特性

- **偏白半透明磨砂玻璃**: 高级感 (使用 Haze 实现高斯模糊)
- **微颗粒噪点**: 真实玻璃质感 (GrainNoise 纯 Compose 实现)
- **弹性物理动画**: 高级且有创意的 Spring 动画
- **多种内置形状**: 气泡 10 种 + 高亮 8 种
- **三维度高亮系统**: `HighlightShape` × `HighlightTheme` × `HighlightAnimation` 自由组合
- **智能定位**: `BubblePlacement.Auto` 自动选空间最大方向
- **id 绑定目标**: `Modifier.onboardingBind("button_id")` — 滚动/折叠后位置自动跟踪
- **持久化**: 完成一次后永久不再提示 (基于 `guideId`)
- **形状自适应**: `HighlightShape.Auto` 根据目标宽高比自动选择 Circle / Stadium / RoundedRect
- **不改变被框选目标原样**: 使用 `BlendMode.DstOut` 切割蒙层, 露出原始 UI
- **操作模拟**: Tap / Long-press / Swipe / Drag / Scroll / Multi-touch

## 模块结构

```
modules/zero-onboarding-guide/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── kotlin/com/itsaky/androidide/onboarding/
│   │   ├── OnboardingGuide.kt              # API 门面 + DSL
│   │   ├── OnboardingController.kt         # 状态机 + 持久化集成
│   │   ├── OnboardingOverlay.kt            # 根容器 (集成三维度高亮 + Auto 定位)
│   │   ├── OnboardingTarget.kt             # id 绑定目标
│   │   ├── bubble/
│   │   │   ├── BubbleShape.kt              # 10 种内置形状
│   │   │   ├── BubbleStyle.kt              # 样式 (玻璃 / 描边 / 投影)
│   │   │   ├── BubbleContent.kt            # 标题 + 副标题 + 图标
│   │   │   └── GuideBubble.kt              # Composable
│   │   ├── highlight/
│   │   │   ├── HighlightFrame.kt           # 三维度组合 (向后兼容旧 HighlightStyle)
│   │   │   ├── HighlightShape.kt           # 8 种形状 + Auto
│   │   │   ├── HighlightTheme.kt           # 8 种主题
│   │   │   └── HighlightAnimation.kt       # 6 种动画
│   │   ├── prefs/
│   │   │   ├── OnboardingPreferences.kt    # 接口
│   │   │   └── SharedPreferencesOnboardingPreferences.kt  # 生产 + Memory 实现
│   │   ├── simulation/
│   │   │   └── TouchSimulator.kt           # 操作模拟
│   │   ├── animation/
│   │   │   └── AnimationDefaults.kt        # 动画规格
│   │   ├── effects/
│   │   │   ├── FrostedGlass.kt             # 磨砂玻璃效果
│   │   │   └── GrainNoise.kt               # 颗粒噪点
│   │   └── demo/
│   │       ├── OnboardingGuideExample.kt   # 完整示例 (展示所有特性)
│   │       └── BubbleGallery.kt            # 形状画廊
│   └── res/values/strings.xml
```

## 快速开始

### 1. 引入依赖

在 `app/build.gradle.kts` 中:

```kotlin
dependencies {
  implementation(projects.modules.zero.onboarding.guide)
}
```

### 2. 初始化持久化 (Application.onCreate)

```kotlin
class MyApp : Application() {
  override fun onCreate() {
    super.onCreate()
    val prefs = SharedPreferencesOnboardingPreferences(this)
    OnboardingPreferences.setDefault(prefs)
  }
}
```

### 3. 简单用法: 居中提示气泡

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
  YourContent()
  GuideBubble(
    content = BubbleContent(title = "欢迎使用", subtitle = "这是一个引导示例"),
    shape = BubbleShape.RoundedRectangle(),
    style = BubbleStyle.Default,
  )
}
```

### 4. 完整示例: 持久化 + id 绑定 + 三维度高亮 + 操作模拟

```kotlin
@Composable
fun OnboardingExample() {
  val context = LocalContext.current
  val prefs = remember { SharedPreferencesOnboardingPreferences(context) }

  // id 绑定的目标 (自动跟踪位置)
  val searchTarget = remember { OnboardingTarget.of("search_button") }
  val drawerTarget = remember { OnboardingTarget.of("drawer_handle") }

  val steps = listOf(
    // 步骤 1: 居中欢迎
    OnboardingStep(
      id = "welcome",
      content = BubbleContent(title = "欢迎使用 ZeroStudio!"),
      bubbleShape = BubbleShape.WideCard(),
    ),

    // 步骤 2: 搜索按钮 (Circle 形状 + 霓虹主题 + 脉冲动画)
    OnboardingStep(
      id = "search",
      content = BubbleContent(title = "点这里搜索"),
      target = searchTarget,                   // <-- id 绑定
      bubbleShape = BubbleShape.RoundedRectangle(),
      bubblePlacement = BubblePlacement.Above,
      highlightShape = HighlightShape.Circle,
      highlightTheme = HighlightTheme.Neon(color = Color.Cyan),
      highlightAnimation = HighlightAnimation.Pulse(durationMs = 1400),
    ),

    // 步骤 3: 底部抽屉 (RoundedRect + Dashed + Scan + 操作模拟)
    OnboardingStep(
      id = "drawer",
      content = BubbleContent(title = "上滑打开抽屉"),
      target = drawerTarget,
      bubbleShape = BubbleShape.SpeechBubble(cornerRadius = 20.dp),
      bubblePlacement = BubblePlacement.Above,
      highlightShape = HighlightShape.RoundedRect(12.dp),
      highlightTheme = HighlightTheme.Dashed(borderColor = Color.White),
      highlightAnimation = HighlightAnimation.Scan(durationMs = 1800),
      touchSimulator = TouchSimulator.swipe(
        fromX = 200f, fromY = 1400f,
        toX = 200f, toY = 800f,
        durationMs = 800, loop = true,
      ),
    ),
  )

  val controller = LaunchOnboarding(
    steps = steps,
    config = OnboardingConfig(
      guideId = "first_time_user",    // <-- 持久化 ID
      skipIfCompleted = true,         // <-- 已完成则跳过
    ),
  )

  Box(modifier = Modifier.fillMaxSize()) {
    // 目标控件 (id 绑定, 位置自动跟踪)
    Box(modifier = Modifier
      .align(Alignment.TopEnd).size(48.dp)
      .onboardingBind(searchTarget)   // <-- 关键
    )
    Box(modifier = Modifier
      .align(Alignment.BottomCenter)
      .onboardingBind(drawerTarget)   // <-- 关键
    )

    // 引导浮层
    OnboardingOverlay(controller = controller)
  }
}
```

**下次启动** 同一 `guideId` 的引导时, 由于持久化已记录为"已完成", `LaunchOnboarding` 会**自动跳过**, 不会显示任何引导.

## API 参考

### `BubblePlacement` (气泡位置)

| Placement | 说明 |
| --- | --- |
| `Auto` | **自动选最大空间方向** (推荐) — 测量气泡尺寸 + 容器尺寸, 选四周空间最大的方向 |
| `Above` | target 上方居中 |
| `Below` | target 下方居中 |
| `Left` | target 左侧 |
| `Right` | target 右侧 |
| `TopCenter` | 屏幕顶部居中 |
| `BottomCenter` | 屏幕底部居中 |
| `Custom(x, y)` | 自定义坐标 |

`BubblePlacement.computeBest(target, container, bubbleSize, margin)` — 手动调用 Auto 算法.

### `HighlightShape` (高亮形状, 8 种 + Auto)

| Shape | 适用场景 |
| --- | --- |
| `Auto` | **自适应** — 根据 target 宽高比自动选择 Circle / Stadium / RoundedRect (默认, 推荐) |
| `RoundedRect(cornerRadius)` | 圆角矩形 (按钮 / 卡片) |
| `Rect` | 矩形 (大面积内容) |
| `Circle` | 圆形 (FAB / 头像) |
| `Oval` | 椭圆头像 |
| `Stadium` | 胶囊 (状态标签) |
| `Polygon(sides, cornerRadius)` | 多边形 (六边形 / 八边形) |
| `Blob(seed, points, irregularity)` | 不规则创意形状 |
| `Spotlight` | 聚光 |
| `Custom(shape)` | 自定义 (传入 `Shape`) |

### `HighlightTheme` (高亮主题, 8 种)

| Theme | 说明 |
| --- | --- |
| `Solid(borderColor, borderWidth, borderAlpha, scrimColor)` | 实色边框 (默认) |
| `Dashed(borderColor, borderWidth, dashLength, gapLength, scrimColor)` | 虚线 |
| `Dotted(borderColor, borderWidth, dotSpacing, scrimColor)` | 点线 |
| `Neon(color, borderWidth, glowRadius, glowColor, scrimColor)` | 霓虹 (外发光) |
| `Tape(color, length, thickness, scrimColor)` | 胶带 (四角 L 形括号, 不闭合) |
| `Corners(color, length, thickness, cornerRadius, scrimColor)` | 仅四角装饰 |
| `Spotlight(scrimColor, ringColor, ringWidth, softEdge)` | 聚光 (圆形高光 + 周围暗) |
| `Frosted(borderColor, borderWidth, blurAlpha, scrimColor)` | 磨砂 |
| `Custom(theme)` | 套娃自定义 |

### `HighlightAnimation` (高亮动画, 6 种)

| Animation | 说明 |
| --- | --- |
| `None` | 无动画 |
| `Pulse(durationMs, minAlpha, maxAlpha, minScale, maxScale)` | 脉冲 (透明度 + 缩放) |
| `Rotate(durationMs, direction)` | 旋转 (顺时针 / 逆时针) |
| `Breathe(durationMs, minAlpha, maxAlpha)` | 呼吸 (缓慢透明度变化) |
| `Scan(durationMs, lineWidth, lineColor, lineAlpha)` | 扫描 (垂直线从左到右) |
| `Wave(durationMs, waveCount, maxRadiusMultiplier, waveColor, waveAlpha)` | 波动 (涟漪扩散) |
| `Shimmer(durationMs, baseColor, highlightColor)` | 微光 (颜色渐变) |

### `OnboardingTarget` (目标 id 绑定)

```kotlin
// 创建目标 (全局复用)
val target = OnboardingTarget.of("search_button")

// 在 UI 上打标记 (自动跟踪位置)
Box(modifier = Modifier.onboardingBind(target)) { ... }

// 快捷方式
Box(modifier = Modifier.onboardingBind("search_button")) { ... }

// 在 step 中使用
OnboardingStep(id = "...", target = target, ...)
```

`target.rectFlow: StateFlow<Rect?>` — 订阅位置变化, `rememberTargetRect(step)` 自动响应.

### `OnboardingConfig` (全局配置)

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `guideId` | `String?` | `null` | 引导唯一 ID (用于持久化) |
| `preferences` | `OnboardingPreferences?` | `null` | 持久化实现 |
| `skipIfCompleted` | `Boolean` | `true` | 已完成是否跳过 |
| `onComplete` | `(() -> Unit)?` | `null` | 完成回调 (会自动持久化) |
| `onSkipped` | `(() -> Unit)?` | `null` | 跳过回调 |
| `cancellable` | `Boolean` | `true` | 点击空白是否跳过 |
| `showProgressIndicator` | `Boolean` | `true` | 是否显示进度 |
| `pauseBetweenStepsMs` | `Long` | `320` | 步骤间暂停 |

### `OnboardingController` API

| 方法 | 说明 |
| --- | --- |
| `start()` | 启动;若 `skipIfCompleted` 且已完成,则不启动 |
| `next()` | 下一步;最后一步会触发 `onComplete` + 持久化 |
| `previous()` | 上一步 |
| `skip()` | 跳过 (不会标记完成) |
| `finish()` | 立即完成 + 持久化 |
| `restart()` | 重新开始 (保留持久化) |
| `restartFromScratch()` | 清除持久化 + 重新开始 |
| `markCompleted()` | 手动标记完成 |
| `clearCompleted()` | 清除持久化 |

### `BubbleShape` (气泡形状, 10 种)

| Shape | 说明 |
| --- | --- |
| `RoundedRectangle` | 圆角矩形 (默认) |
| `Square` | 圆角正方形 |
| `Pill` | 胶囊 |
| `Circle` | 圆形 |
| `WideCard` | 大圆角宽卡片 |
| `StretchedBar` | 长条 |
| `Hexagon` | 六边形 |
| `Diamond` | 菱形 |
| `SpeechBubble` | 聊天气泡 (4 种尖角方向) |
| `Tabbed` | 带底部凸出指示 |
| `Custom(yourShape)` | 自定义 |

### `BubbleStyle` (气泡样式)

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `glassTint` | `Color.White` | 玻璃底色 |
| `glassAlpha` | `0.55f` | 玻璃透明度 |
| `glassBlurRadius` | `24.dp` | 高斯模糊半径 |
| `grainAlpha` | `0.06f` | 颗粒噪点透明度 |
| `borderColor` / `borderWidth` | `0x66FFFFFF` / `1.dp` | 描边 |
| `shadowColor` / `shadowElevation` | `0x33000000` / `12.dp` | 投影 |
| `contentPadding` | `20.dp` | 内部 padding |
| `maxWidth` / `minWidth` | `360.dp` / `120.dp` | 尺寸限制 |

预设: `Default`, `Compact`, `TopBar`, `BottomBar`, `WideCard`, `Tooltip`.

### `TouchSimulator` (操作模拟)

```kotlin
TouchSimulator.tap(x, y)
TouchSimulator.longPress(x, y)
TouchSimulator.swipe(fromX, fromY, toX, toY, durationMs = 600, loop = false)
TouchSimulator.drag(points = listOf(PathPoint(...), ...))
TouchSimulator.scroll(fromX, fromY, toX, toY)
TouchSimulator.multiTouch(points)
```

支持自定义 `loop`, `showFinger`, `showTrail`, `showRipple`, `fingerSize` 等.

### `OnboardingPreferences` (持久化接口)

```kotlin
interface OnboardingPreferences {
  fun isCompleted(guideId: String): Boolean
  fun markCompleted(guideId: String)
  fun reset(guideId: String)
  fun resetAll()
  fun observeCompleted(guideId: String): Flow<Boolean>
  fun getAllCompleted(): Set<String>
}
```

两个内置实现:
- `SharedPreferencesOnboardingPreferences(context)` — 生产环境 (基于 Android `SharedPreferences`)
- `MemoryOnboardingPreferences()` — 内存版 (测试用, 进程结束即丢失)

## 高级用法

### 形状自适应 (Auto) 工作原理

`HighlightShape.Auto` 会根据目标 `Rect` 的宽高比自动选择最合适的形状:

| 宽高比 | 选择的形状 |
| --- | --- |
| 0.85 ~ 1.15 (接近正方形) | `Circle` |
| > 2.5 或 < 0.4 (细长) | `Stadium` (胶囊) |
| 其它 | `RoundedRect` (圆角矩形) |

### 气泡 Auto 定位算法

`BubblePlacement.Auto` 测量气泡实际尺寸 + 容器尺寸, 选四周空间最大的方向, 避免气泡被屏幕边界或被高亮的目标区域遮挡.

### 不改变被框选目标原样

使用 `BlendMode.DstOut` 在蒙层上切割目标形状, 露出下层原始 UI 控件. **不会** 改变目标颜色 / 形状 / 折叠状态.

### 监听引导完成状态

```kotlin
val prefs = SharedPreferencesOnboardingPreferences(context)
val isDone by prefs.observeCompleted("first_time").collectAsState(initial = false)

// isDone = true → 该引导已完成, 不再显示
```

### 调试: 用 SemanticsKey 找到目标节点

```kotlin
import androidx.compose.ui.test.SemanticsMatcher

// 在测试中
composeTestRule.onNode(SemanticsMatcher.expectValue(
  OnboardingTargetIdKey, "search_button"
)).assertExists()
```

## License

GNU General Public License v3
