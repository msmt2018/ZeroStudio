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

package com.itsaky.androidide.fragments.editor.audio

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager
import com.itsaky.androidide.fragments.editor.components.FrostedGlass
import com.itsaky.androidide.fragments.editor.components.FrostedIconButton
import com.itsaky.androidide.fragments.editor.components.FrostedSlider
import com.itsaky.androidide.fragments.editor.components.FrostedText
import com.itsaky.androidide.fragments.editor.components.FrostedToggleIconButton
import com.itsaky.androidide.ui.compose.LocalDarkMode
import com.itsaky.androidide.ui.compose.ProvideDarkMode
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * 音频预览 fragment —— 基于 Media3 ExoPlayer + Compose 磨砂玻璃控件。
 *
 * ## 支持格式 (14 种)
 * mp3 / wav / ogg / flac / aac / m4a / opus / mid / midi / amr / pcm / aiff / ape / wma
 *
 * ## 功能
 * - 基础: 播放/暂停/seek/上一首/下一首/列表循环/单曲循环/随机/倍速
 * - 频谱可视化 (32 频段 FFT 柱状图, 实时绘制)
 * - LRC 歌词同步 (自动滚动到当前行, 突出当前行)
 * - 均衡器 (5+ 预设, 自定义频段)
 * - 元数据显示 (文件名 / 格式 / 大小 / 时长 / 当前位置)
 * - 同目录音频文件自动加入播放列表
 *
 * ## 主题感知
 * 所有 UI 控件全部用磨砂玻璃效果 (FrostedGlass), blur radius 固定不随主题切换,
 * 仅 tint 透明度 + 图标/文本颜色随 [LocalDarkMode] 变化,
 * 保证黑白主题下磨砂玻璃视觉效果一致, 图标清晰可见。
 *
 * @author ZeroStudio
 */
class AudioPreviewFragment : Fragment() {

    companion object {
        const val TAG = "AudioPreviewFragment"
        const val TAB_TITLE = "Audio Preview"

        /**
         * 支持的音频扩展名 (小写, 不含 `.`)。
         * 用于 FragmentTabEntry.fileExtensions + 同目录播放列表扫描。
         */
        val SUPPORTED_EXTENSIONS: Set<String> = setOf(
            "mp3", "wav", "ogg", "flac", "aac", "m4a", "opus",
            "mid", "midi", "amr", "pcm", "aiff", "ape", "wma",
        )

        /** 倍速档位。 */
        val SPEED_OPTIONS: FloatArray = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

        @JvmStatic
        fun newInstance(filePath: String): AudioPreviewFragment {
            return AudioPreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(EditorFragmentTabManager.ARG_FILE_PATH, filePath)
                }
            }
        }
    }

    private var filePath: String? = null
    private var controller: AudioPlaybackController? = null
    private val LOG = LoggerFactory.getLogger(TAG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            filePath = args.getString(EditorFragmentTabManager.ARG_FILE_PATH)
        }
        LOG.info("AudioPreviewFragment.onCreate: filePath={}", filePath)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val path = filePath
        val ctx = requireContext()
        // AudioPlaybackController 必须在 Compose 渲染前创建, 让 Composable 第一帧就有数据源
        val ctrl = AudioPlaybackController(ctx).also { controller = it }
        return ComposeView(ctx).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    ProvideDarkMode {
                        AudioPreviewScreen(filePath = path, controller = ctrl)
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val path = filePath ?: return
        val ctrl = controller ?: return
        // 异步扫描同目录音频列表 + 设置播放列表
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
        controller?.release()
        controller = null
    }

    // ── 内部: 同目录音频播放列表 ──────────────────────────────

    /**
     * 扫描 [target] 所在目录下的所有支持的音频文件, 按文件名排序。
     * 仅用扩展名匹配 (不调用 MediaExtractor, 避免逐文件嗅探耗时)。
     */
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

/**
 * 音频预览主屏 —— 频谱 + 歌词 + 元数据 + 磨砂玻璃控制栏。
 *
 * 布局自上而下:
 * 1. 顶部元数据条 (文件名 / 格式 / 大小 / 时长)
 * 2. 频谱可视化 Canvas (32 频段 FFT 柱状图)
 * 3. 歌词区 (LazyColumn, 自动滚动)
 * 4. 底部磨砂玻璃控制栏 (进度条 + 播放/上下首/循环/随机/倍速 + EQ/歌词切换)
 */
@Composable
private fun AudioPreviewScreen(filePath: String?, controller: AudioPlaybackController) {
    if (filePath.isNullOrBlank()) {
        AudioErrorState(message = "No file path provided")
        return
    }
    val file = remember(filePath) { File(filePath) }
    if (!file.exists() || !file.isFile) {
        AudioErrorState(message = "File not found:\n$filePath")
        return
    }

    val playbackState by controller.state.collectAsState()
    val isDark = LocalDarkMode.current

    // 延迟初始化 visualizer / equalizer (需要 audioSessionId, 在 player prepared 后才有)
    var visualizer by remember { mutableStateOf<AudioVisualizer?>(null) }
    var equalizer by remember { mutableStateOf<EqualizerController?>(null) }
    var lyrics by remember(file) { mutableStateOf<List<LyricLine>>(emptyList()) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(true) }

    // 加载歌词 (从同名 .lrc 文件)
    LaunchedEffect(file) {
        lyrics = withContext(Dispatchers.IO) { LyricSyncController.loadFromFile(file) }
    }

    // 当播放器首次 ready (durationMs > 0 表示已 prepared) 时, 创建 visualizer/equalizer
    LaunchedEffect(playbackState.durationMs) {
        if (playbackState.durationMs > 0 && visualizer == null && equalizer == null) {
            val sessionId = controller.audioSessionId
            if (sessionId != 0) {
                visualizer = AudioVisualizer(sessionId)
                equalizer = EqualizerController(sessionId)
            }
        }
    }

    // 播放状态联动 visualizer: 播放时 start, 暂停/停止时 stop
    LaunchedEffect(playbackState.isPlaying) {
        if (playbackState.isPlaying) visualizer?.start() else visualizer?.stop()
    }

    // 释放 visualizer / equalizer
    DisposableEffect(Unit) {
        onDispose {
            visualizer?.release()
            equalizer?.release()
        }
    }

    val bgColor = if (isDark) Color(0xFF0E0E10) else Color(0xFFFAFAFA)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AudioMetadataBar(file = file, state = playbackState)
            if (visualizer != null) {
                SpectrumCanvas(
                    visualizer = visualizer!!,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                // 加载阶段占位
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FrostedText(text = "Initializing audio session...", fontSize = 12.sp)
                }
            }

            // 歌词区 (可折叠)
            if (showLyrics && lyrics.isNotEmpty()) {
                LyricsView(
                    lyrics = lyrics,
                    positionMs = playbackState.positionMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // EQ 浮层 (从底部滑入)
            AnimatedVisibility(
                visible = showEqualizer && equalizer != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                equalizer?.let { EqualizerPanel(controller = it) }
            }

            // 底部磨砂玻璃控制栏
            FrostedControlBar(
                state = playbackState,
                showEqualizer = showEqualizer,
                showLyrics = showLyrics,
                onPlayPause = {
                    if (playbackState.isPlaying) controller.pause() else controller.play()
                },
                onPrevious = { controller.seekToPrevious() },
                onNext = { controller.seekToNext() },
                onRepeat = { controller.toggleRepeatMode() },
                onShuffle = { controller.toggleShuffle() },
                onSpeed = { cycleSpeed(controller, playbackState.playbackSpeed) },
                onSeek = { controller.seekTo((it * playbackState.durationMs).toLong()) },
                onToggleEqualizer = { showEqualizer = !showEqualizer; showLyrics = false },
                onToggleLyrics = { showLyrics = !showLyrics; showEqualizer = false },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════
//                          子组件
// ════════════════════════════════════════════════════════════════

/**
 * 顶部元数据条 —— 文件名 / 格式 / 大小 / 时长 / 当前位置。
 * 使用磨砂玻璃样式, 与底部控制栏视觉一致。
 */
@Composable
private fun AudioMetadataBar(file: File, state: PlaybackState) {
    val sizeText = remember(file) { humanReadableSize(file.length()) }
    val formatText = remember(file) { file.extension.uppercase(Locale.US) }
    val durationText = remember(state.durationMs) { formatTime(state.durationMs) }
    val positionText = remember(state.positionMs) { formatTime(state.positionMs) }

    FrostedGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTinted(icon = Icons.Filled.MusicNote)
                Spacer(modifier = Modifier.width(8.dp))
                FrostedText(
                    text = file.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            FrostedText(
                text = "$formatText  |  $sizeText  |  $durationText  |  $positionText",
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * 频谱可视化 Canvas —— 32 频段 FFT 柱状图, 实时绘制。
 *
 * - 柱状从底部向上生长, 顶部圆角
 * - 颜色随主题切换 (暗色: 半透明白; 亮色: 半透明黑 + 渐变)
 * - 静止时显示一条平的低线 (避免空荡)
 */
@Composable
private fun SpectrumCanvas(
    visualizer: AudioVisualizer,
    modifier: Modifier = Modifier,
) {
    val waveform by visualizer.waveform.collectAsState()
    val isDark = LocalDarkMode.current

    val barColor = if (isDark) Color(0xCC82B1FF) else Color(0xCC2979FF)
    val barColorDim = if (isDark) Color(0x3382B1FF) else Color(0x332979FF)

    Canvas(modifier = modifier) {
        val bands = waveform
        val count = bands.size.coerceAtLeast(1)
        val totalWidth = size.width
        val totalHeight = size.height
        val gap = 2.dp.toPx()
        val barWidth = (totalWidth - gap * (count - 1)) / count
        val minHeight = 2.dp.toPx()
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

        for (i in 0 until count) {
            val amplitude = bands[i].coerceIn(0f, 1f)
            val barHeight = (amplitude * totalHeight).coerceAtLeast(minHeight)
            val x = i * (barWidth + gap)
            val y = totalHeight - barHeight

            // 渐变: 底部更亮, 顶部更淡
            val brush = Brush.verticalGradient(
                colors = listOf(
                    barColor,
                    barColorDim,
                ),
                startY = totalHeight,
                endY = y,
            )
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }

        // 底部基线
        drawLine(
            color = barColorDim,
            start = Offset(0f, totalHeight - 0.5f),
            end = Offset(totalWidth, totalHeight - 0.5f),
            strokeWidth = 1f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 歌词视图 —— LazyColumn, 当前播放行高亮, 自动滚动到当前行。
 */
@Composable
private fun LyricsView(
    lyrics: List<LyricLine>,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val currentLine = remember(positionMs, lyrics) {
        LyricSyncController.currentLine(positionMs, lyrics)
    }
    val listState = rememberLazyListState()
    val isDark = LocalDarkMode.current

    // 当前行变化时滚动到当前行
    LaunchedEffect(currentLine) {
        if (currentLine in lyrics.indices) {
            listState.animateScrollToItem(currentLine)
        }
    }

    val activeColor = if (isDark) Color(0xFFFFD54F) else Color(0xFFE65100)
    val inactiveColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f)

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 60.dp),
    ) {
        items(lyrics) { line ->
            val isActive = line == lyrics.getOrNull(currentLine)
            val color = if (isActive) activeColor else inactiveColor
            val weight = if (isActive) FontWeight.Bold else FontWeight.Normal
            val size = if (isActive) 17.sp else 14.sp
            Text(
                text = line.text,
                color = color,
                fontSize = size,
                fontWeight = weight,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 16.dp),
            )
        }
    }
}

/**
 * 均衡器面板 —— 预设选择 + 频段垂直滑块 + 启用开关。
 * 用磨砂玻璃容器包装。
 */
@Composable
private fun EqualizerPanel(controller: EqualizerController) {
    val eqState by controller.state.collectAsState()
    val isDark = LocalDarkMode.current
    val scrollState = rememberScrollState()

    FrostedGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 顶部: 启用开关 + 预设名
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTinted(icon = Icons.Filled.Equalizer)
                    Spacer(modifier = Modifier.width(6.dp))
                    FrostedText(text = "Equalizer", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FrostedText(
                        text = if (eqState.enabled) "ON" else "OFF",
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FrostedToggleIconButton(
                        icon = Icons.Filled.GraphicEq,
                        contentDescription = "Toggle EQ",
                        active = eqState.enabled,
                        onClick = { controller.setEnabled(!eqState.enabled) },
                        size = 32.dp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 预设横向滚动条
            if (eqState.presets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    eqState.presets.forEach { preset ->
                        val isActive = preset == eqState.currentPreset
                        val activeColor = if (isDark) Color(0xFF82B1FF) else Color(0xFF2979FF)
                        Surface(
                            color = if (isActive) activeColor.copy(alpha = 0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (isActive) activeColor else Color.Gray.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.clickable { controller.applyPreset(preset) },
                        ) {
                            Text(
                                text = preset,
                                fontSize = 11.sp,
                                color = if (isActive) activeColor else Color.Gray,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 频段垂直滑块 (横向排列)
            if (eqState.bands.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    eqState.bands.forEach { band ->
                        EqualizerBandSlider(
                            band = band,
                            enabled = eqState.enabled,
                            onLevelChange = { level ->
                                controller.setBandLevel(band.index, level)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个 EQ 频段垂直滑块 + 频率标签。
 *
 * 由于 Material3 Slider 默认水平, 通过 [graphicsLayer] 旋转 -90° 实现垂直方向。
 */
@Composable
private fun EqualizerBandSlider(
    band: EqualizerBand,
    enabled: Boolean,
    onLevelChange: (Short) -> Unit,
    modifier: Modifier = Modifier,
) {
    val valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat()
    val currentValue = band.currentLevel.toFloat()
    val freqText = remember(band.centerFreqHz) {
        if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000}k" else "${band.centerFreqHz}"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            FrostedSlider(
                value = currentValue,
                onValueChange = { onLevelChange(it.toInt().toShort()) },
                valueRange = valueRange,
                enabled = enabled,
                modifier = Modifier
                    .width(80.dp)
                    .height(20.dp)
                    .graphicsLayer(rotationZ = -90f),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        FrostedText(text = freqText, fontSize = 10.sp)
    }
}

/**
 * 底部磨砂玻璃控制栏 —— 进度条 + 播放控制 + 倍速 + EQ/歌词切换。
 */
@Composable
private fun FrostedControlBar(
    state: PlaybackState,
    showEqualizer: Boolean,
    showLyrics: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onSpeed: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleEqualizer: () -> Unit,
    onToggleLyrics: () -> Unit,
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

    FrostedGlass(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        cornerRadius = 28.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 错误信息
            state.errorMessage?.let { msg ->
                Text(
                    text = "Error: $msg",
                    color = Color(0xFFEF5350),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

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

            // 按钮区: 左循环/随机 + 中播放控制 + 右倍速/歌词/EQ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧: 循环 / 随机
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FrostedToggleIconButton(
                        icon = repeatIcon,
                        contentDescription = "Repeat",
                        active = repeatActive,
                        onClick = onRepeat,
                        size = 36.dp,
                    )
                    FrostedToggleIconButton(
                        icon = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        active = state.shuffleMode,
                        onClick = onShuffle,
                        size = 36.dp,
                    )
                }

                // 中间: 上一首 / 播放-暂停 / 下一首
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                }

                // 右侧: 倍速 / 歌词 / EQ
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FrostedIconButton(
                        icon = Icons.Filled.Speed,
                        contentDescription = "Playback speed",
                        onClick = onSpeed,
                        size = 36.dp,
                    )
                    FrostedToggleIconButton(
                        icon = Icons.Filled.Lyrics,
                        contentDescription = "Lyrics",
                        active = showLyrics,
                        onClick = onToggleLyrics,
                        size = 36.dp,
                    )
                    FrostedToggleIconButton(
                        icon = Icons.Filled.Equalizer,
                        contentDescription = "Equalizer",
                        active = showEqualizer,
                        onClick = onToggleEqualizer,
                        size = 36.dp,
                    )
                }
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
 * 主题感知小图标 (用于磨砂玻璃容器内的辅助图标, 非 IconButton)。
 */
@Composable
private fun IconTinted(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkMode.current
    val color = if (isDark) Color.White.copy(alpha = 0.95f) else Color.Black.copy(alpha = 0.85f)
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = modifier.size(18.dp),
    )
}

/**
 * 音频错误状态占位。
 */
@Composable
private fun AudioErrorState(message: String) {
    val isDark = LocalDarkMode.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFFAFAFA))
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
            color = if (isDark) Color(0xFFE0E0E0) else Color(0xFF333333),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// ════════════════════════════════════════════════════════════════
//                          辅助函数
// ════════════════════════════════════════════════════════════════

/**
 * 循环切换倍速: 0.5x → 0.75x → 1.0x → 1.25x → 1.5x → 1.75x → 2.0x → 0.5x ...
 */
private fun cycleSpeed(controller: AudioPlaybackController, currentSpeed: Float) {
    val idx = AudioPreviewFragment.SPEED_OPTIONS.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.01f }
    val next = AudioPreviewFragment.SPEED_OPTIONS[(idx + 1).mod(AudioPreviewFragment.SPEED_OPTIONS.size)]
    controller.setPlaybackSpeed(next)
}

/** 把毫秒格式化为 mm:ss 或 h:mm:ss。 */
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

/** 人类可读的文件大小 (B/KB/MB/GB)。 */
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
