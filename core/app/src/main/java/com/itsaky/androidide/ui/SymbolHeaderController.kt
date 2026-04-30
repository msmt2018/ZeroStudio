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
  private data class State(
      val isExternalSymbolMode: Boolean = false,
      val statusText: CharSequence = "",
      val statusGravity: Int = Gravity.CENTER,
      val cursorText: CharSequence = "",
      val alpha: Float = 1f,
  )

  private var state = State()

  private fun render(newState: State) {
    state = newState
    statusView.gravity = newState.statusGravity
    statusView.text =
        if (newState.statusText.isBlank() && !newState.isExternalSymbolMode) {
          statusView.context.getString(R.string.msg_swipe_up)
        } else {
          newState.statusText
        }
    cursorView.text = newState.cursorText
    statusView.alpha = newState.alpha
    cursorView.alpha = newState.alpha
  }

  fun onExternalModeChanged(external: Boolean) {
    render(state.copy(isExternalSymbolMode = external))
  }

  fun onStatusChanged(text: CharSequence, gravity: Int, isBusy: Boolean) {
    val statusText =
        if (text.isBlank() && !isBusy) statusView.context.getString(R.string.msg_swipe_up) else text
    render(state.copy(statusText = statusText, statusGravity = gravity))
  }

  fun onActionChanged(actionText: CharSequence, progress: Int) {
    if (state.isExternalSymbolMode) return
    val statusText =
        if (actionText.isBlank()) {
          statusView.context.getString(R.string.msg_installing_apk)
        } else {
          actionText
        }
    render(
        state.copy(
            statusText = statusText,
            statusGravity = Gravity.CENTER,
            cursorText = "${progress.coerceIn(0, 100)}%"
        )
    )
  }

  fun onVisualFractionChanged(fraction: Float) {
    if (state.isExternalSymbolMode) return
    val alpha = fraction.coerceIn(0f, 1f)
    render(state.copy(alpha = alpha))
  }

  fun onPageChanged(page: Int) {
    var text = state.statusText
    var gravity = state.statusGravity
    if (page == EditorBottomSheet.CHILD_ACTION) {
      gravity = Gravity.CENTER
      if (text.isBlank()) {
        text = statusView.context.getString(R.string.msg_installing_apk)
      }
    } else if (page == EditorBottomSheet.CHILD_HEADER && text.isBlank()) {
      text = statusView.context.getString(R.string.msg_swipe_up)
    }
    render(state.copy(statusText = text, statusGravity = gravity))
  }

  fun onCursorChanged(text: CharSequence) {
    if (!state.isExternalSymbolMode) return
    render(state.copy(cursorText = text))
  }
}
