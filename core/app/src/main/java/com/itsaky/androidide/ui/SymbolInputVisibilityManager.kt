package com.itsaky.androidide.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Shared preview visibility signal; no View or Activity references are retained. */
object SymbolInputVisibilityManager {
    var previewHidden by mutableStateOf(false)
        private set
    fun hideForPreview() { previewHidden = true }
    fun showFromPreview() { previewHidden = false }
    fun unregister() { previewHidden = false }
}
