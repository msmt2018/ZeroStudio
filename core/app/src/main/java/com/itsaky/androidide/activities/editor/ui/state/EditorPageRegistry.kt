package com.itsaky.androidide.activities.editor.ui.state

import android.os.Bundle
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment

/** A stable page identity shared by the sidebar and IDE drawer. */
sealed interface EditorPage {
    val id: String
    val title: String

    /** The FragmentManager owns creation, saved state, and lifecycle of this page. */
    data class FragmentPage(
        override val id: String,
        override val title: String,
        val fragmentClass: Class<out Fragment>,
        val arguments: Bundle = Bundle.EMPTY,
    ) : EditorPage

    data class ActionPage(override val id: String, override val title: String, val onClick: () -> Unit) : EditorPage

    /** The page receives normal Compose lifecycle and saveable-state owners from its host. */
    data class ScreenPage(
        override val id: String,
        override val title: String,
        val content: @Composable () -> Unit,
    ) : EditorPage
}

/**
 * Activity-scoped registry. Register pages on the main thread after recreation, then restore
 * the selected ID. Do not keep this registry in a singleton: screen lambdas may capture a host.
 */
@Stable
@MainThread
class EditorPageRegistry {
    var pages: List<EditorPage> by mutableStateOf(emptyList())
        private set
    var selectedId: String? by mutableStateOf(null)
        private set
    val selectedPage: EditorPage? get() = pages.firstOrNull { it.id == selectedId }

    fun register(page: EditorPage) {
        require(page.id.isNotBlank()) { "Page ID must not be blank" }
        require(pages.none { it.id == page.id }) { "Page already registered: ${page.id}" }
        pages = pages + page
        if (selectedId == null && page !is EditorPage.ActionPage) selectedId = page.id
    }

    fun select(id: String): Boolean {
        val page = pages.firstOrNull { it.id == id } ?: return false
        if (page is EditorPage.ActionPage) { page.onClick(); return true }
        selectedId = id
        return true
    }

    fun unregister(id: String): Boolean {
        val index = pages.indexOfFirst { it.id == id }
        if (index < 0) return false
        pages = pages.filterNot { it.id == id }
        if (selectedId == id) selectedId = pages.filterNot { it is EditorPage.ActionPage }.let { it.getOrNull(index.coerceAtMost(it.lastIndex))?.id }
        return true
    }

    fun clear() {
        pages = emptyList()
        selectedId = null
    }
}
