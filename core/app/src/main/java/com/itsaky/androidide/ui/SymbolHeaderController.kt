package com.itsaky.androidide.ui

import android.view.Gravity
import android.widget.TextView
import com.itsaky.androidide.R

/**
 * Unified controller for symbol header text/cursor/fraction/page-state rendering.
 */
class SymbolHeaderController(
    private val statusView: TextView,
    private val cursorView: TextView,
) {
  private var isExternalSymbolMode: Boolean = false
  private var lastStatusText: CharSequence = ""

  fun onExternalModeChanged(external: Boolean) {
    isExternalSymbolMode = external
  }

  fun onStatusChanged(text: CharSequence, gravity: Int, isBusy: Boolean) {
    statusView.gravity = gravity
    lastStatusText = text
    statusView.text =
        if (text.isBlank() && !isBusy) {
          statusView.context.getString(R.string.msg_swipe_up)
        } else {
          text
        }
  }

  fun onActionChanged(actionText: CharSequence, progress: Int) {
    if (isExternalSymbolMode) return
    statusView.gravity = Gravity.CENTER
    statusView.text =
        if (actionText.isBlank()) {
          statusView.context.getString(R.string.msg_installing_apk)
        } else {
          actionText
        }
    cursorView.text = "${progress.coerceIn(0, 100)}%"
  }

  fun onVisualFractionChanged(fraction: Float) {
    if (isExternalSymbolMode) return
    val alpha = fraction.coerceIn(0f, 1f)
    statusView.alpha = alpha
    cursorView.alpha = alpha
  }

  fun onPageChanged(page: Int) {
    if (page == EditorBottomSheet.CHILD_ACTION) {
      statusView.gravity = Gravity.CENTER
      if (statusView.text.isNullOrBlank()) {
        statusView.text = statusView.context.getString(R.string.msg_installing_apk)
      }
    } else if (page == EditorBottomSheet.CHILD_HEADER && statusView.text.isNullOrBlank()) {
      statusView.text = statusView.context.getString(R.string.msg_swipe_up)
    }
  }

  fun onCursorChanged(text: CharSequence) {
    if (!isExternalSymbolMode) return
    cursorView.text = text
  }
}
