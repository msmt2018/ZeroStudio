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

package com.itsaky.androidide.actions.etc

import android.content.Context
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorActivityAction
import com.itsaky.androidide.actions.markInvisible
import com.itsaky.androidide.fragments.debugger.DeviceConnectionBottomSheet
import com.itsaky.androidide.utils.flashError

/**
 * 打开设备连接管理底部弹窗的顶层 Toolbar Action。
 *
 * 此 Action 注册在 [com.itsaky.androidide.actions.ActionItem.Location.EDITOR_TOOLBAR],
 * 作为编辑器顶部工具栏的独立按钮, 用户点击后会弹出
 * [DeviceConnectionBottomSheet] 底部弹窗, 进行 4 种 ADB 连接方式
 * (Local / WiFi / OTG / Fastboot) 的管理。
 *
 * 设计参考: `debugger/android-adb-shell` 参考工程的连接管理入口。
 *
 * @author android_zero
 */
class OpenDeviceConnectionAction(context: Context, override val order: Int) :
    EditorActivityAction() {

  override val id: String = ID
  override var requiresUIThread: Boolean = true

  init {
    label = context.getString(R.string.title_open_device_connection)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_android)
  }

  companion object {
    const val ID = "ide.editor.open_device_connection"
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)
    // 仅在 EditorActivityKt 上下文中可见
    data.getActivity() ?: run {
      markInvisible()
      return
    }
    visible = true
    enabled = true
  }

  override fun getShowAsActionFlags(data: ActionData): Int {
    // 顶层独立按钮, 始终显示在工具栏上
    return MenuItem.SHOW_AS_ACTION_ALWAYS
  }

  override suspend fun execAction(data: ActionData): Boolean {
    val activity = data.getActivity() ?: run {
      flashError(R.string.msg_device_connection_not_available)
      return false
    }
    DeviceConnectionBottomSheet().show(
      activity.supportFragmentManager,
      "DeviceConnectionBottomSheet",
    )
    return true
  }
}
