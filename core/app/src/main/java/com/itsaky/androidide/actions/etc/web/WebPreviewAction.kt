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

package com.itsaky.androidide.actions.etc.web

import android.content.Context
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.KeyboardUtils
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.actions.markInvisible
import com.itsaky.androidide.activities.editor.EditorActivityKt
import com.itsaky.androidide.fragments.editor.web.WebPreviewFragment
import com.itsaky.androidide.models.SaveResult
import com.itsaky.androidide.resources.R
import java.io.File

/**
 * Action menu item for previewing web files in a tab.
 *
 * This action opens a Web preview tab in the EditorActivityKt
 * when the user clicks the preview button in the editor toolbar.
 *
 * 支持的格式: html / htm. 完整列表见 [WebPreviewFragment.SUPPORTED_EXTENSIONS]。
 * 其他 web 场景 (Vue/React 构建产物 / dev server / Node.js 后端) 通过工具栏
 * 地址栏输入或底部后端控制栏启动进入, 不走文件扩展名匹配。
 *
 * 预览 UI 采用半透明高斯模糊磨砂玻璃控件 (FrostedGlass / FrostedIconButton /
 * FrostedToggleIconButton), 内嵌 androidx.webkit WebView + Chrome DevTools
 * (三段式 CDP 桥接: unix socket → LocalSocketForwarder → DevTools 前端)。
 * 图标 / 文本颜色通过 [com.itsaky.androidide.ui.compose.LocalDarkMode] 在黑白
 * 主题下自适应。
 *
 * @author ZeroStudio
 */
class WebPreviewAction(context: Context, override val order: Int) : EditorRelatedAction() {

    override val id: String = ID

    override var requiresUIThread: Boolean = false

    companion object {
        const val ID = "ide.editor.webPreview"
    }

    init {
        label = context.getString(R.string.title_web_preview)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_file_type_image)
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)

        val activity = data.getActivity() as? EditorActivityKt
        if (activity == null) {
            markInvisible()
            return
        }

        // 只匹配 web 后缀 (html / htm)
        val editor = data.getEditor()
        val file = editor?.file

        if (file != null) {
            val extension = file.extension.lowercase()
            if (WebPreviewFragment.SUPPORTED_EXTENSIONS.contains(extension)) {
                visible = true
                enabled = true
            } else {
                markInvisible()
            }
        } else {
            visible = true
            enabled = false
        }
    }

    override fun getShowAsActionFlags(data: ActionData): Int {
        val activity = data.getActivity() ?: return super.getShowAsActionFlags(data)
        return if (KeyboardUtils.isSoftInputVisible(activity)) {
            MenuItem.SHOW_AS_ACTION_IF_ROOM
        } else {
            MenuItem.SHOW_AS_ACTION_ALWAYS
        }
    }

    override suspend fun execAction(data: ActionData): Boolean {
        val activity = data.requireActivity() as? EditorActivityKt ?: return false
        val file = data.getEditor()?.file ?: return false
        val index = activity.findIndexOfEditorByFile(file)
        if (index >= 0) {
            activity.saveResult(index, SaveResult())
        }
        return true
    }

    override fun postExec(data: ActionData, result: Any) {
        val activity = data.requireActivity() as? EditorActivityKt ?: return
        val editor = data.getEditor()
        val file = editor?.file

        if (file != null && file.exists() && file.canRead()) {
            openWebPreview(activity, file)
        }
    }

    /**
     * Opens the Web preview tab with the given file.
     *
     * @param activity The EditorActivityKt
     * @param file The HTML file to preview
     */
    private fun openWebPreview(activity: EditorActivityKt, file: File) {
        val extension = file.extension.lowercase()
        val fragmentTabManager = activity.fragmentTabManager ?: return

        fragmentTabManager.openFileTab(file.absolutePath, extension)
    }
}
