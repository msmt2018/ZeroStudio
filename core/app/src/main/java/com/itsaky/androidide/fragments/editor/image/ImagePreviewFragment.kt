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
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import coil3.ImageLoader
import coil3.execute
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.svg.SvgDecoder
import com.itsaky.androidide.file.FileValidator
import com.itsaky.androidide.file.MimeTypeConstants
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import android.zero.studio.layouteditor.vectormaster.VectorMasterDrawable

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
}

// region 顶层 Composable

/**
 * 顶层 Compose 屏 —— 异步读图 + 缩放/平移 + 错误/加载状态.
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
    val state by produceState<ImageUiState>(initialValue = ImageUiState.Loading, file) {
        value = runCatching { loadImage(file) }.fold(
            onSuccess = { ImageUiState.Loaded(it) },
            onFailure = { ImageUiState.Error(it.message ?: it.javaClass.simpleName) },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        TopStatusBar(file = file, state = state)
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                ImageUiState.Loading -> CircularProgressIndicator(color = Color.White)
                is ImageUiState.Error -> ErrorState(message = s.message)
                is ImageUiState.Loaded -> ZoomableImage(
                    bitmap = s.bitmap,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * 顶部状态条 —— 文件名 / 大小 / 尺寸 / mime.
 */
@Composable
private fun TopStatusBar(file: File, state: ImageUiState) {
    val sizeText = remember(file) { humanReadableSize(file.length()) }
    val dimensionText = when (val s = state) {
        is ImageUiState.Loaded -> "${s.bitmap.width} x ${s.bitmap.height}"
        else -> "—"
    }
    val mimeText = when (state) {
        is ImageUiState.Loaded -> (state as ImageUiState.Loaded).mime
        else -> guessMimeByExt(file)
    }
    Surface(
        color = Color(0xFF252526),
        contentColor = Color(0xFFCCCCCC),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = file.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFFEEEEEE),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$sizeText   |   $dimensionText   |   $mimeText",
                fontSize = 11.sp,
                color = Color(0xFF9CDCFE),
                fontFamily = FontFamily.Monospace,
            )
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
 * 缩放 + 平移的图片视图.
 *
 * - 双指捏合: 缩放 (0.1x ~ 10x).
 * - 单指拖动: 平移.
 * - 双击: 还原 (scale=1, offset=0).
 */
@Composable
private fun ZoomableImage(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
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
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
        )
    }
}

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
private suspend fun loadImage(file: File): LoadedImage = withContext(Dispatchers.IO) {
    val name = file.name.lowercase(Locale.US)
    val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
    // 优先按"内容是不是 Android vector"判, 避免 layout XML 等被错路由
    if (ext == "xml" && FileValidator.isLikelyAndroidVector(file)) {
        decodeVectorXml(file)
    } else if (ext in ImagePreviewFragment.SVG_FORMATS) {
        decodeSvg(file)
    } else {
        decodeRaster(file)
    }
}

/**
 * 解析 Android XML vector drawable. 用 [VectorMasterDrawable] 而非
 * `AppCompatResources.getDrawable`, 因为后者要求 drawable 必须在 res/.
 *
 * 流程: VectorMasterDrawable 解析 XML → 取内在宽高 (或从 `<vector>` 头读
 * android:width/height) → 用 [Canvas] 渲染到 [Bitmap].
 */
private fun decodeVectorXml(file: File): LoadedImage {
    val ctx = appCtx
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
        Log.w(TAG, "readVectorDimensions: fallback for $file", e)
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
private suspend fun decodeSvg(file: File): LoadedImage {
    val ctx = appCtx
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
 * 走 Coil 3.x 默认 decoder, 异步在 IO 线程. 解码得到 [Drawable] 后渲染到
 * [Bitmap].
 */
private suspend fun decodeRaster(file: File): LoadedImage {
    val ctx = appCtx
    val mime = guessMimeByExt(file)
    val loader = ImageLoader.Builder(ctx).build()
    val req = ImageRequest.Builder(ctx)
        .data(file)
        .build()
    val result = loader.execute(req)
    if (result !is SuccessResult) {
        throw IllegalStateException("Failed to decode bitmap: ${result.javaClass.simpleName}")
    }
    val drawable: Drawable = coilImageToDrawable(result, ctx)
    val bmp = drawableToBitmap(drawable)
    return LoadedImage(bitmap = bmp, mime = mime)
}

/**
 * 把 Coil 3.x 的 [coil3.Image] 转成 Android [Drawable]. Coil 3.x 在 Android
 * 平台上的实现是 `coil3.android.AndroidImage`, 暴露一个 `drawable: Drawable`
 * 属性. 反射拿不到时 fallback 到 `Bitmap` 字段.
 *
 * 之所以不用 reflection-only 的 asImageBitmap: 反射对 Coil 内部类的字段名敏感
 * (不同 Coil 版本字段可能改名), 用 try-catch 包裹后实际能稳定工作的就是
 * `drawable` 字段.
 */
private fun coilImageToDrawable(
    result: SuccessResult,
    ctx: Context,
): Drawable {
    val image = result.image
    val cls = image::class.java
    for (f in cls.declaredFields) {
        f.isAccessible = true
        val v = f.get(image) ?: continue
        if (v is Drawable) return v
        if (v is Bitmap) return BitmapDrawable(ctx.resources, v)
    }
    throw IllegalStateException(
        "Cannot extract drawable from coil3.Image of type ${cls.name}",
    )
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

/**
 * 在 Composable 中拿到 application context, 避免持有 Activity 引用.
 * 用 [LocalContext] 拿到的实际上是 Activity / sub-context, 但
 * 传给 Coil / BitmapFactory / VectorMasterDrawable 都安全.
 */
private val appCtx: Context
    @Composable
    get() = LocalContext.current.applicationContext

private val LOG = LoggerFactory.getLogger("ImagePreviewFragment")

// endregion
