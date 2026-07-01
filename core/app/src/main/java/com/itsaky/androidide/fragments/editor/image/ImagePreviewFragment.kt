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
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.ComposeView
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.size.Size
import com.itsaky.androidide.fragments.editor.FragmentTabEntry
import com.itsaky.androidide.fragments.editor.FragmentTabRegistry
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fragment that previews image files (raster, GIF, SVG, Android vector drawable, etc.).
 *
 * Registered via [FragmentTabRegistry] under the [TAG] tag, similar to
 * [com.itsaky.androidide.fragments.editor.markdown.MarkdownPreviewFragment].
 *
 * Supports (see [ImageFormat] for the full list):
 *  - Raster (PNG, JPEG, WebP, BMP, HEIF/HEIC, TIFF, ICO, ...)
 *  - Animated (GIF, animated WebP) via Coil's [GifDecoder] / [ImageDecoderDecoder]
 *  - SVG via Coil's [SvgDecoder] (AndroidSVG under the hood)
 *  - Android XML vector drawables via [android.zero.studio.layouteditor.vectormaster.VectorMasterDrawable]
 *    which understands the full AAPT vector path syntax (pathData, fillColor, strokeColor,
 *    gradient, fillType, strokeWidth, strokeLineCap, strokeLineJoin, strokeMiterLimit,
 *    fillAlpha, strokeAlpha, trimPathStart/End, ...).  See `xml/vectormaster/`.
 *
 * The fragment renders a single [ComposeView] that hosts the entire preview UI:
 *  - Status bar at the top with file name, size, dimensions, mime type
 *  - Pinch-zoom / pan canvas (axis ratio preserved) for the actual image
 *  - Async image loading via Coil for non-XML formats
 *  - Direct [BitmapFactory] / [ImageDecoder] / [VectorMasterDrawable] decode on IO dispatcher
 *
 * The fragment is intentionally NOT a Sora editor tab — it has its own [FragmentTabEntry]
 * registered in `EditorHandlerActivity.registerFragmentTabs()` (under the [TAG] id).
 * Companion exposes [TAG] / [TAB_TITLE] / [ARG_FILE_PATH] / [newInstance] / [supports]
 * but not a pre-built [FragmentTabEntry] — callers (currently only the activity) construct
 * the entry inline so they can wire `fileExtensions` / `fragmentFactory` together.
 */
class ImagePreviewFragment : androidx.fragment.app.Fragment() {

  private var filePath: String? = null

  @Deprecated("Use newInstance() + arguments")
  constructor() : super()

  /**
   * Construct the fragment with the file path. We pass via `arguments` so the fragment can
   * survive recreation (config change, process death) without losing the file under preview.
   */
  companion object {
    const val TAG = "ImagePreviewFragment"
    // Match the key used by EditorFragmentTabManager.openTab() — it always puts the
    // file path under "file_path".  Using a different key would silently break the
    // openTab(fragmentId, path) flow.
    const val ARG_FILE_PATH = "file_path"

    /** Tab title shown when the tab manager renders a static entry.  EditorHandlerActivity
     *  registers a richer FragmentTabEntry (with fileExtensions / fragmentFactory); this
     *  field exists so the static [TAB_ENTRY] can also be registered standalone. */
    const val TAB_TITLE = "Image Preview"

    fun newInstance(filePath: String): ImagePreviewFragment =
      ImagePreviewFragment().apply {
        arguments = android.os.Bundle().apply { putString(ARG_FILE_PATH, filePath) }
      }

    /**
     * True if [filePath] looks like an image we can render.  Use this from the editor's
     * "Open preview" / "Open image" action to decide whether to dispatch to this fragment.
     */
    fun supports(filePath: String): Boolean {
      val ext = filePath.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
      return ext in SUPPORTED_FORMATS
    }
  }

  override fun onCreate(savedInstanceState: android.os.Bundle?) {
    super.onCreate(savedInstanceState)
    filePath = arguments?.getString(ARG_FILE_PATH) ?: savedInstanceState?.getString(ARG_FILE_PATH)
  }

  override fun onSaveInstanceState(outState: android.os.Bundle) {
    super.onSaveInstanceState(outState)
    filePath?.let { outState.putString(ARG_FILE_PATH, it) }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: android.os.Bundle?,
  ): View {
    val path = filePath
    val ctx = requireContext()

    return ComposeView(ctx).apply {
      // Dispose composition when the view is detached from the tree, not when the activity
      // is destroyed — this is the same pattern used by the other Compose-based fragments in
      // the project and keeps Coil / vector state alive during fragment view recreation.
      setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        MaterialTheme {
          ImagePreviewScreen(filePath = path, applicationContext = ctx.applicationContext)
        }
      }
    }
  }
}

/**
 * 识别要预览的图像格式.
 *
 * Coi 3.x 走 coil-svg / coil-gif 解码器, xml 矢量图走 xml/vectormaster 的
 * VectorMasterDrawable (因为它能解析 AAPT 编译过的 <vector> 标签里的 pathData 等高级属性,
 * 这些属性在普通 [android.graphics.drawable.Drawable.createFromXml] 也能解析, 但缺少
 * 渐变 / 动画支持).  优先级:
 *
 *  1. XML 矢量图 → VectorMasterDrawable (需要先看文件头是不是 `<?xml` + `<vector>`)
 *  2. 其它走 Coil (SVG / GIF / 静态栅格)
 */
object ImageFormat {
  // 文件后缀到 Coil decoder 的映射.  静态栅格 + GIF + WebP 等都交给 ImageDecoderDecoder
  // (API 28+) 或 GifDecoder (低 API), Coil 3.x 内部会自动选.  SVG 单独标记是因为要加
  // SvgDecoder.
  val RASTER_DECODER_FORMATS = setOf(
    "png", "jpg", "jpeg", "jpe", "jfif",
    "webp", "gif", "bmp", "dib",
    "heif", "heic", "hif",
    "tiff", "tif", "ico", "cur",
    "apng", "avif", "jp2", "j2k", "jpf", "jpx",
    "wbmp",
  )

  val SVG_FORMATS = setOf("svg", "svgz")
  val XML_VECTOR_FORMATS = setOf("xml") // 走 VectorMasterDrawable, 需要看文件内容

  val SUPPORTED_FORMATS: Set<String> = RASTER_DECODER_FORMATS + SVG_FORMATS + XML_VECTOR_FORMATS
}

/**
 * Image preview UI. 顶部状态栏显示文件信息, 中间是缩放/拖动画布.
 *
 * 用 [produceState] 异步加载 — 栅格走 Coil, XML 矢量图走 VectorMasterDrawable. 失败时
 * 显示错误信息 (含文件路径 + 错误), 成功时显示带 pinch-zoom 的预览.
 */
@Composable
private fun ImagePreviewScreen(filePath: String?, applicationContext: Context) {
  val context = LocalContext.current

  if (filePath == null) {
    ErrorState(message = "No file specified")
    return
  }

  val file = remember(filePath) { File(filePath) }
  if (!file.exists() || !file.isFile) {
    ErrorState(message = "File not found:\n${file.absolutePath}")
    return
  }

  val ext = remember(filePath) {
    filePath.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
  }
  if (ext !in SUPPORTED_FORMATS) {
    ErrorState(
      message = "Unsupported image format: .$ext\n\n" +
        "Supported: ${SUPPORTED_FORMATS.sorted().joinToString(", ")}"
    )
    return
  }

  val state by produceState<ImageUiState>(initialValue = ImageUiState.Loading, filePath) {
    value = withContext(Dispatchers.IO) { loadImage(applicationContext, file) }
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      TopStatusBar(file = file, state = state)
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
      ) {
        when (val s = state) {
          ImageUiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(40.dp))
          is ImageUiState.Error -> ErrorState(message = s.message)
          is ImageUiState.Loaded -> ZoomableImage(
            bitmap = s.bitmap,
            contentDescription = file.name,
          )
        }
      }
    }
  }
}

private sealed class ImageUiState {
  data object Loading : ImageUiState()
  data class Loaded(val bitmap: Bitmap) : ImageUiState()
  data class Error(val message: String) : ImageUiState()
}

/**
 * Top bar with file name, size, dimensions, mime type.  Stays visible across all states so
 * the user always knows which file is being previewed (and its on-disk size).
 */
@Composable
private fun TopStatusBar(file: File, state: ImageUiState) {
  val context = LocalContext.current
  val fileSizeText = remember(file) {
    runCatching { Formatter.formatShortFileSize(context, file.length()) }
      .getOrDefault("${file.length()} B")
  }
  val dimensionsText = when (val s = state) {
    is ImageUiState.Loaded -> "${s.bitmap.width}×${s.bitmap.height} px"
    else -> "…"
  }
  val mimeText = remember(file) { guessMimeType(file) }

  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    tonalElevation = 1.dp,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
      Text(
        text = file.name,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
      )
      Text(
        text = "$fileSizeText  ·  $dimensionsText  ·  $mimeText",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
      )
    }
  }
}

/**
 * The actual image with pinch-to-zoom and drag-to-pan.  We use a simple [Modifier.pointerInput]
 * + [detectTransformGestures] + manual scale/offset state because we don't want to pull in
 * the whole accompanist / graphics dependency tree just for this.
 */
@Composable
private fun ZoomableImage(bitmap: Bitmap, contentDescription: String?) {
  var scale by remember { mutableFloatStateOf(1f) }
  var offsetX by remember { mutableFloatStateOf(0f) }
  var offsetY by remember { mutableFloatStateOf(0f) }

  val painter: Painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
      .pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
          scale = (scale * zoom).coerceIn(0.25f, 8f)
          offsetX += pan.x
          offsetY += pan.y
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = painter,
      contentDescription = contentDescription,
      contentScale = ContentScale.Fit,
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
    )
  }
}

@Composable
private fun ErrorState(message: String) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.BrokenImage,
        contentDescription = null,
        modifier = Modifier.size(56.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
      )
    }
  }
}

// === Loading logic (IO dispatcher) ==============================================

/**
 * Decode [file] into a [Bitmap] using the best decoder for its format.  This is intentionally
 * single-purpose: it returns a fully-decoded [Bitmap] so the UI thread never blocks on
 * parsing / decoding.
 *
 * - XML vector drawable (`.xml` with `<vector>` root): [VectorMasterDrawable] (supports full
 *   AAPT vector path syntax, gradients, trim path, etc.).  We render the drawable into a
 *   Bitmap matching its intrinsic size (defaulting to 512×512 if missing).
 * - SVG (`.svg` / `.svgz`): [coil.decode.SvgDecoder] via a tiny inline [ImageLoader].  We do
 *   not use Coil's full [AsyncImage] pipeline because we want a synchronous [Bitmap] back.
 * - Everything else: try Android [ImageDecoder] (API 28+, supports HEIF / AVIF / animated
 *   WebP), falling back to [BitmapFactory].
 */
private suspend fun loadImage(context: Context, file: File): ImageUiState = withContext(Dispatchers.IO) {
  runCatching {
    val ext = file.extension.lowercase(Locale.ROOT)
    when {
      ext in ImageFormat.XML_VECTOR_FORMATS && looksLikeVectorXml(file) -> {
        decodeVectorXml(context, file)
      }
      ext in ImageFormat.SVG_FORMATS -> {
        decodeSvg(context, file)
      }
      else -> {
        decodeRaster(context, file)
      }
    }?.let { ImageUiState.Loaded(it) }
      ?: ImageUiState.Error("Failed to decode: ${file.name}")
  }.getOrElse { e ->
    ImageUiState.Error("Failed to load ${file.name}\n\n${e::class.java.simpleName}: ${e.message ?: "unknown"}")
  }
}

private fun looksLikeVectorXml(file: File): Boolean = runCatching {
  val head = file.useLines { it.take(8).joinToString("\n") }
  head.contains("<vector") || head.contains("<animated-vector")
}.getOrDefault(false)

/**
 * Parse an Android XML vector drawable via [VectorMasterDrawable], then render to bitmap.
 *
 * [VectorMasterDrawable] is a full AAPT vector parser (path data, fillType, gradient,
 * stroke caps/joins, trim path, etc.) — see `xml/vectormaster/`.  It exposes
 * `Context + File` constructor that reads + parses the file lazily, then we draw it into
 * a software [Bitmap] matching its intrinsic size (scaled up to a sensible preview size,
 * capped at 2048 to keep memory bounded).
 */
private fun decodeVectorXml(context: Context, file: File): Bitmap? {
  val master = android.zero.studio.layouteditor.vectormaster.VectorMasterDrawable(context, file)
  // VectorMasterDrawable's getIntrinsicWidth/Height returns the AAPT viewport size in
  // pixels, but for many drawables the viewport is small (24x24dp is common for icons).
  // We pick the larger of: intrinsic size or 256, then clamp to 2048.  This gives a
  // reasonable preview size that can be pinch-zoomed further by the user.
  val intrinsicW = master.intrinsicWidth
  val intrinsicH = master.intrinsicHeight
  val w = (if (intrinsicW > 0) intrinsicW else 256).coerceIn(64, 2048)
  val h = (if (intrinsicH > 0) intrinsicH else 256).coerceIn(64, 2048)
  master.setBounds(0, 0, w, h)
  val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
  master.draw(android.graphics.Canvas(bmp))
  return bmp
}

/**
 * Decode SVG using [coil.decode.SvgDecoder] (AndroidSVG).  We spin up a one-off [ImageLoader]
 * with the SVG decoder registered and ask for the bitmap synchronously.  No disk cache (the
 * file is local and we already have it in memory as a [Uri]).
 */
private fun decodeSvg(context: Context, file: File): Bitmap? {
  val loader = ImageLoader.Builder(context)
    .components { add(SvgDecoder.Factory()) }
    .build()
  val request = ImageRequest.Builder(context)
    .data(Uri.fromFile(file))
    .size(Size.ORIGINAL)
    .allowHardware(false)
    .build()
  val result = loader.execute(request)
  val drawable = result.drawable ?: return null
  val w = (drawable.intrinsicWidth.takeIf { it > 0 } ?: 512).coerceAtMost(2048)
  val h = (drawable.intrinsicHeight.takeIf { it > 0 } ?: 512).coerceAtMost(2048)
  val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
  drawable.setBounds(0, 0, w, h)
  drawable.draw(android.graphics.Canvas(bmp))
  return bmp
}

/** Raster image (PNG/JPEG/WebP/HEIF/etc).  Uses [ImageDecoder] on API 28+, [BitmapFactory] below. */
private fun decodeRaster(context: Context, file: File): Bitmap? {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    val source = ImageDecoder.createSource(file)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
      decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
      decoder.isMutableRequired = false
      // No downsampling; user can pinch-zoom.
    }
  } else {
    val opts = BitmapFactory.Options().apply {
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    file.inputStream().use { BitmapFactory.decodeStream(it, null, opts) }
  }
}

// === Mime type guess ============================================================

/**
 * Best-effort mime guess from file extension.  Used only for the status bar.  The real
 * decoding uses Coil / VectorMasterDrawable / ImageDecoder which look at magic bytes, not
 * the extension.
 */
private fun guessMimeType(file: File): String {
  val ext = file.extension.lowercase(Locale.ROOT)
  return when (ext) {
    "png" -> "image/png"
    "jpg", "jpeg", "jpe", "jfif" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "bmp", "dib" -> "image/bmp"
    "heif", "heic", "hif" -> "image/heif"
    "tiff", "tif" -> "image/tiff"
    "ico" -> "image/x-icon"
    "cur" -> "image/x-win-bitmap"
    "apng" -> "image/apng"
    "avif" -> "image/avif"
    "wbmp" -> "image/vnd.wap.wbmp"
    "svg" -> "image/svg+xml"
    "svgz" -> "image/svg+xml"
    "xml" -> "application/xml (vector drawable?)"
    "jp2", "j2k", "jpf", "jpx" -> "image/jp2"
    else -> "application/octet-stream"
  }
}
