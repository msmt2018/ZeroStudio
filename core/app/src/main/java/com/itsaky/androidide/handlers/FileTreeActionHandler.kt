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

package com.itsaky.androidide.handlers

import android.content.Context
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import androidx.core.view.GravityCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem.Location.EDITOR_FILE_TREE
import com.itsaky.androidide.actions.ActionMenu
import com.itsaky.androidide.actions.ActionsRegistry
import com.itsaky.androidide.actions.internal.DefaultActionsRegistry
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.eventbus.events.filetree.FileClickEvent
import com.itsaky.androidide.eventbus.events.filetree.FileLongClickEvent
import com.itsaky.androidide.events.ExpandTreeNodeRequestEvent
import com.itsaky.androidide.events.FileContextMenuItemClickEvent
import com.itsaky.androidide.events.ListProjectFilesRequestEvent
import com.itsaky.androidide.file.FileValidator
import com.itsaky.androidide.fragments.editor.audio.AudioPreviewFragment
import com.itsaky.androidide.fragments.editor.image.ImagePreviewFragment
import com.itsaky.androidide.fragments.editor.video.VideoPreviewFragment
import com.itsaky.androidide.fragments.sheets.OptionsListFragment
import com.itsaky.androidide.models.SheetOption
import com.itsaky.androidide.utils.ApkInstaller
import com.itsaky.androidide.utils.InstallationResultHandler
import java.io.File
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN

/**
 * Handles events related to files in filetree.
 *
 * @author android_zero
 */
@Suppress("unused")
class FileTreeActionHandler : BaseEventHandler() {

  private var lastHeld: Node<FileObject>? = null

  companion object {

    const val TAG_FILE_OPTIONS_FRAGMENT = "file_options_fragment"
  }

  /**
   * 文件点击路由分发。
   *
   * 设计原则:
   * 1. **特殊二进制文件** (图片/音频/视频/apk 等) —— 通过文件扩展名匹配走对应
   *    预览 Fragment 或安装器。这类文件的解码器只支持特定格式, 扩展名匹配就
   *    足够, 不需要也不应该尝试用文本编辑器打开。
   * 2. **文本类型文件** —— 不通过扩展名判断 (文本文件后缀极其广泛, 无法穷举),
   *    而是作为默认 fallback 走 [EditorHandlerActivity.openFile]。无论是否有
   *    扩展名、扩展名是否已知, 只要没被上面的二进制路由命中, 都交给文本编辑器。
   *
   * 不在前置做文件大小硬性拦截。文本文件是否 OOM 取决于 openFile 内部的
   * 内存管理 (分块读取 / 流式渲染 / 懒加载等) 以及设备内存极限, 由 openFile
   * 自身负责, 不在此处用固定阈值一刀切。
   */
  @Subscribe(threadMode = MAIN)
  fun onFileClicked(event: FileClickEvent) {
    if (!checkIsEditorActivity(event)) {
      logCannotHandle(event)
      return
    }

    if (event.file.isDirectory) return

    val context = event[Context::class.java]!! as EditorHandlerActivity
    context.binding.root.closeDrawer(GravityCompat.START)

    // === APK 安装路由 ===
    // apk 是二进制安装包, 按扩展名匹配走系统安装器。
    if (event.file.name.endsWith(".apk")) {
      ApkInstaller.installApk(
          context,
          InstallationResultHandler.createEditorActivitySender(context),
          event.file,
          context.installationSessionCallback(),
      )
      return
    }

    // === 图片预览路由 ===
    // 文件后缀命中 [ImagePreviewFragment.RASTER_DECODER_FORMATS] (PNG / JPG /
    // WebP / GIF / HEIC / BMP / AVIF / ICO / TIFF 等位图) 时, 直接在 IDE
    // 内的 Image Preview tab 里打开, 不再走系统 Intent.ACTION_VIEW 调外部
    // viewer (老逻辑会把用户切出 IDE).
    //
    // 注意: SVG / SVGZ / XML 矢量图 *不* 在这里直接打开预览 —— 它们先以
    // 文本编辑器打开 (方便编辑源码), 用户需要预览渲染效果时通过编辑器
    // 工具栏的 "Render As Image" action (ImagePreviewAction) 转换到
    // ImagePreviewFragment tab. 这与 PreviewLayoutAction (布局 XML 先
    // 编辑后预览) 的交互模式一致.
    if (isSupportedImageFile(event.file)) {
      val ext = event.file.extension.lowercase()
      val tabId = context.fragmentTabManager?.openFileTab(
        filePath = event.file.absolutePath,
        fileExtension = ext,
      )
      if (tabId != null) {
        log.info("Opened image preview tab {} for {}", tabId, event.file)
        return
      }
      // tab 没注册 (理论不会, 走不到这里) → fall through 到默认文本路由
    }

    // === 音频预览路由 ===
    // 文件后缀命中 [AudioPreviewFragment.SUPPORTED_EXTENSIONS] (mp3 / wav / ogg /
    // flac / aac / m4a / opus / mid / midi / amr / pcm / aiff / ape / wma) 时,
    // 直接在 IDE 内的 Audio Preview tab 里打开。Media3 ExoPlayer 流式按需解码,
    // 不需要把整个文件读进内存, 大文件也能正常播放。
    if (isSupportedAudioFile(event.file)) {
      val ext = event.file.extension.lowercase()
      val tabId = context.fragmentTabManager?.openFileTab(
        filePath = event.file.absolutePath,
        fileExtension = ext,
      )
      if (tabId != null) {
        log.info("Opened audio preview tab {} for {}", tabId, event.file)
        return
      }
    }

    // === 视频预览路由 ===
    // 文件后缀命中 [VideoPreviewFragment.SUPPORTED_EXTENSIONS] (mp4 / mkv / webm /
    // avi / mov / 3gp / mpg / mpeg / ts / m2ts / flv / wmv / m4v / vob / ogv) 时,
    // 直接在 IDE 内的 Video Preview tab 里打开。同样流式解码, 无大小限制。
    if (isSupportedVideoFile(event.file)) {
      val ext = event.file.extension.lowercase()
      val tabId = context.fragmentTabManager?.openFileTab(
        filePath = event.file.absolutePath,
        fileExtension = ext,
      )
      if (tabId != null) {
        log.info("Opened video preview tab {} for {}", tabId, event.file)
        return
      }
    }

    // === 默认文本路由 (fallback) ===
    // 所有未被上面二进制路由命中的文件 (包括无扩展名、未知扩展名、HTML / HTM /
    // XML / Markdown / JSON / 各类源码等文本文件) 均以文本编辑器打开。
    // 文本文件后缀极其广泛无法穷举, 故不在此做扩展名判断, 统一交给 openFile。
    // 用户需要预览渲染效果时通过编辑器工具栏的对应 Action 按钮
    // (WebPreviewAction / MarkdownPreviewAction / ImagePreviewAction 等)
    // 切换到预览 Fragment tab。
    //
    // 不做前置大小拦截 —— 文件能否打开由 openFile 内部的内存管理决定
    // (分块读取 / 流式渲染 / 懒加载等), 而非固定阈值。
    context.openFile(event.file)
  }

  /**
   * 判断给定文件是否应该直接走 [ImagePreviewFragment] 预览 (而非文本编辑器).
   *
   * 规则: 仅位图格式 ([ImagePreviewFragment.RASTER_DECODER_FORMATS]) 直接
   * 预览. SVG / SVGZ / Android XML vector 不在此列 —— 它们先以文本编辑器
   * 打开, 用户通过 [com.itsaky.androidide.actions.etc.ImagePreviewAction]
   * ("渲染为图像") 再切换到预览 tab.
   */
  private fun isSupportedImageFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    if (ext.isEmpty()) return false
    return ext in ImagePreviewFragment.RASTER_DECODER_FORMATS
  }

  /** 判断给定文件是否应该走 [AudioPreviewFragment] (仅扩展名匹配). */
  private fun isSupportedAudioFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    return ext.isNotEmpty() && ext in AudioPreviewFragment.SUPPORTED_EXTENSIONS
  }

  /** 判断给定文件是否应该走 [VideoPreviewFragment] (仅扩展名匹配). */
  private fun isSupportedVideoFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    return ext.isNotEmpty() && ext in VideoPreviewFragment.SUPPORTED_EXTENSIONS
  }

  @Suppress("UNCHECKED_CAST")
  @Subscribe(threadMode = MAIN)
  fun onFileLongClicked(event: FileLongClickEvent) {
    if (!checkIsEditorActivity(event)) {
      logCannotHandle(event)
      return
    }

    this.lastHeld = event[Node::class.java] as? Node<FileObject>
    val context = event[Context::class.java]!! as EditorHandlerActivity
    createFileOptionsFragment(context, event.file)
        .show(context.supportFragmentManager, TAG_FILE_OPTIONS_FRAGMENT)
  }

  private fun createFileOptionsFragment(
      context: EditorHandlerActivity,
      file: File,
  ): OptionsListFragment {
    val fragment = OptionsListFragment()
    val registry = ActionsRegistry.getInstance()
    val actions = registry.getActions(EDITOR_FILE_TREE)
    val data = ActionData()
    data.apply {
      put(Context::class.java, context)
      put(File::class.java, file)
      lastHeld?.let { put(Node::class.java, it) }
    }

    for (action in actions.values) {
      check(action !is ActionMenu) { "File tree actions do not support action menus" }

      action.prepare(data)
      if (!action.enabled || !action.visible) continue

      fragment.addOption(
          SheetOption(action.id, action.icon, action.label, file).apply { this.extra = data }
      )
    }

    return fragment
  }

  @Subscribe(threadMode = MAIN)
  internal fun onFileOptionClicked(event: FileContextMenuItemClickEvent) {
    val option = event.option
    if (option.extra !is ActionData) return

    val data = option.extra!! as ActionData
    val registry = ActionsRegistry.getInstance() as DefaultActionsRegistry
    val action = registry.findAction(EDITOR_FILE_TREE, option.id)

    checkNotNull(action) {
      "Invalid FileContextMenuItemClickEvent received. No action item registered with id '${option.id}'"
    }

    registry.executeAction(action, data)
  }

  private fun requestFileListing() {
    EventBus.getDefault().post(ListProjectFilesRequestEvent())
    EventBus.getDefault()
        .post(com.itsaky.androidide.fragments.git.tree.ListProjectFilesRequestEvent())
  }

  private fun requestExpandHeldNode() {
    lastHeld?.let { requestExpandNode(it) }
  }

  private fun requestExpandNode(node: Node<FileObject>) {
    EventBus.getDefault().post(ExpandTreeNodeRequestEvent(node))
  }
}
