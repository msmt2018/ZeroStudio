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

package com.itsaky.androidide.actions.build

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.BaseBuildAction
import com.itsaky.androidide.actions.markInvisible
import com.itsaky.androidide.debugger.DebuggerController
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.android.androidAppProjects
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.resolveAttr

/**
 * PR-D1: 顶层 "🪲 调试" 工具栏按钮。
 *
 * 作为最显眼的入口点，提供一键 Attach/Detach JDWP 调试器的能力：
 *
 * - 当调试器未连接时，点击触发 [DebuggerController.toggleDebugConnection]，
 *   IDE 会主动 connect 到 127.0.0.1:5005（默认 JDWP 端口），并把当前
 *   BreakpointManager 中的所有断点通过 [com.zerostudio.debugger.api.Debugger] 重新
 *   安装。
 * - 当调试器已连接时，点击会断开连接，停止所有 VM 事件流，清理会话状态。
 *
 * 与已有的 [com.itsaky.androidide.debugger.menu.DebuggerActionMenuProvider] 不同：
 * DebuggerActionMenuProvider 注入到 Toolbar ActionMode（overflow 菜单），
 * 负责细粒度的运行控制/单步/视图；本类作为 EDITOR_TOOLBAR 顶层 icon，**最
 * 大化可视性**——任何打开的项目都能看到。
 *
 * 真正的 build+install+launch+JDWP-attach 流程由 PR-D2/PR-D3 实现。
 * 在那之前，本 action 只会去 connect 一个已经在目标设备上启动的 JDWP server。
 *
 * @author android_zero
 */
class DebugProjectAction(context: Context, override val order: Int) : BaseBuildAction() {

    override val id: String = "ide.editor.build.debugProject"

    /**
     * PR-D1: 必须在 UI 线程执行 —— 连接 / 断开会触发 [BaseEditorActivity.invalidateOptionsMenu]
     * 以让按钮 label/icon 立即更新。
     */
    override var requiresUIThread: Boolean = true

    init {
        label = context.getString(R.string.debugger_action_start_debug)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_bug)
        enabled = false
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)
        val activity = data.getActivity() ?: run {
            markInvisible()
            return
        }

        val controller = DebuggerController.getInstance()
        val hasAppModule = IProjectManager.getInstance()
            .getWorkspace()
            ?.androidAppProjects()
            ?.iterator()
            ?.hasNext() == true

        if (!hasAppModule) {
            visible = false
            enabled = false
            return
        }

        visible = true
        // 调试器已连接时禁用，让用户去 overflow 菜单中点 🪲 调试 (toggle) 断开。
        // 这样可以避免误触导致的中断。
        enabled = controller.debugger() == null && (buildService?.isBuildInProgress == false)
    }

    override fun createColorFilter(data: ActionData): ColorFilter? {
        // 未连接 → 主色；构建中 → 警告色
        val ctx = data.getContext() ?: return null
        val attr = if (buildService?.isBuildInProgress == true) {
            R.attr.colorError
        } else {
            R.attr.colorPrimary
        }
        return PorterDuffColorFilter(ctx.resolveAttr(attr), PorterDuff.Mode.SRC_ATOP)
    }

    override fun getShowAsActionFlags(data: ActionData): Int {
        // PR-D1: 调试按钮必须始终显示在 toolbar 上 —— 不可放进 overflow。
        return MenuItem.SHOW_AS_ACTION_ALWAYS
    }

    override suspend fun execAction(data: ActionData): Boolean {
        val activity = data.getActivity() ?: return false

        if (buildService?.isBuildInProgress == true) {
            flashError(R.string.debugger_err_build_in_progress)
            return false
        }

        val controller = DebuggerController.getInstance()
        val existing = controller.debugger()
        if (existing != null) {
            controller.disconnect()
            activity.flashInfo(R.string.debugger_msg_disconnected)
            return true
        }

        // PR-D1: 仅 connect，由 PR-D2 之后实现 build → install → launch → attach 串联。
        // 主机/端口暂取 build variants 中保存的 PORT_HINT (由 IdeDebuggerInitScriptPlugin
        // 注入到 manifest placeholders)；这里先 fallback 到 127.0.0.1:5005。
        val (host, port) = resolveDebugTarget(activity)
        controller.connect(host, port)
        activity.flashInfo(activity.getString(R.string.debugger_msg_connecting, host, port))
        return true
    }

    /**
     * 从 Activity 持有的 BuildPreferences / VariantInfo / DebuggerBootstrapProvider manifest
     * placeholders 中解析调试目标 host:port。当前实现 (PR-D1) 全部 fallback 到 loopback 5005；
     * 后续 PR-D2/PR-D3 接入 shizuku 之后改成读 ContentProvider meta-data。
     */
    private fun resolveDebugTarget(
        @Suppress("UNUSED_PARAMETER") context: Context
    ): Pair<String, Int> = "127.0.0.1" to 5005
}
