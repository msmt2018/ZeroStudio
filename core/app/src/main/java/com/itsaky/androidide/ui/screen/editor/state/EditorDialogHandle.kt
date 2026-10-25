/*
 *  This file is part of ZeroStudio.
 *
 *  ZeroStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ZeroStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with ZeroStudio.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.ui.screen.editor.state

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
