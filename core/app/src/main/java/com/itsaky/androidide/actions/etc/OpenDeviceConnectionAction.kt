/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.actions.etc

import android.content.Context
import android.view.MenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.core.content.ContextCompat
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorActivityAction
import com.itsaky.androidide.actions.markInvisible
import com.itsaky.androidide.fragments.debugger.connection.DeviceConnectionSheetContent
import com.itsaky.androidide.utils.flashError


/**
 * 打开设备连接管理底部弹窗的顶层 Toolbar Action。
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
    data.getActivity() ?: run {
      markInvisible()
      return
    }
    visible = true
    enabled = true
  }

  override fun getShowAsActionFlags(data: ActionData): Int = MenuItem.SHOW_AS_ACTION_ALWAYS

  @OptIn(ExperimentalMaterial3Api::class)
  override suspend fun execAction(data: ActionData): Boolean {
    val activity = data.getActivity() ?: run {
      flashError(R.string.msg_device_connection_not_available)
      return false
    }

    activity.content.dialog = {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      ModalBottomSheet(
          onDismissRequest = { activity.content.dialog = null },
          sheetState = sheetState,
      ) {
        DeviceConnectionSheetContent(
            onDismiss = { activity.content.dialog = null },
            onNavigateToPairOwn = { /* 可在此触发全屏导航 */ },
            onNavigateToPairOther = { /* 可在此触发全屏导航 */ },
            onNavigateToAdbConsole = { /* 可在此触发全屏导航 */ },
        )
      }
    }
    return true
  }
}