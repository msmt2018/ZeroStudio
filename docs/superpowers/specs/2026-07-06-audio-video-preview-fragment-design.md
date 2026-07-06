# 设计文档：音频与视频预览 Fragment

- **日期**: 2026-07-06
- **范围**: core/app 内新增 `AudioPreviewFragment` 与 `VideoPreviewFragment` 两个编辑器预览 tab
- **不在本次范围**: Web 预览 fragment（用户已确认延后，下次会话使用 `androidx.webkit:1.17.0-alpha03`）
- **依赖前置**: 项目已有 `androidx.media3` 依赖（chatai/speech 模块的 `AudioPlayer.kt` 已使用），本次仅补全 ui/session/datasource/extractor 子包

## 1. 背景与目标

### 1.1 现状

- 编辑器预览 tab 系统由三层组成：`FragmentTabRegistry`（注册表）+ `EditorFragmentTabRegistrar`（注册器）+ `EditorFragmentTabManager`（运行时生命周期），位于 [core/app/.../fragments/editor/](file:///workspace/core/app/src/main/java/com/itsaky/androidide/fragments/editor/) 与 [core/app/.../utils/EditorFragmentTabRegistrar.kt](file:///workspace/core/app/src/main/java/com/itsaky/androidide/utils/EditorFragmentTabRegistrar.kt)
- 当前已注册 3 个 tab：Markdown (order=100)、Image (order=110)、Universal (order=120)
- [FileValidator.kt](file:///workspace/core/common/src/main/java/com/itsaky/androidide/file/FileValidator.kt) 已实现 `isAudio(file)` / `isVideo(file)`（通过 `MediaExtractor` 嗅探容器轨道），但**未接入预览路由**
- FileExtension 枚举已为音频 14 种扩展名、视频 15 种扩展名定义了图标（`ic_file_type_music` / `ic_file_type_video`），但点击后只走默认文本编辑器
- 项目已有 Haze 库（`dev.chrisbanes.haze:2.0.0-alpha03`）并在 chatai 与 onboarding 模块验证过 frosted glass 实现
- 项目已有 `material-icons-extended:1.7.8` 图标库

### 1.2 目标

新增两个编辑器预览 fragment：

1. **AudioPreviewFragment** — 支持多种音频格式播放，丰富功能（EQ / 频谱 / 歌词 / 后台播放），UI 控件采用半透明高斯模糊磨砂效果，APK 增量 ≤ 2 MB
2. **VideoPreviewFragment** — 支持多种视频格式播放，丰富功能（手势 / 字幕 / 音轨 / 比例），同样 UI 控件采用半透明高斯模糊磨砂效果

### 1.3 非目标

- 不引入 FFmpeg 扩展（避免 5-15 MB 增量，违反轻量化要求）
- 不内置编译浏览器内核（Web fragment 延后）
- 不实现完整的音视频编辑功能（仅预览播放）
- 不重构现有 ImagePreviewFragment / MarkdownPreviewFragment

## 2. 架构

### 2.1 模块组织

参照现有 `ImagePreviewFragment` 模式，所有代码放在 `core/app` 内，不新建 modules/。理由：与现有 fragment 系统、Action 系统、资源系统紧密集成，避免跨模块依赖协调。

```
core/app/src/main/java/com/itsaky/androidide/fragments/editor/
├── audio/
│   ├── AudioPreviewFragment.kt        # 主 fragment (ComposeView + MaterialTheme)
│   ├── AudioPlaybackController.kt     # Media3 ExoPlayer 封装 (StateFlow 暴露状态)
│   ├── AudioVisualizer.kt             # 频谱可视化 (Visualizer API + Compose Canvas)
│   ├── LyricSyncController.kt         # .lrc 解析 + 时间轴同步滚动
│   ├── EqualizerController.kt         # android.media.audiofx.Equalizer 封装
│   └── components/
│       ├── FrostedControlBar.kt       # 磨砂玻璃控制栏 (Haze + 主题感知图标)
│       ├── FrostedButton.kt           # 磨砂玻璃按钮
│       └── FrostedSlider.kt           # 磨砂玻璃滑块
├── video/
│   ├── VideoPreviewFragment.kt        # 主 fragment (AndroidView + PlayerView + Compose overlay)
│   ├── VideoGestureHandler.kt         # 亮度/音量/进度手势
│   └── VideoSubtitleController.kt     # 字幕轨道选择 + 渲染
└── (现有 image/ markdown/ 不变)

core/app/src/main/java/com/itsaky/androidide/ui/compose/
└── LocalDarkMode.kt                   # CompositionLocal<Boolean> + 提供者
```

### 2.2 主题感知方案

**问题**: core/app 当前没有 Compose 端的暗色状态持有者（`MaterialTheme` 使用裸的硬编码 `Color.White`）。`AppCompatDelegate.setDefaultNightMode` 设置的夜间模式 Compose 无法直接感知。

**方案**: 引入 `LocalDarkMode` CompositionLocal，通过 `ThemeManager.isNightModeActive(context)` 解析当前夜间模式状态。这镜像了 chatai 模块的 `LocalDarkMode` 模式。

```kotlin
// ui/compose/LocalDarkMode.kt
val LocalDarkMode = compositionLocalOf<Boolean> { error("LocalDarkMode not provided") }

@Composable
fun ProvideDarkMode(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDark = remember { ThemeManager.isNightModeActive(context) }
    // 监听配置变化 (主题切换会触发 recreate, 但 fragment 内需主动更新)
    val configuration = LocalConfiguration.current
    val isDarkState = remember(configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
        ThemeManager.isNightModeActive(context)
    }
    CompositionLocalProvider(LocalDarkMode provides isDarkState, content = content)
}
```

**主题感知颜色**:

| 元素 | Light 模式 | Dark 模式 |
|---|---|---|
| 图标颜色 | `Color.Black.copy(alpha = 0.85f)` | `Color.White.copy(alpha = 0.95f)` |
| 磨砂玻璃 tint | `Color.White.copy(alpha = 0.55f)` | `Color.Black.copy(alpha = 0.45f)` |
| 磨砂玻璃边框 | `Color.White.copy(alpha = 0.3f)` | `Color.White.copy(alpha = 0.15f)` |
| 文本颜色 | `Color.Black.copy(alpha = 0.9f)` | `Color.White.copy(alpha = 0.9f)` |

**关键约束**: 高斯模糊效果本身**不随主题切换**——blur radius、blur algorithm、noise 纹理都是主题无关的。只有 tint 透明度和图标颜色随主题变化。这是通过 Haze 库的 `HazeMaterials.thin()` + 显式 `tint` 参数实现的，tint 由 `LocalDarkMode.current` 决定，blur 行为固定。

### 2.3 磨砂玻璃控件实现

基于 Haze 库（已在 [core/chatai/app/.../ChatInput.kt](file:///workspace/core/chatai/app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt) 验证）与 onboarding 模块的 [FrostedGlass.kt](file:///workspace/modules/zero-onboarding-guide/src/main/kotlin/com/itsaky/androidide/onboarding/effects/FrostedGlass.kt) 范式。

```kotlin
@Composable
fun FrostedControlBar(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isDark = LocalDarkMode.current
    val tint = if (isDark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.55f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    tint = HazeTint(tint),
                    blurRadius = 20.dp,
                    noiseFactor = 0.2f,
                    backgroundColor = Color.Transparent
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        content()
    }
}
```

**降级**: Haze 在 API < 31 自动降级为半透明纯色背景（无真模糊），保留主题感知的 tint。这是 Haze 库的内置行为，无需手动处理。

### 2.4 图标方案

使用项目已有的 `androidx.compose.material:material-icons-extended:1.7.8`。所有图标通过 `Icons.Default.Xxx` 访问，tint 由 `LocalDarkMode` 决定：

```kotlin
@Composable
fun FrostedIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = if (LocalDarkMode.current) Color.White.copy(alpha = 0.95f) else Color.Black.copy(alpha = 0.85f)
    IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = iconColor)
    }
}
```

各功能使用的 Material 图标：
- 播放/暂停: `Icons.Default.PlayArrow` / `Icons.Default.Pause`
- 上一首/下一首: `Icons.Default.SkipPrevious` / `Icons.Default.SkipNext`
- 循环: `Icons.Default.Repeat` / `Icons.Default.RepeatOne`
- 随机: `Icons.Default.Shuffle`
- 列表: `Icons.Default.QueueMusic`（音频）/ `Icons.Default.VideoLibrary`（视频）
- EQ: `Icons.Default.Equalizer`
- 倍速: `Icons.Default.Speed`
- 音量: `Icons.Default.VolumeUp`
- 字幕: `Icons.Default.Subtitles`
- 比例: `Icons.Default.AspectRatio`
- 截图: `Icons.Default.PhotoCamera`
- 全屏: `Icons.Default.Fullscreen`

## 3. 音频 Fragment 详细设计

### 3.1 支持格式

`SUPPORTED_FORMATS` (setOf):
```
mp3, wav, ogg, flac, aac, m4a, opus, mid, midi, amr, pcm, aiff, ape, wma
```

ExoPlayer 原生支持前 11 种。`aiff/ape/wma` 通过 `MediaExtractor` 嗅探，若设备解码器不支持则在 UI 显示"该设备不支持此格式解码"提示（不崩溃）。

### 3.2 AudioPlaybackController

封装 `androidx.media3.exoplayer.ExoPlayer`，通过 `StateFlow<PlaybackState>` 暴露状态：

```kotlin
data class PlaybackState(
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val playbackSpeed: Float,
    val repeatMode: Int,        // REPEAT_MODE_OFF/ONE/ALL
    val shuffleMode: Boolean,
    val currentTrackIndex: Int,
    val errorMessage: String?
)

class AudioPlaybackController(context: Context) {
    val state: StateFlow<PlaybackState>
    fun setMediaItems(uris: List<Uri>, startIndex: Int)
    fun play(); fun pause(); fun seekTo(positionMs: Long)
    fun seekToNext(); fun seekToPrevious()
    fun setPlaybackSpeed(speed: Float)
    fun setRepeatMode(mode: Int); fun setShuffleMode(enabled: Boolean)
    fun release()
}
```

**生命周期**: Fragment `onCreateView` 创建 controller，`onDestroyView` 调用 `release()`。

### 3.3 频谱可视化 (AudioVisualizer)

使用 `android.media.audiofx.Visualizer`（API 18+，在 minSdk 26 内）：

```kotlin
class AudioVisualizer(audioSessionId: Int) {
    private val visualizer = Visualizer(audioSessionId).apply {
        captureSize = Visualizer.getCaptureSizeRange()[1]
        setDataListener(...) { waveform: ByteArray -> updateState(waveform) }
    }
    val waveform: StateFlow<FloatArray>  // 归一化到 [0, 1]
}
```

Compose Canvas 实时绘制：
- **柱状模式**: 32 个频段，每段高度 = `waveform[i]` 映射到 0-100dp
- **波形模式**: 连续路径绘制
- **圆形模式**: 极坐标映射

颜色使用 `LocalDarkMode` 感知（与图标相同的黑白策略），但波形顶部可使用主题强调色（来自当前 IDETheme，可选）。

### 3.4 歌词同步 (LyricSyncController)

```kotlin
data class LyricLine(val timeMs: Long, val text: String)
class LyricSyncController {
    fun parseLrc(content: String): List<LyricLine>  // 解析 [mm:ss.xx] 格式
    fun loadFromFile(file: File): List<LyricLine>   // 同名 .lrc 或嵌入 ID3
    fun currentLine(positionMs: Long, lyrics: List<LyricLine>): Int  // 二分查找
}
```

UI: LazyColumn 渲染歌词，当前行高亮放大，上下行半透明，自动滚动到当前行。

### 3.5 均衡器 (EqualizerController)

```kotlin
class EqualizerController(audioSessionId: Int) {
    val bandCount: Int
    val bandFrequencies: IntArray  // Hz
    val bandLevels: IntArray       // 当前 dB
    val presets: List<String>      // Pop, Rock, Jazz, Classical, Normal...
    fun setBandLevel(band: Int, level: Int)
    fun applyPreset(name: String)
}
```

使用 `android.media.audiofx.Equalizer`（API 9+）。预设通过 Equalizer 内置 `usePreset()` + 自定义补充。

### 3.6 后台播放

Foreground Service + `MediaSession`：
- `AudioPlaybackService : MediaSessionService`
- 通知栏：专辑封面 + 标题 + 控制按钮（同样使用磨砂玻璃样式）
- `MediaButtonReceiver` 处理耳机线控 / 蓝牙按键

由于 fragment 与 service 的生命周期不同，使用 `MediaController` 在 fragment 内远程控制 service。

### 3.7 UI 布局

```
┌─────────────────────────────────────────┐
│ [文件名] ────────── [00:00 / 03:24]    │ ← 顶部磨砂栏
├─────────────────────────────────────────┤
│                                         │
│         [专辑封面 / 占位音符]           │
│                                         │
│         标题                            │
│         艺术家 - 专辑                   │
│                                         │
│   ▁▂▃▅▇▇▅▃▂▁ ▁▂▃▅▇▇▅▃▂▁ ▁▂▃▅▇▇▅▃▂▁   │ ← 频谱可视化
│                                         │
│         ♪ 当前歌词行 (放大)             │
│      下一句歌词 (淡色 0.5)              │
│      下下句歌词 (淡色 0.3)              │
│                                         │
├─────────────────────────────────────────┤
│  ━━━━━━━━━●━━━━━━━━━━━━━━━━━━━━━━━━━━  │ ← 磨砂进度条
│ [⏮] [⏯] [⏭] [🔁] [🔀] [📋] [🎚️] [1x] │ ← 磨砂控制栏
└─────────────────────────────────────────┘
```

所有 `[xxx]` 标注的控件使用 `FrostedControlBar` / `FrostedButton` / `FrostedSlider`，图标使用 Material Icons + 主题感知 tint。

## 4. 视频 Fragment 详细设计

### 4.1 支持格式

`SUPPORTED_FORMATS` (setOf):
```
mp4, mkv, webm, mov, m4v, 3gp, ts, mpg, mpeg, m2ts, ogv, avi, flv, wmv, vob
```

ExoPlayer 原生支持前 11 种。`avi/flv/wmv/vob` 通过 `MediaExtractor` 嗅探后尝试播放，不支持则提示。

### 4.2 视频画面渲染

使用 `androidx.media3.ui.PlayerView`（嵌入到 Compose via `AndroidView`）。Compose overlay 在 `PlayerView` 之上叠加磨砂玻璃控件。

```kotlin
Box {
    AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } })
    FrostedControlBar(hazeState, modifier = Modifier.align(TopCenter)) { /* 顶部工具栏 */ }
    FrostedControlBar(hazeState, modifier = Modifier.align(BottomCenter)) { /* 底部控制栏 */ }
}
```

`PlayerView` 自带的 `useController = false`（禁用默认控制条，全部使用磨砂玻璃自定义控件）。

### 4.3 手势 (VideoGestureHandler)

```kotlin
class VideoGestureHandler(
    val onBrightnessChange: (Float) -> Unit,    // 0.0 - 1.0
    val onVolumeChange: (Float) -> Unit,        // 0.0 - 1.0
    val onSeekRelative: (Long) -> Unit,         // 相对 seek 毫秒
    val onScale: (Float) -> Unit,               // 1.0 - 3.0
    val onDoubleTap: () -> Unit                 // 暂停/恢复
)
```

- **左半屏上下滑**: 调亮度（`Window.attributes.screenBrightness`）
- **右半屏上下滑**: 调音量（`AudioManager.setStreamVolume`）
- **左右滑**: 快进/快退（显示 ±10s 预览气泡，磨砂玻璃）
- **双击**: 暂停/恢复
- **双指缩放**: 画面缩放 1.0x-3.0x

通过 `Modifier.pointerInput` + `detectTransformGestures` + `detectTapGestures`。

### 4.4 字幕 (VideoSubtitleController)

```kotlin
class VideoSubtitleController {
    fun loadEmbeddedTracks(player: ExoPlayer): List<TrackInfo>  // 通过 TrackSelector 获取
    fun selectTrack(track: TrackInfo?)
    fun loadExternalSrt(file: File): List<Cue>  // 解析 .srt
    fun loadExternalVtt(file: File): List<Cue>  // 解析 .vtt
    fun styleSubtitle(size: Float, color: Color, position: Float)  // 底部偏移
}
```

字幕渲染使用 `PlayerView` 内置 subtitle view + 自定义样式。

### 4.5 画面比例

支持 5 种模式通过 `PlayerView.resizeMode`：
- 原始 (`RESIZE_MODE_ZOOM`)
- 16:9 (`RESIZE_MODE_FIT`)
- 4:3 (`RESIZE_MODE_FIT` + AspectRatioFrameLayout)
- 拉伸 (`RESIZE_MODE_FILL`)
- 裁剪 (`RESIZE_MODE_ZOOM` + center crop)

### 4.6 截图

通过 `PlayerView.bitmap`（Media3 1.x 提供）或 `TextureView.getBitmap()` 截图，保存到 `Pictures/ZeroStudio/Screenshots/<filename>_<timestamp>.png`，通过 `MediaScannerConnection` 通知相册。

### 4.7 UI 布局

```
┌─────────────────────────────────────────┐
│ [文件名] [分辨率 1920x1080] [⤢] [📷]    │ ← 顶部磨砂栏 (3秒无操作淡出)
├─────────────────────────────────────────┤
│                                         │
│                                         │
│           视频画面 (手势区)              │
│        左半屏=亮度 右半屏=音量           │
│        左右滑=seek 双击=暂停             │
│                                         │
│                                         │
├─────────────────────────────────────────┤
│  ━━━━━━━━━●━━━━━━━━━━━━━━━━━━━━━━━━━━  │ ← 磨砂进度条
│ [⏯] [🔁] [1.0x] [🔊] [📝] [⛶]          │ ← 磨砂控制栏
└─────────────────────────────────────────┘

快进预览气泡 (手势中临时显示):
        ┌──────────────┐
        │  +10s        │
        │  00:42 → 00:52 │
        └──────────────┘
        (磨砂玻璃 + 主题感知)
```

## 5. 注册与路由

### 5.1 EditorFragmentTabRegistrar

在 [EditorFragmentTabRegistrar.kt](file:///workspace/core/app/src/main/java/com/itsaky/androidide/utils/EditorFragmentTabRegistrar.kt) `registerAll()` 末尾新增：

```kotlin
registerAudioPreview()    // order=130
registerVideoPreview()    // order=140
```

新增常量：
```kotlin
const val AUDIO_PREVIEW = "audio_preview"
const val VIDEO_PREVIEW = "video_preview"
```

### 5.2 Action 系统

在 [EditorActivityActions.kt](file:///workspace/core/app/src/main/java/com/itsaky/androidide/utils/EditorActivityActions.kt) 注册：
- `AudioPreviewAction` (id `ide.editor.audioPreview`, icon `ic_file_type_music`)
- `VideoPreviewAction` (id `ide.editor.videoPreview`, icon `ic_file_type_video`)

参照 [ImagePreviewAction.kt](file:///workspace/core/app/src/main/java/com/itsaky/androidide/actions/etc/image/ImagePreviewAction.kt) 模式：`prepare` 检查 `SUPPORTED_FORMATS.contains(extension)`，`postExec` 调用 `fragmentTabManager.openFileTab(...)`。

### 5.3 FileTreeActionHandler 路由

在 [FileTreeActionHandler.kt](file:///workspace/core/app/src/main/java/com/itsaky/androidide/handlers/FileTreeActionHandler.kt) `onFileClicked` 中，参照 image 路由（line 102），新增：

```kotlin
if (FileValidator.isAudio(file)) {
    context.fragmentTabManager?.openFileTab(file.absolutePath, file.extension)
    return
}
if (FileValidator.isVideo(file)) {
    context.fragmentTabManager?.openFileTab(file.absolutePath, file.extension)
    return
}
```

### 5.4 AndroidManifest

注册 `AudioPlaybackService`：

```xml
<service
    android:name=".fragments.editor.audio.AudioPlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="false">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

权限：
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />  <!-- EQ -->
```

## 6. 依赖补充

在 [core/app/build.gradle.kts](file:///workspace/core/app/build.gradle.kts) `dependencies` 块新增：

```kotlin
// Media3 (项目已有 exoplayer, 补全其他子包)
implementation(libs.androidx.media3.exoplayer)      // 已有
implementation(libs.androidx.media3.ui)
implementation(libs.androidx.media3.session)
implementation(libs.androidx.media3.datasource)
implementation(libs.androidx.media3.extractor)

// Haze (磨砂玻璃, 项目已在 chatai/onboarding 使用)
implementation(libs.haze)
implementation(libs.haze.blur)
implementation(libs.haze.blur.materials)
```

在 `gradle/libs.versions.toml` 补全 media3 子包别名（haze 别名已存在，无需新增）。

预计 APK 增量：1-2 MB（media3-ui + session + datasource + extractor）。

## 7. 字符串资源

在 [core/resources/src/main/res/values/strings.xml](file:///workspace/core/resources/src/main/res/values/strings.xml) 新增：

```xml
<!-- Audio Preview -->
<string name="audio_preview_tab_title">Audio Preview</string>
<string name="audio_preview_eq_preset_normal">Normal</string>
<string name="audio_preview_eq_preset_pop">Pop</string>
<string name="audio_preview_eq_preset_rock">Rock</string>
<string name="audio_preview_eq_preset_jazz">Jazz</string>
<string name="audio_preview_eq_preset_classical">Classical</string>
<string name="audio_preview_visualizer_bars">Bars</string>
<string name="audio_preview_visualizer_wave">Wave</string>
<string name="audio_preview_visualizer_circular">Circular</string>
<string name="audio_no_lyrics">No lyrics found</string>
<string name="audio_format_unsupported">Device decoder does not support this format</string>

<!-- Video Preview -->
<string name="video_preview_tab_title">Video Preview</string>
<string name="video_ratio_original">Original</string>
<string name="video_ratio_16_9">16:9</string>
<string name="video_ratio_4_3">4:3</string>
<string name="video_ratio_stretch">Stretch</string>
<string name="video_ratio_crop">Crop</string>
<string name="video_subtitle_off">Off</string>
<string name="video_subtitle_external">External: %1$s</string>
<string name="video_screenshot_saved">Screenshot saved to Pictures/ZeroStudio/Screenshots</string>
```

## 8. 实现顺序

1. **基础设施** (无 UI):
   - `LocalDarkMode.kt` + `ProvideDarkMode` composable
   - `FrostedControlBar` / `FrostedButton` / `FrostedSlider` 组件
   - libs.versions.toml + build.gradle.kts 依赖补充

2. **AudioPlaybackController** (无 UI):
   - ExoPlayer 封装 + StateFlow
   - 单元测试（播放/暂停/seek/列表切换）

3. **AudioVisualizer / LyricSyncController / EqualizerController** (无 UI):
   - Visualizer 数据采集
   - LRC 解析
   - Equalizer 封装

4. **AudioPreviewFragment** (UI):
   - 主 fragment 骨架 + ComposeView
   - 频谱可视化 Canvas
   - 歌词 LazyColumn
   - 磨砂玻璃控制栏
   - 元数据显示

5. **AudioPlaybackService** (后台播放):
   - MediaSession + Foreground Service
   - 通知栏（磨砂玻璃样式）
   - MediaController 远程控制

6. **VideoPlaybackController** (无 UI):
   - 复用 ExoPlayer 封装

7. **VideoGestureHandler / VideoSubtitleController** (无 UI):
   - 手势检测
   - 字幕加载

8. **VideoPreviewFragment** (UI):
   - PlayerView + Compose overlay
   - 磨砂玻璃控制栏
   - 手势集成
   - 截图功能

9. **注册与路由**:
   - EditorFragmentTabRegistrar 新增注册
   - AudioPreviewAction / VideoPreviewAction
   - FileTreeActionHandler 路由扩展
   - AndroidManifest service 注册

10. **资源**:
    - strings.xml
    - 图标（复用现有 `ic_file_type_music` / `ic_file_type_video`）

## 9. 测试策略

- **单元测试**: AudioPlaybackController 状态机、LyricSyncController LRC 解析、EqualizerController 预设映射
- **手动测试**: 各格式音频/视频文件实际播放、磨砂玻璃效果在 light/dark 主题下显示、手势操作、字幕加载
- **回归测试**: 现有 Markdown/Image/Universal preview tab 不受影响

## 10. 风险与缓解

| 风险 | 缓解 |
|---|---|
| Haze 2.0.0-alpha03 是 alpha 版本 | 项目 chatai/onboarding 已使用此版本稳定运行；若崩溃可降级为半透明纯色背景 |
| minSdk 26 下 Visualizer/EQ API 行为差异 | API 18+ 即可使用，26 完全覆盖；测试设备覆盖 26/29/33 |
| MediaSession 后台播放与 fragment 生命周期协调 | 使用 MediaController 远程控制，fragment onDestroyView 仅释放 controller 不停 service |
| 不支持的音频格式（如设备无 WMA 解码器） | ExoPlayer 抛出 `UnsupportedFormatException`，UI 显示友好提示，不崩溃 |
| 视频 AVI/FLV/WMV 容器可能无法播放 | 同上，嗅探后尝试播放，失败提示 |
