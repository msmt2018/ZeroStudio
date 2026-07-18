/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.fragments.editor.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import android.graphics.BitmapFactory
import android.zero.studio.layouteditor.vectormaster.VectorMasterDrawable
import androidx.annotation.RequiresApi
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.svg.SvgDecoder
import com.itsaky.androidide.file.FileValidator
import com.itsaky.androidide.file.MimeTypeConstants
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager
import com.itsaky.androidide.onboarding.effects.frostedGlass
import com.itsaky.androidide.ui.SymbolInputVisibilityManager
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * 通用图片预览 fragment —— 支持 Android XML vector drawable / SVG / PNG / JPG /
 * WebP / GIF / BMP / HEIC / AVIF / ICO / TIFF 等所有常见格式.
 *
 * ## 设计要点
 *
 * - **Compose-based**: 与 [com.itsaky.androidide.fragments.editor.markdown.MarkdownPreviewFragment]
 *   风格一致, 直接 `ComposeView` `setContent`, 通过 `produceState` +
 *   `Dispatchers.IO` 异步解码图片, 不阻塞主线程.
 * - **三套解码器**:
 *   - Android XML vector (`*.xml` 且根标签是 `<vector>`) 走
 *     [VectorMasterDrawable] (xml/vectormaster), 不依赖
 *     `AppCompatResources.getDrawable`, 直接从 raw `File` 解析.
 *   - SVG (`*.svg` / `*.svgz`) 走 Coil 3.x 的 [SvgDecoder] (底层用
 *     AndroidSVG).
 *   - 其余位图 (PNG / JPG / WebP / HEIC / GIF / BMP / AVIF / ICO / TIFF ...) 走
 *     Coil 3.x 的默认 decoder (`ImageDecoderDecoder` + `GifDecoder`).
 * - **缩放/平移**: 双指捏合 + 单指拖动, 用 [detectTransformGestures] 实现.
 *   缩放范围 0.1x ~ 10x, 双击还原.
 * - **复用 Compose 生态**: 不引 WebView, 不引 Glide, 仅依赖已经在 toml 中
 *   的 Coil 3.x (`io.coil.core` / `io.coil.svg` / `io.coil.gif`).
 *
 * ## 注册方式
 *
 * 跟 [com.itsaky.androidide.fragments.editor.markdown.MarkdownPreviewFragment]
 * 一样, 通过 [com.itsaky.androidide.fragments.editor.FragmentTabRegistry] 在
 * [com.itsaky.androidide.activities.editor.EditorHandlerActivity] 注册.
 *
 * @author android_zero
 */
class ImagePreviewFragment : Fragment() {

    companion object {
        const val TAG = "ImagePreviewFragment"
        const val ARG_FILE_PATH = "file_path"
        const val TAB_TITLE = "Image Preview"

        /**
         * 扩展名集合 (小写, 不含 `.`), 用于
         * [com.itsaky.androidide.fragments.editor.FragmentTabEntry.fileExtensions].
         *
         * - [RASTER_DECODER_FORMATS] —— 系统 [BitmapFactory] / Coil 默认 decoder
         *   能解的位图.
         * - [SVG_FORMATS] —— SVG / SVGZ, 走 [SvgDecoder].
         * - [XML_VECTOR_FORMATS] —— Android XML vector drawable (根标签是
         *   `<vector>`), 走 [VectorMasterDrawable]. 注意: 普通 `*.xml` 文件
         *   (布局 / manifest / values) 不在本集合里, 它们由
         *   [isLikelyAndroidVector] 在加载时做 content sniff.
         * - [SUPPORTED_FORMATS] —— 三者并集.
         */
        val RASTER_DECODER_FORMATS: Set<String> = setOf(
            "png", "jpg", "jpeg", "jpe", "jfif",
            "webp", "gif", "bmp", "heic", "heif", "avif",
            "ico", "cur", "tiff", "tif",
        )
        val SVG_FORMATS: Set<String> = setOf("svg", "svgz")
        val XML_VECTOR_FORMATS: Set<String> = setOf("xml")

        val SUPPORTED_FORMATS: Set<String> =
            RASTER_DECODER_FORMATS + SVG_FORMATS + XML_VECTOR_FORMATS

        /**
         * 工厂: 按文件路径创建 fragment.
         *
         * @param filePath 绝对路径. 文件后缀必须属于 [SUPPORTED_FORMATS] 中之一,
         *                 或者文件内容被 [FileValidator.isLikelyAndroidVector] 识别为
         *                 vector, 否则 fragment 打开后会展示错误.
         */
        @JvmStatic
        fun newInstance(filePath: String): ImagePreviewFragment {
            return ImagePreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(EditorFragmentTabManager.ARG_FILE_PATH, filePath)
                }
            }
        }
    }

    private var filePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            filePath = args.getString(EditorFragmentTabManager.ARG_FILE_PATH)
        }
        LOG.info("ImagePreviewFragment.onCreate: filePath={}", filePath)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme {
                ImagePreviewScreen(filePath = filePath)
            }
        }
    }

    // === Bug 5.1: 进入图片预览时隐藏符号输入控件, 退出时恢复 ===
    // 图片预览 fragment 底部原本会被 AdvancedSymbolInputView 遮挡, 这里在
    // 可见性变化时通过 SymbolInputVisibilityManager 把符号输入控件 + header
    // 状态栏 + 分隔线全部 GONE, 只保留 EdgeSnapBubbleView 在屏幕可见区域.
    // 上滑气泡时 EditorBottomSheet.drawerDragListener 会调用 showFromPreview() 恢复.

    override fun onResume() {
        super.onResume()
        SymbolInputVisibilityManager.hideForPreview()
    }

    override fun onPause() {
        super.onPause()
        SymbolInputVisibilityManager.showFromPreview()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            SymbolInputVisibilityManager.showFromPreview()
        } else {
            SymbolInputVisibilityManager.hideForPreview()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        SymbolInputVisibilityManager.showFromPreview()
    }
}

// region 顶层 Composable

/** 预览背景模式. */
private enum class BackgroundMode(val label: String) {
    CHECKER("Checker"),
    WHITE("White"),
    BLACK("Black"),
    GRAY("Gray");

    fun next(): BackgroundMode = when (this) {
        CHECKER -> WHITE
        WHITE -> BLACK
        BLACK -> GRAY
        GRAY -> CHECKER
    }
}

/** 画布适应模式 (映射到 Compose [ContentScale]). */
private enum class FitMode(val label: String, val contentScale: ContentScale) {
    FIT("Fit", ContentScale.Fit),
    FILL("Fill", ContentScale.FillBounds),
    CROP("Crop", ContentScale.Crop),
    NONE("None", ContentScale.None),
}

/**
 * 顶层 Compose 屏 —— 异步读图 + 缩放/平移 + 错误/加载状态 + 工具栏
 * (背景色切换 / 透明度滑块 / 文件信息 / Canvas 帆布设置).
 */
@Composable
private fun ImagePreviewScreen(filePath: String?) {
    if (filePath.isNullOrBlank()) {
        ErrorState(message = "No file path provided")
        return
    }
    val file = remember(filePath) { File(filePath) }
    if (!file.exists() || !file.isFile) {
        ErrorState(message = "File not found:\n$filePath")
        return
    }

    // 异步加载: 拿到 ImageData 给 UI 显示
    // 注意: appCtx 是 @Composable getter, 不能在 produceState 的 suspend 块内调用,
    // 必须先在 Composable 作用域里取值, 再传给 loadImage.
    val ctx = LocalContext.current.applicationContext
    val state by produceState<ImageUiState>(initialValue = ImageUiState.Loading, file) {
        value = runCatching { loadImage(ctx, file) }.fold(
            onSuccess = { ImageUiState.Loaded(bitmap = it.bitmap, mime = it.mime) },
            onFailure = { ImageUiState.Error(it.message ?: it.javaClass.simpleName) },
        )
    }

    // === 工具栏状态 ===
    var backgroundMode by remember { mutableStateOf(BackgroundMode.CHECKER) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var fitMode by remember { mutableStateOf(FitMode.FIT) }
    var showInfo by remember { mutableStateOf(false) }
    var showCanvas by remember { mutableStateOf(false) }
    var showOpacity by remember { mutableStateOf(false) }

    fun closeAllPanels() {
        showInfo = false
        showCanvas = false
        showOpacity = false
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        TopToolbar(
            file = file,
            state = state,
            backgroundMode = backgroundMode,
            onCycleBackground = { backgroundMode = backgroundMode.next() },
            onToggleOpacity = {
                val wasOpen = showOpacity
                closeAllPanels()
                showOpacity = !wasOpen
            },
            onToggleInfo = {
                val wasOpen = showInfo
                closeAllPanels()
                showInfo = !wasOpen
            },
            onToggleCanvas = {
                val wasOpen = showCanvas
                closeAllPanels()
                showCanvas = !wasOpen
            },
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // 1. 背景层 (白/黑/灰/透明棋盘)
            PreviewBackground(backgroundMode, Modifier.fillMaxSize())
            // 2. 图片层
            when (val s = state) {
                ImageUiState.Loading -> CircularProgressIndicator(color = Color.White)
                is ImageUiState.Error -> ErrorState(message = s.message)
                is ImageUiState.Loaded -> ZoomableImage(
                    bitmap = s.bitmap,
                    alpha = opacity,
                    rotation = rotation,
                    fitMode = fitMode,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // 3. 浮动设置面板 (frosted glass 风格)
            if (showOpacity) {
                OpacitySliderPanel(
                    opacity = opacity,
                    onOpacityChange = { opacity = it },
                )
            }
            if (showInfo) {
                FileInfoPanel(file = file, state = state)
            }
            if (showCanvas) {
                CanvasSettingsPanel(
                    rotation = rotation,
                    onRotationChange = { rotation = it },
                    fitMode = fitMode,
                    onFitModeChange = { fitMode = it },
                    onReset = {
                        rotation = 0f
                        fitMode = FitMode.FIT
                    },
                )
            }
        }
    }
}

/**
 * 顶部工具栏 —— 文件名 / 大小 + 工具按钮 (背景色 / 透明度 / 文件信息 / Canvas).
 */
@Composable
private fun TopToolbar(
    file: File,
    state: ImageUiState,
    backgroundMode: BackgroundMode,
    onCycleBackground: () -> Unit,
    onToggleOpacity: () -> Unit,
    onToggleInfo: () -> Unit,
    onToggleCanvas: () -> Unit,
) {
    val sizeText = remember(file) { humanReadableSize(file.length()) }
    Surface(
        color = Color(0xFF252526),
        contentColor = Color(0xFFCCCCCC),
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFFEEEEEE),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sizeText,
                    fontSize = 11.sp,
                    color = Color(0xFF9CDCFE),
                    fontFamily = FontFamily.Monospace,
                )
            }
            IconButton(onClick = onCycleBackground) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = "Background: ${backgroundMode.label}",
                    tint = Color(0xFFCCCCCC),
                )
            }
            IconButton(onClick = onToggleOpacity) {
                Icon(
                    imageVector = Icons.Outlined.Opacity,
                    contentDescription = "Opacity",
                    tint = Color(0xFFCCCCCC),
                )
            }
            IconButton(onClick = onToggleInfo) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "File info",
                    tint = Color(0xFFCCCCCC),
                )
            }
            IconButton(onClick = onToggleCanvas) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "Canvas settings",
                    tint = Color(0xFFCCCCCC),
                )
            }
        }
    }
}

/**
 * 预览背景层. CHECKER 模式绘制透明棋盘, 其余纯色填充.
 */
@Composable
private fun PreviewBackground(mode: BackgroundMode, modifier: Modifier = Modifier) {
    when (mode) {
        BackgroundMode.CHECKER -> CheckerboardBackground(modifier)
        BackgroundMode.WHITE -> Box(modifier.background(Color.White))
        BackgroundMode.BLACK -> Box(modifier.background(Color.Black))
        BackgroundMode.GRAY -> Box(modifier.background(Color(0xFF808080)))
    }
}

/**
 * 透明棋盘背景 —— 8x8 dp 交替灰白格子, 用于检视带 alpha 通道的图片.
 */
@Composable
private fun CheckerboardBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cell = 16.dp.toPx()
        val w = size.width
        val h = size.height
        var y = 0f
        var row = 0
        while (y < h) {
            var x = 0f
            var col = 0
            while (x < w) {
                if ((row + col) % 2 == 0) {
                    drawRect(
                        color = Color(0xFFE0E0E0),
                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(cell, cell),
                    )
                }
                x += cell
                col++
            }
            y += cell
            row++
        }
    }
}

/**
 * 错误占位 —— 居中的红色提示 + 错误信息.
 */
@Composable
private fun ErrorState(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(24.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.BrokenImage,
            contentDescription = null,
            tint = Color(0xFFE57373),
            modifier = Modifier.padding(8.dp),
        )
        Text(
            text = message,
            color = Color(0xFFE0E0E0),
            fontSize = 13.sp,
        )
    }
}

/**
 * 缩放 + 平移 + 旋转的图片视图.
 *
 * - 双指捏合: 缩放 (0.1x ~ 10x).
 * - 单指拖动: 平移.
 * - 双击: 还原 (scale=1, offset=0).
 * - [alpha] / [rotation] / [fitMode] 由顶部工具栏面板控制.
 */
@Composable
private fun ZoomableImage(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    rotation: Float = 0f,
    fitMode: FitMode = FitMode.FIT,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.1f, 10f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = null,
            contentScale = fitMode.contentScale,
            alpha = alpha,
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                    rotationZ = rotation,
                ),
        )
    }
}

// region 浮动设置面板 (FrostedGlass 风格)

/**
 * 透明度滑块面板 —— 0% ~ 100%, 浮于顶部居中.
 */
@Composable
private fun OpacitySliderPanel(
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .padding(9.dp)
                .width(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .frostedGlass(
                    shape = RoundedCornerShape(16.dp),
                    tint = Color(0xFF2D2D30),
                    alpha = 0.85f,
                )
                .padding(9.dp),
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Opacity,
                    contentDescription = null,
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Opacity",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(opacity * 100).toInt()}%",
                    color = Color(0xFF9CDCFE),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Slider(
                value = opacity,
                onValueChange = onOpacityChange,
                valueRange = 0f..1f,
            )
        }
    }
}

/**
 * 文件信息面板 —— 名称 / 大小 / 尺寸 / MIME / 路径, 浮于右上.
 */
@Composable
private fun FileInfoPanel(file: File, state: ImageUiState) {
    val sizeText = remember(file) { humanReadableSize(file.length()) }
    val dimensionText = when (val s = state) {
        is ImageUiState.Loaded -> "${s.bitmap.width} × ${s.bitmap.height} px"
        else -> "—"
    }
    val mimeText = when (state) {
        is ImageUiState.Loaded -> (state as ImageUiState.Loaded).mime
        else -> guessMimeByExt(file)
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = Modifier
                .padding(9.dp)
                .width(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .frostedGlass(
                    shape = RoundedCornerShape(16.dp),
                    tint = Color(0xFF2D2D30),
                    alpha = 0.85f,
                )
                .padding(9.dp),
        ) {
            InfoRow(label = "Name", value = file.name)
            InfoRow(label = "Size", value = sizeText)
            InfoRow(label = "Dimensions", value = dimensionText)
            InfoRow(label = "MIME", value = mimeText)
            InfoRow(label = "Path", value = file.absolutePath)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = Color(0xFF9CDCFE),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            color = Color(0xFFEEEEEE),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Canvas 帆布设置面板 —— 旋转角度 (0°~360°) + 适应模式 + 重置, 浮于右上.
 */
@Composable
private fun CanvasSettingsPanel(
    rotation: Float,
    onRotationChange: (Float) -> Unit,
    fitMode: FitMode,
    onFitModeChange: (FitMode) -> Unit,
    onReset: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = Modifier
                .padding(9.dp)
                .width(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .frostedGlass(
                    shape = RoundedCornerShape(16.dp),
                    tint = Color(0xFF2D2D30),
                    alpha = 0.85f,
                )
                .padding(9.dp),
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.RotateRight,
                    contentDescription = null,
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Rotation",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${rotation.toInt()}°",
                    color = Color(0xFF9CDCFE),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Slider(
                value = rotation,
                onValueChange = onRotationChange,
                valueRange = 0f..360f,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Fit Mode",
                color = Color.White,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FitMode.entries.forEach { mode ->
                    FilterChip(
                        selected = fitMode == mode,
                        onClick = { onFitModeChange(mode) },
                        label = { Text(mode.label, fontSize = 11.sp) },
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onReset) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Reset",
                        tint = Color(0xFFCCCCCC),
                    )
                }
            }
        }
    }
}

// endregion

// endregion

// region 数据模型 / 解码

/**
 * 加载结果 (内部) —— UI 层只看 [ImageUiState].
 */
private data class LoadedImage(
    val bitmap: Bitmap,
    val mime: String,
)

private sealed interface ImageUiState {
    data object Loading : ImageUiState
    data class Loaded(val bitmap: Bitmap, val mime: String) : ImageUiState
    data class Error(val message: String) : ImageUiState
}

// endregion

// region 解码逻辑 (Dispatchers.IO)

/**
 * 顶层加载入口 —— 根据文件后缀 / 内容分发到不同解码器.
 */
private suspend fun loadImage(ctx: Context, file: File): LoadedImage = withContext(Dispatchers.IO) {
    val name = file.name.lowercase(Locale.US)
    val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
    // 优先按"内容是不是 Android vector"判, 避免 layout XML 等被错路由
    if (ext == "xml" && FileValidator.isLikelyAndroidVector(file)) {
        decodeVectorXml(ctx, file)
    } else if (ext in ImagePreviewFragment.SVG_FORMATS) {
        decodeSvg(ctx, file)
    } else {
        decodeRaster(ctx, file)
    }
}

/**
 * 解析 Android XML vector drawable. 用 [VectorMasterDrawable] 而非
 * `AppCompatResources.getDrawable`, 因为后者要求 drawable 必须在 res/.
 *
 * 流程: VectorMasterDrawable 解析 XML → 取内在宽高 (或从 `<vector>` 头读
 * android:width/height) → 用 [Canvas] 渲染到 [Bitmap].
 */
private fun decodeVectorXml(ctx: Context, file: File): LoadedImage {
    val drawable = try {
        VectorMasterDrawable(ctx, file)
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to parse vector XML: ${e.message}", e)
    }
    // VectorMasterDrawable 默认 bounds 为 (0,0,0,0), 需要给它一个目标尺寸.
    // 优先用 intrinsicWidth/Height (VectorDrawable 通常是 24x24), 否则从
    // android:width/height 头读, 再否则 fallback 24dp*density.
    val density = ctx.resources.displayMetrics.density
    val intrinsicW = drawable.intrinsicWidth.takeIf { it > 0 }
    val intrinsicH = drawable.intrinsicHeight.takeIf { it > 0 }
    val (w, h) = if (intrinsicW != null && intrinsicH != null) {
        intrinsicW to intrinsicH
    } else {
        readVectorDimensions(file, density)
    }
    val targetW = w.coerceAtLeast(1)
    val targetH = h.coerceAtLeast(1)
    drawable.setBounds(0, 0, targetW, targetH)
    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.draw(canvas)
    return LoadedImage(bitmap = bmp, mime = "image/xml+vector")
}

/**
 * 从 vector XML 头部解析 `android:width` / `android:height`. 缺单位时按 dp;
 * 失败时回退 24dp.
 */
private fun readVectorDimensions(file: File, density: Float): Pair<Int, Int> {
    val fallback = (24f * density).toInt().coerceAtLeast(1)
    return try {
        val head = file.useLines { seq -> seq.take(20).joinToString("\n") }
        val w = attrPx(head, "android:width", density) ?: fallback
        val h = attrPx(head, "android:height", density) ?: fallback
        w.coerceAtLeast(1) to h.coerceAtLeast(1)
    } catch (e: Throwable) {
        Log.w(ImagePreviewFragment.TAG, "readVectorDimensions: fallback for $file", e)
        fallback to fallback
    }
}

/**
 * 简易 `android:width="24dp"` 解析. 缺单位时按 dp. 解析失败返回 null.
 */
private fun attrPx(xml: String, attr: String, density: Float): Int? {
    val regex = Regex("""\Q$attr\E\s*=\s*"([^"]+)"""")
    val m = regex.find(xml) ?: return null
    val raw = m.groupValues[1].trim()
    val isDp = raw.endsWith("dp") || raw.endsWith("dip")
    val isPx = raw.endsWith("px")
    val num = raw.removeSuffix("dp").removeSuffix("dip")
        .removeSuffix("px").trim()
        .toFloatOrNull() ?: return null
    return when {
        isPx -> num.toInt()
        else -> (num * density).toInt()  // dp / 无单位都按 dp 处理
    }
}

/**
 * 用 Coil 3.x 解码 SVG —— [SvgDecoder] 自动嗅探 `<svg` 标记. 解码得到
 * [Drawable] 后再用 [Canvas] 渲染到 [Bitmap], 让 Compose Image 直接吃
 * BitmapPainter, 缩放时无重影.
 */
private suspend fun decodeSvg(ctx: Context, file: File): LoadedImage {
    val loader = ImageLoader.Builder(ctx)
        .components {
            // 显式注册 SVG decoder, 避免依赖 service-loader 的自动发现
            // (Android 模块里 ServiceLoader 在某些打包配置下可能丢).
            add(SvgDecoder.Factory())
        }
        .build()
    val req = ImageRequest.Builder(ctx)
        .data(file)
        .build()
    val result = loader.execute(req)
    if (result !is SuccessResult) {
        throw IllegalStateException("Failed to decode SVG: ${result.javaClass.simpleName}")
    }
    val drawable: Drawable = coilImageToDrawable(result, ctx)
    val bmp = drawableToBitmap(drawable)
    return LoadedImage(bitmap = bmp, mime = MimeTypeConstants.IMAGE_SVG_XML)
}

/**
 * 解码位图 (PNG / JPG / WebP / GIF / HEIC / BMP / AVIF / ICO / TIFF ...) ——
 * 直接用 Android 原生 [BitmapFactory] / [ImageDecoder] 解码, 不依赖 Coil
 * 3.x 的组件注册机制.
 *
 * **原因**: 之前用 Coil 3.x 的 `ImageLoader.Builder.components { add(
 * BitmapFactoryDecoder.Factory()) }` 显式注册 decoder, 但 `components { }`
 * 会替换整个 ComponentRegistry (而非追加), 导致在某些打包配置下
 * BitmapFactoryDecoder 无法正确解析, PNG / JPG 等常规位图返回 ErrorResult,
 * 表现为"图片不显示". 改用原生 API 直接解码, 无依赖、无歧义.
 *
 * - API 28+: 用 [ImageDecoder] (支持 HEIC / AVIF / GIF 动图, 从回调取 Bitmap)
 * - API 26-27: 用 [BitmapFactory] (GIF 仅渲染首帧)
 *
 * 注: GIF 动图只渲染首帧, 不播放动画. 这是预览模式的设计限制.
 */
private fun decodeRaster(ctx: Context, file: File): LoadedImage {
    val mime = guessMimeByExt(file)
    val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeRasterWithImageDecoder(file)
    } else {
        BitmapFactory.decodeFile(file.absolutePath)
    }
    if (bmp == null) {
        throw IllegalStateException("Failed to decode bitmap: ${file.name}")
    }
    return LoadedImage(bitmap = bmp, mime = mime)
}

/** API 28+: 用 ImageDecoder 解码位图 (支持 HEIC / AVIF / GIF 首帧). */
@RequiresApi(Build.VERSION_CODES.P)
private fun decodeRasterWithImageDecoder(file: File): Bitmap {
    val source = ImageDecoder.createSource(file)
    // ImageDecoder.decodeBitmap 的回调签名是 (decoder, info, source) -> Unit,
    // 必须在 decoder 上调用 setTargetSize / setAllocator.
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        // 限制最大尺寸, 避免超大图片 OOM (与 Coil 默认行为一致)
        val maxDim = maxOf(info.size.width, info.size.height)
        if (maxDim > 4096) {
            val scale = 4096f / maxDim
            decoder.setTargetSize(
                (info.size.width * scale).toInt(),
                (info.size.height * scale).toInt(),
            )
        }
        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
    }
}

/**
 * 把 Coil 3.x 的 [coil3.Image] 转成 Android [Drawable].
 *
 * Coil 3.x 用 multiplatform 的 [coil3.Image] 接口替代了 Android [Drawable]。
 * 官方 public API 是 [coil3.asDrawable] 扩展函数 (在 coil-core 中),
 * 内部实现 (coil3.android.AndroidImage) 不是公开 API, 不应直接引用。
 *
 * 这里直接调用 `image.asDrawable(resources)`, 不再依赖反射兜底 ——
 * asDrawable 是 Coil 3.x 升级文档明确推荐的互操作 API。
 */
private fun coilImageToDrawable(
    result: SuccessResult,
    ctx: Context,
): Drawable {
    return result.image.asDrawable(ctx.resources)
}

/**
 * 把 [Drawable] 渲染成 [Bitmap]. 仅在 width/height > 0 时才真正渲染, 防止
 * SVG 还没解析完时取 0x0 引发 Native 异常.
 */
private fun drawableToBitmap(drawable: Drawable): Bitmap {
    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return bmp
}

// endregion

// region 杂项工具

private fun guessMimeByExt(file: File): String {
    val ext = file.extension.lowercase(Locale.US)
    return when (ext) {
        "png" -> MimeTypeConstants.IMAGE_PNG
        "jpg", "jpeg", "jpe", "jfif" -> MimeTypeConstants.IMAGE_JPEG
        "webp" -> MimeTypeConstants.IMAGE_WEBP
        "gif" -> MimeTypeConstants.IMAGE_GIF
        "bmp" -> MimeTypeConstants.IMAGE_BMP
        "heic" -> MimeTypeConstants.IMAGE_HEIC
        "heif" -> MimeTypeConstants.IMAGE_HEIC
        "avif" -> MimeTypeConstants.IMAGE_AVIF
        "ico" -> MimeTypeConstants.IMAGE_ICO
        "tiff", "tif" -> MimeTypeConstants.IMAGE_TIFF
        "svg" -> MimeTypeConstants.IMAGE_SVG_XML
        "svgz" -> MimeTypeConstants.IMAGE_SVG_XML_COMPRESSED
        "xml" -> "image/xml+vector"
        else -> "application/octet-stream"
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

private val LOG = LoggerFactory.getLogger("ImagePreviewFragment")

// endregion
