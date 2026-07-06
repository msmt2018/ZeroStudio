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
import com.itsaky.androidide.utils.flashError
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
    const val MB_10: Long = 10 * 1024 * 1024
  }

  @Subscribe(threadMode = MAIN)
  fun onFileClicked(event: FileClickEvent) {
    if (!checkIsEditorActivity(event)) {
      logCannotHandle(event)
      return
    }

    if (event.file.isDirectory) return

    val context = event[Context::class.java]!! as EditorHandlerActivity
    context.binding.root.closeDrawer(GravityCompat.START)
    if (event.file.name.endsWith(".apk")) {
      ApkInstaller.installApk(
          context,
          InstallationResultHandler.createEditorActivitySender(context),
          event.file,
          context.installationSessionCallback(),
      )
      return
    }

    if (MB_10 < event.file.length()) {
      flashError("File is too big!")
      log.warn(
          "Cannot open {} as it is too big. File size: {} bytes",
          event.file,
          event.file.length(),
      )
      return
    }

    // === 图片预览路由 ===
    // 文件后缀命中 ImagePreviewFragment.SUPPORTED_FORMATS 时, 直接在 IDE
    // 内的 Image Preview tab 里打开, 不再走系统 Intent.ACTION_VIEW 调外部
    // viewer (老逻辑会把用户切出 IDE). `.xml` 文件需要做 content sniff:
    // layout / manifest / values 等 .xml 都不是 vector, 走 content 头 1KB
    // 包含 "<vector" 才算 Android vector drawable, 避免误判.
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
      // tab 没注册 (理论不会, 走不到这里) → fall through 到普通 openFile
    }

    // === 音频预览路由 ===
    // 文件后缀命中 AudioPreviewFragment.SUPPORTED_EXTENSIONS (mp3 / wav / ogg /
    // flac / aac / m4a / opus / mid / midi / amr / pcm / aiff / ape / wma) 时,
    // 直接在 IDE 内的 Audio Preview tab 里打开.
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
    // 文件后缀命中 VideoPreviewFragment.SUPPORTED_EXTENSIONS (mp4 / mkv / webm /
    // avi / mov / 3gp / mpg / mpeg / ts / m2ts / flv / wmv / m4v / vob / ogv) 时,
    // 直接在 IDE 内的 Video Preview tab 里打开.
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

    context.openFile(event.file)
  }

  /**
   * 判断给定文件是否应该走 [ImagePreviewFragment]. 规则:
   *  - 扩展名在 [ImagePreviewFragment.SUPPORTED_FORMATS] 中
   *  - 对 `.xml` 还要做 [FileValidator.isLikelyAndroidVector] content sniff,
   *    排除 layout / manifest / values 等非 vector xml.
   */
  private fun isSupportedImageFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    if (ext.isEmpty() || ext !in ImagePreviewFragment.SUPPORTED_FORMATS) return false
    if (ext == "xml") return FileValidator.isLikelyAndroidVector(file)
    return true
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
