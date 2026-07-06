/*
 * This file is part of ZeroStudio.
 *
 * ZeroStudio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ZeroStudio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ZeroStudio. If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.fragments.editor.video

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager
import com.itsaky.androidide.fragments.editor.components.FrostedGlass
import com.itsaky.androidide.fragments.editor.components.FrostedIconButton
import com.itsaky.androidide.fragments.editor.components.FrostedSlider
import com.itsaky.androidide.fragments.editor.components.FrostedText
import com.itsaky.androidide.fragments.editor.components.FrostedToggleIconButton
import com.itsaky.androidide.ui.compose.LocalDarkMode
import com.itsaky.androidide.ui.compose.ProvideDarkMode
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * 视频预览 fragment —— 基于 Media3 ExoPlayer + PlayerView + Compose 磨砂玻璃控件 overlay。
 *
 * ## 支持格式 (15 种)
 * mp4 / mkv / webm / avi / mov / 3gp / mpg / mpeg / ts / m2ts / flv / wmv / m4v / vob / ogv
 *
 * ## 功能
 * - 基础: 播放/暂停/seek/上一首/下一首/列表循环/单曲循环/随机/倍速
 * - 手势: 左半屏上下滑调亮度, 右半屏调音量, 中间左右滑 seek, 单击显隐控件, 双击播放暂停, 长按 2x
 * - 字幕: 内嵌字幕轨道选择 + 外挂 .srt / .vtt 同名文件解析 + Compose overlay 渲染
 * - 视频比例: 自适应 / 16:9 / 4:3 / 拉伸
 * - 截图: PixelCopy 把当前帧保存为 PNG 到同目录
 * - 控件自动隐藏: 播放 3 秒无操作自动隐藏控件
 *
 * ## 主题感知
 * 与 [com.itsaky.androidide.fragments.editor.audio.AudioPreviewFragment] 一致:
 * 所有 UI 控件用磨砂玻璃效果, blur 不随主题切换, 图标颜色随 [LocalDarkMode] 变化。
 *
 * @author ZeroStudio
 */
class VideoPreviewFragment : Fragment() {

    companion object {
        const val TAG = "VideoPreviewFragment"
        const val TAB_TITLE = "Video Preview"

        val SUPPORTED_EXTENSIONS: Set<String> = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "mpg", "mpeg",
            "ts", "m2ts", "flv", "wmv", "m4v", "vob", "ogv",
        )

        val SPEED_OPTIONS: FloatArray = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

        /** 控件自动隐藏时间 (毫秒)。 */
        const val CONTROLS_AUTO_HIDE_MS = 3000L

        @JvmStatic
        fun newInstance(filePath: String): VideoPreviewFragment {
            return VideoPreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(EditorFragmentTabManager.ARG_FILE_PATH, filePath)
                }
            }
        }
    }

    private var filePath: String? = null
    private var controller: VideoPlaybackController? = null
    private var playerViewRef: PlayerView? = null
    private val LOG = LoggerFactory.getLogger(TAG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            filePath = args.getString(EditorFragmentTabManager.ARG_FILE_PATH)
        }
        LOG.info("VideoPreviewFragment.onCreate: filePath={}", filePath)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val path = filePath
        val ctx = requireContext()
        val ctrl = VideoPlaybackController(ctx).also { controller = it }
        return ComposeView(ctx).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    ProvideDarkMode {
                        VideoPreviewScreen(
                            filePath = path,
                            controller = ctrl,
                            onPlayerViewReady = { playerViewRef = it },
                            onScreenshot = { takeScreenshot() },
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val path = filePath ?: return
        val ctrl = controller ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val playlist = buildPlaylist(File(path))
            val startIndex = playlist.indexOfFirst { it == path }.coerceAtLeast(0)
            val uris = playlist.map { Uri.fromFile(File(it)) }
            withContext(Dispatchers.Main) {
                if (uris.isEmpty()) {
                    ctrl.setMediaItem(Uri.fromFile(File(path)))
                } else {
                    ctrl.setMediaItems(uris, startIndex)
                }
                ctrl.play()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 恢复亮度跟随系统
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            brightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller?.release()
        controller = null
        playerViewRef = null
    }

    // ── 截图 ─────────────────────────────────────────────────

    /**
     * 用 PixelCopy 把 PlayerView 当前的 SurfaceView 内容保存为 PNG 到视频同目录。
     */
    private fun takeScreenshot() {
        val playerView = playerViewRef ?: return
        val surface = playerView.videoSurfaceView as? SurfaceView ?: run {
            LOG.warn("PlayerView surface is not SurfaceView, cannot screenshot")
            return
        }
        val width = surface.width
        val height = surface.height
        if (width <= 0 || height <= 0) return
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PixelCopy.request(
                    surface,
                    bitmap,
                    { result ->
                        if (result == PixelCopy.SUCCESS) {
                            saveScreenshot(bitmap)
                        } else {
                            LOG.warn("PixelCopy failed: result={}", result)
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
            } else {
                LOG.warn("PixelCopy requires API 24+, current API={}", Build.VERSION.SDK_INT)
            }
        } catch (e: Exception) {
            LOG.error("Screenshot failed", e)
        }
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        val path = filePath ?: return
        val videoFile = File(path)
        val parent = videoFile.parentFile ?: return
        val ts = System.currentTimeMillis()
        val screenshotFile = File(parent, "${videoFile.nameWithoutExtension}_screenshot_$ts.png")
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                FileOutputStream(screenshotFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                LOG.info("Screenshot saved: {}", screenshotFile.absolutePath)
            }.onFailure { e ->
                LOG.error("Failed to save screenshot", e)
            }
        }
    }

    // ── 同目录视频播放列表 ────────────────────────────────────

    private suspend fun buildPlaylist(target: File): List<String> =
        withContext(Dispatchers.IO) {
            val parent = target.parentFile ?: return@withContext listOf(target.absolutePath)
            runCatching {
                parent.listFiles { f ->
                    f.isFile && f.extension.lowercase(Locale.US) in SUPPORTED_EXTENSIONS
                }?.map { it.absolutePath }?.sorted() ?: listOf(target.absolutePath)
            }.getOrDefault(listOf(target.absolutePath))
        }
}

// ════════════════════════════════════════════════════════════════
//                          顶层 Composable
// ════════════════════════════════════════════════════════════════

@Composable
private fun VideoPreviewScreen(
    filePath: String?,
    controller: VideoPlaybackController,
    onPlayerViewReady: (PlayerView) -> Unit,
    onScreenshot: () -> Unit,
) {
    if (filePath.isNullOrBlank()) {
        VideoErrorState(message = "No file path provided")
        return
    }
    val file = remember(filePath) { File(filePath) }
    if (!file.exists() || !file.isFile) {
        VideoErrorState(message = "File not found:\n$filePath")
        return
    }

    val state by controller.state.collectAsState()
    val context = LocalContext.current

    // 加载外挂字幕
    var externalSubtitles by remember(file) { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var useExternalSubtitles by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        externalSubtitles = withContext(Dispatchers.IO) {
            VideoSubtitleController.loadFromFile(file)
        }
        // 若有外挂字幕, 默认启用
        if (externalSubtitles.isNotEmpty()) useExternalSubtitles = true
    }

    // 控件自动隐藏: 播放中且无操作 3 秒后隐藏
    LaunchedEffect(state.isPlaying, state.controlsVisible) {
        if (state.isPlaying && state.controlsVisible) {
            delay(VideoPreviewFragment.CONTROLS_AUTO_HIDE_MS)
            controller.setControlsVisible(false)
        }
    }

    // 手势回调
    val gestureCallbacks = remember(controller, context) {
        object : VideoGestureCallbacks {
            override fun onBrightnessChange(brightness: Float) {
                controller.setBrightness(brightness)
                (context as? Activity)?.window?.attributes = context.window?.attributes?.apply {
                    this.brightness = brightness
                }
            }
            override fun onVolumeChange(volume: Float) = controller.setVolume(volume)
            override fun onSeekDelta(deltaMs: Long) {
                val newPos = (controller.state.value.positionMs + deltaMs).coerceAtLeast(0L)
                controller.seekTo(newPos)
            }
            override fun onDoubleTap() {
                if (state.isPlaying) controller.pause() else controller.play()
            }
            override fun onSingleTap() {
                controller.toggleControls()
            }
            override fun onLongPressStart() {
                controller.setPlaybackSpeed(2.0f)
            }
            override fun onLongPressEnd() {
                controller.setPlaybackSpeed(1.0f)
            }
            override fun getInitialBrightness(): Float {
                val lp = (context as? Activity)?.window?.attributes
                return lp?.brightness?.takeIf { it > 0 } ?: 0.5f
            }
            override fun getInitialVolume(): Float = state.volume
            override fun getInitialPositionMs(): Long = state.positionMs
        }
    }

    val gestureHandler = remember { VideoGestureHandler() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ── PlayerView (AndroidView) ──────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // 我们用 Compose 自定义控件
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    player = controller.player
                    onPlayerViewReady(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── 手势层 (透明, 覆盖整个屏幕) ───────────────────────
        // 用一个估算的 IntSize; 实际值在 measure 后才有, 这里用 1080x1920 占位
        val screenSize = IntSize(1080, 1920)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureHandler.gestureModifier(screenSize, gestureCallbacks)),
        )

        // ── 字幕 overlay ─────────────────────────────────────
        val currentSub = if (useExternalSubtitles && externalSubtitles.isNotEmpty()) {
            VideoSubtitleController.currentSubtitle(state.positionMs, externalSubtitles)
        } else null
        if (currentSub != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                SubtitleOverlay(
                    text = currentSub,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 100.dp, start = 24.dp, end = 24.dp),
                )
            }
        }

        // ── 顶部信息栏 (自动隐藏) ─────────────────────────────
        AnimatedVisibility(
            visible = state.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            VideoTopBar(
                file = file,
                state = state,
                onScreenshot = onScreenshot,
            )
        }

        // ── 中央播放按钮 (暂停时显示) ─────────────────────────
        AnimatedVisibility(
            visible = state.controlsVisible && !state.isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            FrostedGlass(
                cornerRadius = 36.dp,
                contentPadding = PaddingValues(8.dp),
            ) {
                FrostedIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    onClick = { controller.play() },
                    size = 56.dp,
                )
            }
        }

        // ── 底部控制栏 (自动隐藏) ─────────────────────────────
        AnimatedVisibility(
            visible = state.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            VideoControlBar(
                state = state,
                hasExternalSubtitles = externalSubtitles.isNotEmpty(),
                onPlayPause = {
                    if (state.isPlaying) controller.pause() else controller.play()
                },
                onPrevious = { controller.seekToPrevious() },
                onNext = { controller.seekToNext() },
                onRepeat = { controller.toggleRepeatMode() },
                onSpeed = { cycleSpeed(controller, state.playbackSpeed) },
                onSeek = { controller.seekTo((it * state.durationMs).toLong()) },
                onToggleSubtitles = { showSubtitlePicker = !showSubtitlePicker },
            )
        }

        // ── 字幕轨道选择浮层 ─────────────────────────────────
        AnimatedVisibility(
            visible = showSubtitlePicker,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            SubtitlePickerPanel(
                state = state,
                hasExternalSubtitles = externalSubtitles.isNotEmpty(),
                useExternalSubtitles = useExternalSubtitles,
                onSelectBuiltIn = { idx ->
                    useExternalSubtitles = false
                    controller.selectSubtitle(idx)
                    showSubtitlePicker = false
                },
                onSelectExternal = {
                    useExternalSubtitles = true
                    controller.selectSubtitle(-1) // 关闭内嵌
                    showSubtitlePicker = false
                },
                onDismiss = { showSubtitlePicker = false },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════
//                          子组件
// ════════════════════════════════════════════════════════════════

/**
 * 顶部信息条: 文件名 + 时长 + 截图按钮。
 */
@Composable
private fun VideoTopBar(
    file: File,
    state: VideoPlaybackState,
    onScreenshot: () -> Unit,
) {
    val sizeText = remember(file) { humanReadableSize(file.length()) }
    val durationText = remember(state.durationMs) { formatTime(state.durationMs) }
    val resolutionText = if (state.videoWidth > 0 && state.videoHeight > 0) {
        "${state.videoWidth}x${state.videoHeight}"
    } else "—"

    FrostedGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FrostedText(
                    text = file.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                FrostedIconButton(
                    icon = Icons.Filled.Fullscreen,
                    contentDescription = "Screenshot",
                    onClick = onScreenshot,
                    size = 32.dp,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            FrostedText(
                text = "$resolutionText  |  $sizeText  |  $durationText",
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * 底部控制栏: 进度条 + 播放控制 + 倍速 + 字幕。
 */
@Composable
private fun VideoControlBar(
    state: VideoPlaybackState,
    hasExternalSubtitles: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onSpeed: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleSubtitles: () -> Unit,
) {
    val positionText = remember(state.positionMs) { formatTime(state.positionMs) }
    val durationText = remember(state.durationMs) { formatTime(state.durationMs) }
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val repeatIcon = when (state.repeatMode) {
        Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
        else -> Icons.Filled.Repeat
    }
    val repeatActive = state.repeatMode != Player.REPEAT_MODE_OFF
    val speedText = remember(state.playbackSpeed) {
        if (state.playbackSpeed == 1.0f) "1x" else "${state.playbackSpeed}x"
    }
    val subtitleActive = state.selectedSubtitleIndex >= 0 || hasExternalSubtitles

    FrostedGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 进度条
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrostedText(text = positionText, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(8.dp))
                FrostedSlider(
                    value = progress,
                    onValueChange = onSeek,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                FrostedText(text = durationText, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 按钮区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左: 循环 / 随机
                FrostedToggleIconButton(
                    icon = repeatIcon,
                    contentDescription = "Repeat",
                    active = repeatActive,
                    onClick = onRepeat,
                    size = 36.dp,
                )

                // 中: 上一首 / 播放 / 下一首
                FrostedIconButton(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    onClick = onPrevious,
                    size = 36.dp,
                )
                FrostedIconButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    onClick = onPlayPause,
                    size = 44.dp,
                )
                FrostedIconButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    onClick = onNext,
                    size = 36.dp,
                )

                // 右: 倍速 / 音量 / 字幕
                FrostedIconButton(
                    icon = Icons.Filled.Speed,
                    contentDescription = "Playback speed",
                    onClick = onSpeed,
                    size = 36.dp,
                )
                FrostedIconButton(
                    icon = Icons.Filled.VolumeUp,
                    contentDescription = "Volume",
                    onClick = { /* 由手势控制, 这里仅显示 */ },
                    size = 36.dp,
                )
                FrostedToggleIconButton(
                    icon = Icons.Filled.Subtitles,
                    contentDescription = "Subtitles",
                    active = subtitleActive,
                    onClick = onToggleSubtitles,
                    size = 36.dp,
                )
            }

            // 倍速显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                FrostedText(text = "Speed: $speedText", fontSize = 10.sp)
            }
        }
    }
}

/**
 * 字幕轨道选择面板。
 */
@Composable
private fun SubtitlePickerPanel(
    state: VideoPlaybackState,
    hasExternalSubtitles: Boolean,
    useExternalSubtitles: Boolean,
    onSelectBuiltIn: (Int) -> Unit,
    onSelectExternal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = LocalDarkMode.current
    val activeColor = if (isDark) Color(0xFF82B1FF) else Color(0xFF2979FF)

    FrostedGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrostedText(text = "Subtitles", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                FrostedIconButton(
                    icon = Icons.Filled.FullscreenExit,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    size = 28.dp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                // 关闭字幕
                item {
                    SubtitleOptionRow(
                        text = "Off",
                        isSelected = state.selectedSubtitleIndex < 0 && !useExternalSubtitles,
                        activeColor = activeColor,
                        onClick = { onSelectBuiltIn(-1) },
                    )
                }
                // 外挂字幕
                if (hasExternalSubtitles) {
                    item {
                        SubtitleOptionRow(
                            text = "External (.srt/.vtt)",
                            isSelected = useExternalSubtitles,
                            activeColor = activeColor,
                            onClick = onSelectExternal,
                        )
                    }
                }
                // 内嵌字幕轨道
                items(state.availableSubtitles) { track ->
                    SubtitleOptionRow(
                        text = track.label + (track.language?.let { " ($it)" } ?: ""),
                        isSelected = !useExternalSubtitles && state.selectedSubtitleIndex == track.trackIndex,
                        activeColor = activeColor,
                        onClick = { onSelectBuiltIn(track.trackIndex) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleOptionRow(
    text: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isSelected) activeColor.copy(alpha = 0.2f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onClick = onClick,
    ) {
        Text(
            text = text,
            color = if (isSelected) activeColor else Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/**
 * 字幕文本 overlay。
 */
@Composable
private fun SubtitleOverlay(text: String, modifier: Modifier = Modifier) {
    val isDark = LocalDarkMode.current
    val bgColor = if (isDark) Color.Black.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * 视频错误状态。
 */
@Composable
private fun VideoErrorState(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(24.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = null,
            tint = Color(0xFFE57373),
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = message,
            color = Color(0xFFE0E0E0),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// ════════════════════════════════════════════════════════════════
//                          辅助函数
// ════════════════════════════════════════════════════════════════

private fun cycleSpeed(controller: VideoPlaybackController, currentSpeed: Float) {
    val idx = VideoPreviewFragment.SPEED_OPTIONS.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.01f }
    val next = VideoPreviewFragment.SPEED_OPTIONS[(idx + 1).mod(VideoPreviewFragment.SPEED_OPTIONS.size)]
    controller.setPlaybackSpeed(next)
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun humanReadableSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var size = bytes.toDouble() / 1024
    var unitIdx = 0
    while (size >= 1024 && unitIdx < units.size - 1) {
        size /= 1024
        unitIdx++
    }
    return String.format(Locale.US, "%.1f %s", size, units[unitIdx])
}
