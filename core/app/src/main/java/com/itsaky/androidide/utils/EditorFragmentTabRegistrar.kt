package com.itsaky.androidide.utils

import com.itsaky.androidide.fragments.editor.FragmentTabEntry
import com.itsaky.androidide.fragments.editor.FragmentTabRegistry
import com.itsaky.androidide.fragments.editor.audio.AudioPreviewFragment
import com.itsaky.androidide.fragments.editor.image.ImagePreviewFragment
import com.itsaky.androidide.fragments.editor.markdown.MarkdownPreviewFragment
import com.itsaky.androidide.fragments.editor.video.VideoPreviewFragment
import com.itsaky.androidide.fragments.editor.web.WebPreviewFragment
import com.itsaky.androidide.resources.R
import com.zerostudio.preview.UniversalPreviewEngineFragment

/**
 * 编辑器 Fragment Tab 注册器。
 *
 * 把原本内联在 [com.itsaky.androidide.activities.editor.EditorActivityKt]
 * 里的 `FragmentTabRegistry.register(...)` 调用集中到这个单独的 class,
 * 让 EditorActivityKt 只负责调用 [registerAll], 不关心具体注册了哪些 tab。
 *
 * 新增预览 tab 时只需在本文件的 [registerAll] 里追加一个 [register] 调用,
 * EditorActivityKt 零改动。
 *
 * @author ZeroStudio
 */
object EditorFragmentTabRegistrar {

  /** Markdown 预览 tab 的 id (供外部引用, 例如去重判断) */
  const val MARKDOWN_PREVIEW = "markdown_preview"

  /** 图片预览 tab 的 id */
  const val IMAGE_PREVIEW = "image_preview"

  /** C/C++ 3D/2D 通用预览 tab 的 id */
  const val UNIVERSAL_PREVIEW = "universal_preview"

  /** 音频预览 tab 的 id */
  const val AUDIO_PREVIEW = "audio_preview"

  /** 视频预览 tab 的 id */
  const val VIDEO_PREVIEW = "video_preview"

  /** Web 预览 tab 的 id */
  const val WEB_PREVIEW = "web_preview"

  /**
   * 注册全部编辑器 Fragment Tab。
   *
   * 幂等: 如果已注册过 (以 [MARKDOWN_PREVIEW] 为标志), 直接 return。
   * 在 [com.itsaky.androidide.activities.editor.EditorActivityKt] 初始化时调用一次。
   */
  fun registerAll() {
    // 只注册一次
    if (FragmentTabRegistry.isRegistered(MARKDOWN_PREVIEW)) {
      return
    }

    registerMarkdownPreview()
    registerImagePreview()
    registerUniversalPreview()
    registerAudioPreview()
    registerVideoPreview()
    registerWebPreview()
  }

  // ── Markdown 预览 ──────────────────────────────────────────

  private fun registerMarkdownPreview() {
    FragmentTabRegistry.register(
      FragmentTabEntry(
        id = MARKDOWN_PREVIEW,
        title = "Markdown Preview",
        iconRes = R.drawable.ic_markdown_preview,
        fragmentClass = MarkdownPreviewFragment::class.java,
        fileExtensions = MarkdownPreviewFragment.SUPPORTED_EXTENSIONS,
        order = 100,
        fragmentFactory = { MarkdownPreviewFragment() }
      )
    )
  }

  // ── 图片预览 ────────────────────────────────────────────────

  /**
   * Android XML vector / SVG / 常见位图 (PNG / JPG / WebP / GIF / HEIC /
   * BMP / AVIF / ICO / TIFF). 文件后缀命中 [ImagePreviewFragment.SUPPORTED_FORMATS]
   * 时 editor 在 tab 栏给出 "Image Preview" 入口。
   *
   * factory 不传 filePath: 真正的路径在 [EditorFragmentTabManager] 打开 tab
   * 时通过 [ImagePreviewFragment.newInstance] 注入到 arguments。这里仅供 tab
   * 创建时 fallback 预览, 真实打开后会用 newInstance(filePath) 覆盖。
   */
  private fun registerImagePreview() {
    FragmentTabRegistry.register(
      FragmentTabEntry(
        id = IMAGE_PREVIEW,
        title = ImagePreviewFragment.TAB_TITLE,
        iconRes = R.drawable.ic_file_type_image,
        fragmentClass = ImagePreviewFragment::class.java,
        fileExtensions = ImagePreviewFragment.SUPPORTED_FORMATS,
        order = 110,
        fragmentFactory = { ImagePreviewFragment() },
      )
    )
  }

  // ── C/C++ 3D/2D 通用预览 ───────────────────────────────────

  /**
   * 双核架构 (WebView + Three.js / GLSurfaceView + JNI C++ NDK)。
   * 文件后缀命中 [UniversalPreviewEngineFragment.SUPPORTED_EXTENSIONS]
   * (c / cpp / h / glsl / cu 等) 时 editor 在 tab 栏给出 "Universal Preview" 入口。
   */
  private fun registerUniversalPreview() {
    FragmentTabRegistry.register(
      FragmentTabEntry(
        id = UNIVERSAL_PREVIEW,
        title = UniversalPreviewEngineFragment.TAB_TITLE,
        iconRes = R.drawable.ic_code,
        fragmentClass = UniversalPreviewEngineFragment::class.java,
        fileExtensions = UniversalPreviewEngineFragment.SUPPORTED_EXTENSIONS,
        order = 120,
        fragmentFactory = { UniversalPreviewEngineFragment() },
      )
    )
  }

  // ── 音频预览 ────────────────────────────────────────────────

  /**
   * Media3 ExoPlayer + Compose 磨砂玻璃控件。
   * 支持 14 种音频格式 (mp3 / wav / ogg / flac / aac / m4a / opus / mid / midi /
   * amr / pcm / aiff / ape / wma), 含频谱可视化 + LRC 歌词同步 + 均衡器。
   * 文件后缀命中 [AudioPreviewFragment.SUPPORTED_EXTENSIONS] 时 editor 在 tab 栏
   * 给出 "Audio Preview" 入口。
   */
  private fun registerAudioPreview() {
    FragmentTabRegistry.register(
      FragmentTabEntry(
        id = AUDIO_PREVIEW,
        title = AudioPreviewFragment.TAB_TITLE,
        iconRes = R.drawable.ic_file_type_image, // 暂时复用图片图标, 后续可换专用音频图标
        fragmentClass = AudioPreviewFragment::class.java,
        fileExtensions = AudioPreviewFragment.SUPPORTED_EXTENSIONS,
        order = 130,
        fragmentFactory = { AudioPreviewFragment() },
      )
    )
  }

  // ── 视频预览 ────────────────────────────────────────────────

  /**
   * Media3 ExoPlayer + PlayerView + Compose 磨砂玻璃控件 overlay。
   * 支持 15 种视频格式 (mp4 / mkv / webm / avi / mov / 3gp / mpg / mpeg / ts /
   * m2ts / flv / wmv / m4v / vob / ogv), 含手势控制 + 字幕 + 截图。
   * 文件后缀命中 [VideoPreviewFragment.SUPPORTED_EXTENSIONS] 时 editor 在 tab 栏
   * 给出 "Video Preview" 入口。
   */
  private fun registerVideoPreview() {
    FragmentTabRegistry.register(
      FragmentTabEntry(
        id = VIDEO_PREVIEW,
        title = VideoPreviewFragment.TAB_TITLE,
        iconRes = R.drawable.ic_file_type_image, // 暂时复用图片图标, 后续可换专用视频图标
        fragmentClass = VideoPreviewFragment::class.java,
        fileExtensions = VideoPreviewFragment.SUPPORTED_EXTENSIONS,
        order = 140,
        fragmentFactory = { VideoPreviewFragment() },
      )
    )
  }

  // ── Web 预览 ────────────────────────────────────────────────

  /**
   * androidx.webkit WebView + Compose 磨砂玻璃控件 + Chrome DevTools。
   * 支持 html / htm 静态文件预览, 以及通过工具栏地址栏输入 URL / 底部后端
   * 控制栏启动 Termux 中的 node/python/php 运行时来预览本地 dev server。
   *
   * DevTools 采用三段式桥接:
   *   1. 被调试 WebView setWebContentsDebuggingEnabled(true) → 暴露 CDP unix socket
   *   2. LocalSocketForwarder 把 abstract socket 转发到 localhost:9222 TCP
   *   3. 第二个 WebView 加载 chrome-devtools-frontend.appspot.com 的 DevTools 前端
   *
   * 设备切换: UA + viewport 真实重渲染, 11 档预置设备
   * (iPhone SE/14/14 Pro Max, Pixel 7/7 Pro, Galaxy S23, iPad Mini/Pro 11,
   * Desktop 1080p/4K, MacBook Air)。
   */
  private fun registerWebPreview() {
    FragmentTabRegistry.register(
      FragmentTabEntry(
        id = WEB_PREVIEW,
        title = WebPreviewFragment.TAB_TITLE,
        iconRes = R.drawable.ic_file_type_image, // 暂时复用图片图标, 后续可换专用 web 图标
        fragmentClass = WebPreviewFragment::class.java,
        fileExtensions = WebPreviewFragment.SUPPORTED_EXTENSIONS,
        order = 150,
        fragmentFactory = { WebPreviewFragment() },
      )
    )
  }
}
