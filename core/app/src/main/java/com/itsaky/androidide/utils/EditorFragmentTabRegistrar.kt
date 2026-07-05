package com.itsaky.androidide.utils

import com.itsaky.androidide.fragments.editor.FragmentTabEntry
import com.itsaky.androidide.fragments.editor.FragmentTabRegistry
import com.itsaky.androidide.fragments.editor.image.ImagePreviewFragment
import com.itsaky.androidide.fragments.editor.markdown.MarkdownPreviewFragment
import com.itsaky.androidide.resources.R
import com.zerostudio.preview.UniversalPreviewEngineFragment

/**
 * 编辑器 Fragment Tab 注册器。
 *
 * 把原本内联在 [com.itsaky.androidide.activities.editor.EditorHandlerActivity]
 * 里的 `FragmentTabRegistry.register(...)` 调用集中到这个单独的 class,
 * 让 EditorHandlerActivity 只负责调用 [registerAll], 不关心具体注册了哪些 tab。
 *
 * 新增预览 tab 时只需在本文件的 [registerAll] 里追加一个 [register] 调用,
 * EditorHandlerActivity 零改动。
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

  /**
   * 注册全部编辑器 Fragment Tab。
   *
   * 幂等: 如果已注册过 (以 [MARKDOWN_PREVIEW] 为标志), 直接 return。
   * 在 [com.itsaky.androidide.activities.editor.EditorHandlerActivity] 初始化时调用一次。
   */
  fun registerAll() {
    // 只注册一次
    if (FragmentTabRegistry.isRegistered(MARKDOWN_PREVIEW)) {
      return
    }

    registerMarkdownPreview()
    registerImagePreview()
    registerUniversalPreview()
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
}
