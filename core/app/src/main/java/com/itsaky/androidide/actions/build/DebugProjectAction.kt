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
import com.itsaky.androidide.debugger.DebugSessionLauncher
import com.itsaky.androidide.debugger.DebuggerController
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.android.androidAppProjects
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashInfo
import com.itsaky.androidide.utils.resolveAttr
import java.io.File

/**
 * PR-D1/D2: 顶层 "🪲 调试" 工具栏按钮。
 *
 * 作为最显眼的入口点，提供一键 build → install → launch → JDWP-attach
 * 的能力。点击后：
 *
 *  1. [com.itsaky.androidide.actions.openApplicationModuleChooser] 选 module
 *  2. [com.itsaky.androidide.projects.builder.BuildService.executeTasks] 跑 assembleDebug
 *  3. [com.itsaky.androidide.utils.ApkInstaller.installApk] 安装 APK
 *  4. [com.itsaky.androidide.utils.IntentUtils.launchApp] 启动 App
 *  5. [com.itsaky.androidide.debugger.JdwpPortResolver] poll 目标 App 的 JDWP 端口
 *  6. [DebuggerController.connect] 建立 JDWP 链路
 *
 * 与已有的 [com.itsaky.androidide.debugger.menu.DebuggerActionMenuProvider] 不同：
 * DebuggerActionMenuProvider 注入到 Toolbar ActionMode（overflow 菜单），
 * 负责细粒度的运行控制/单步/视图；本类作为 EDITOR_TOOLBAR 顶层 icon，**最
 * 大化可视性**——任何打开的项目都能看到。
 *
 * 完整流程由 [DebugSessionLauncher] 驱动；本 action 只是入口。
 *
 * @author android_zero
 */
class DebugProjectAction(context: Context, override val order: Int) : BaseBuildAction() {

    override val id: String = "ide.editor.build.debugProject"

    /**
     * PR-D1: 必须在 UI 线程执行 —— 会通过 [com.itsaky.androidide.activities.editor.BaseEditorActivity.invalidateOptionsMenu]
     * 让按钮 label/icon 立即更新。
     */
    override var requiresUIThread: Boolean = true

    init {
        label = context.getString(R.string.debugger_action_start_debug)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_bug)
        enabled = false
    }

    @Suppress("unused")
    private val launcher: DebugSessionLauncher = DebugSessionLauncher(context.applicationContext)

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

        // PR-D2: 启动完整 build → install → launch → JDWP-attach 流程。
        // 启动器在后台线程里完成 assemble/install/launch/port-poll/connect,
        // 期间通过 Listener 把每个 step 推回 UI 线程,这里只需要把 Listener
        // 接到 activity 的 flashInfo 即可。
        launcher.setListener(object : DebugSessionLauncher.Listener {
            override fun onBuildStarting(module, variant) {
                activity.flashInfo(
                        activity.getString(R.string.debugger_msg_build_starting, module.name))
            }
            override fun onInstallStarting(apk: File) {
                activity.flashInfo(
                        activity.getString(R.string.debugger_msg_install_starting, apk.name))
            }
            override fun onInstallCommitted() {
                activity.flashInfo(R.string.debugger_msg_install_committed)
            }
            override fun onLaunched(packageName: String) {
                activity.flashInfo(
                        activity.getString(R.string.debugger_msg_launched, packageName))
            }
            override fun onAttaching(host: String, port: Int) {
                activity.flashInfo(
                        activity.getString(R.string.debugger_msg_connecting, host, port))
            }
            override fun onConnected(host: String, port: Int) {
                activity.flashInfo(
                        activity.getString(R.string.debugger_msg_connected, host, port))
                activity.invalidateOptionsMenu()
            }
            override fun onFailed(step: DebugSessionLauncher.Step, message: String) {
                flashError(activity.getString(R.string.debugger_msg_failed, step.name, message))
            }
        })
        val started = launcher.start(data)
        if (!started) {
            flashError(R.string.debugger_err_launcher_busy)
        }
        return started
    }
}
