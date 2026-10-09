package com.itsaky.androidide.activities.editor.ui.state

import androidx.compose.runtime.Composable

/** Imperative business callback adapter for a Compose dialog, with identity-safe dismissal. */
class EditorDialogHandle(
    private val state: () -> EditorUiState?,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    private var onShow: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private val body: @Composable () -> Unit = { content(::dismiss) }
    val isShowing get() = state()?.dialog === body
    fun show() { state()?.dialog = body; onShow?.invoke() }
    fun dismiss() { if (isShowing) { state()?.dialog = null; onDismiss?.invoke() } }
    fun setOnShowListener(listener: () -> Unit) { onShow = listener }
    fun setOnDismissListener(listener: () -> Unit) { onDismiss = listener }
}
