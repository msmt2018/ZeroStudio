# zero-onboarding-guide

> 一个 Android Compose 库: 操作引导 + 指引气泡 + 高亮框选 + 操作模拟 (Dribbble 风格).

`zero-onboarding-guide` 是一个独立的 Android Library 模块, 主要用于在应用内任何位置
显示**操作引导气泡**, **高亮框选遮罩**, 以及**操作模拟动画**.

## 设计理念

参考 [dribbble.com](https://dribbble.com) 顶级设计师的 UX 设计风格:

- **偏白半透明磨砂玻璃**: 高级感 (使用 Haze 实现高斯模糊)
- **微颗粒噪点**: 真实玻璃质感 (GrainNoise 纯 Compose 实现)
- **弹性物理动画**: 高级且有创意的 Spring 动画
- **多种内置形状**: 圆角矩形 / 圆 / 胶囊 / 六边形 / 菱形 / 聊天气泡等 10 种
- **高亮框选**: 用 `BlendMode.DstOut` 切割蒙层, 让目标区域自然透出
- **操作模拟**: 手指动画 + 轨迹 + 波纹, 支持 tap / long-press / swipe / drag / multi-touch / scroll

## 模块结构

```
modules/zero-onboarding-guide/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── kotlin/com/itsaky/androidide/onboarding/
│   │   ├── OnboardingGuide.kt              # API 门面 + DSL
│   │   ├── OnboardingController.kt         # 状态机
│   │   ├── OnboardingOverlay.kt            # 根容器
│   │   ├── bubble/
│   │   │   ├── BubbleShape.kt              # 10 种内置形状
│   │   │   ├── BubbleStyle.kt              # 样式 (玻璃 / 描边 / 投影)
│   │   │   ├── BubbleContent.kt            # 标题 + 副标题 + 图标
│   │   │   └── GuideBubble.kt              # Composable
│   │   ├── highlight/
│   │   │   └── HighlightFrame.kt           # 高亮框选 + 脉冲
│   │   ├── simulation/
│   │   │   └── TouchSimulator.kt           # 操作模拟
│   │   ├── animation/
│   │   │   └── AnimationDefaults.kt        # 动画规格
│   │   ├── effects/
│   │   │   ├── FrostedGlass.kt             # 磨砂玻璃效果
│   │   │   └── GrainNoise.kt               # 颗粒噪点
│   │   └── demo/
│   │       ├── OnboardingGuideExample.kt   # 完整示例
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

### 2. 最简用法 (居中提示气泡)

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
  YourContent()

  GuideBubble(
    content = BubbleContent(
      title = "欢迎使用",
      subtitle = "这是一个引导示例",
    ),
    shape = BubbleShape.RoundedRectangle(),
    style = BubbleStyle.Default,
  )
}
```

### 3. 高亮框选 + 气泡 (典型引导)

```kotlin
@Composable
fun OnboardingExample() {
  var targetRect by remember { mutableStateOf<Rect?>(null) }
  var controller by remember { mutableStateOf<OnboardingController?>(null) }

  LaunchedEffect(Unit) {
    controller = LaunchOnboarding(
      steps = listOf(
        OnboardingStep(
          id = "step1",
          content = BubbleContent(
            title = "点这里!",
            subtitle = "这是搜索按钮",
          ),
          targetRect = targetRect,
          bubbleShape = BubbleShape.RoundedRectangle(),
          bubbleStyle = BubbleStyle.Tooltip,
          bubblePlacement = BubblePlacement.Above,
          highlightStyle = HighlightStyle.Strong,
        ),
      ),
    )
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // 目标控件
    Box(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .size(48.dp)
        .onGloballyPositioned { coords ->
          val pos = coords.positionInWindow()
          targetRect = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
        },
    )

    // 引导浮层
    controller?.let { OnboardingOverlay(controller = it) }
  }
}
```

### 4. 操作模拟 (上滑打开底部抽屉)

```kotlin
val drawerGuideStep = OnboardingStep(
  id = "drawer",
  content = BubbleContent(
    title = "上滑打开底部抽屉",
    subtitle = "这里有更多功能",
  ),
  targetRect = drawerHandleRect,
  touchSimulator = TouchSimulator.swipe(
    fromX = 200f, fromY = 1400f,  // 起点
    toX = 200f, toY = 800f,      // 终点
    durationMs = 800,
    loop = true,
  ),
)
```

### 5. 多种内置形状

```kotlin
BubbleShape.RoundedRectangle()        // 圆角矩形 (默认)
BubbleShape.Square()                  // 圆角正方形
BubbleShape.Pill()                    // 胶囊
BubbleShape.Circle                    // 圆形
BubbleShape.WideCard()                // 大圆角宽卡片
BubbleShape.StretchedBar()            // 长条
BubbleShape.Hexagon()                 // 六边形
BubbleShape.Diamond()                 // 菱形
BubbleShape.SpeechBubble()            // 聊天气泡 (带尖角)
BubbleShape.Tabbed()                  // 带底部凸出指示
BubbleShape.Custom(yourShape)         // 自定义
```

### 6. 多步引导 DSL

```kotlin
val steps = listOf(
  OnboardingGuide.step("welcome", "欢迎使用", "这是引导"),
  OnboardingGuide.stepWithGesture("drawer", "上滑打开抽屉",
    touchSimulator = TouchSimulator.swipe(...),
  ),
  OnboardingGuide.customStep("settings") {
    content = BubbleContent.full("设置", "自定义你的 IDE", Icons.Default.Settings)
    bubbleShape = BubbleShape.WideCard()
    bubbleStyle = BubbleStyle.WideCard
  },
)

LaunchOnboarding(steps, autoStart = true)
```

## API 参考

### `BubbleShape` (10 种内置形状)

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
| `Custom(shape)` | 自定义形状 |

### `BubbleStyle` (样式)

| 字段 | 说明 | 默认 |
| --- | --- | --- |
| `glassTint` | 玻璃底色 | `Color.White` |
| `glassAlpha` | 玻璃底色透明度 | `0.55f` |
| `glassBlurRadius` | 高斯模糊半径 | `24.dp` |
| `grainAlpha` | 颗粒噪点透明度 | `0.06f` |
| `borderColor` / `borderWidth` | 描边 | `0x66FFFFFF` / `1.dp` |
| `shadowColor` / `shadowElevation` | 投影 | `0x33000000` / `12.dp` |
| `contentPadding` | 内部 padding | `20.dp` |
| `maxWidth` / `minWidth` | 尺寸限制 | `360.dp` / `120.dp` |
| `innerHighlight` | 顶部高光内描边 | `true` |

预设: `Default`, `Compact`, `TopBar`, `BottomBar`, `WideCard`, `Tooltip`.

### `TouchSimulator` (操作模拟)

```kotlin
TouchSimulator.tap(x, y)
TouchSimulator.longPress(x, y)
TouchSimulator.swipe(fromX, fromY, toX, toY)
TouchSimulator.drag(points = listOf(PathPoint(...), ...))
TouchSimulator.scroll(fromX, fromY, toX, toY)
TouchSimulator.multiTouch(points)
```

支持自定义 `loop`, `showFinger`, `showTrail`, `showRipple`, `fingerSize` 等.

## 集成到 ZeroStudio

```kotlin
// app/build.gradle.kts
dependencies {
  implementation(projects.modules.zero.onboarding.guide)
}
```

## License

GNU General Public License v3
