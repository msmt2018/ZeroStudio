package com.itsaky.androidide.fragments.toolbox

import android.studio.zero.regular.expression.preview.RegexPreviewFragment
import android.zero.studio.chatai.server.mcp.McpFragment
import com.itsaky.androidide.R
import com.itsaky.androidide.repository.dependencies.analyzer.ui.DependencyUpdateFragment
import me.rerere.rikkahub.RouteFragment

/**
 * Central registry for editor toolbox tiles. Add or remove entries here to plug tools into the
 * toolbox without changing the toolbox UI/container implementation.
 */
object EditorToolboxRegistry {

  private val entries = linkedMapOf<String, EditorToolboxEntry>()

  init {
    register(
        EditorToolboxEntry(
            id = "dependency_updates",
            titleRes = R.string.title_dependency_updates,
            descriptionRes = R.string.desc_dependency_updates,
            iconRes = R.drawable.ic_package,
            fragmentClass = DependencyUpdateFragment::class,
        )
    )
    register(
        EditorToolboxEntry(
            id = "regex_preview",
            titleRes = R.string.title_regular_preview,
            descriptionRes = R.string.desc_regular_preview,
            iconRes = R.drawable.ic_find_replace,
            fragmentClass = RegexPreviewFragment::class,
        )
    )
    register(
        EditorToolboxEntry(
            id = "mcp_server",
            titleRes = R.string.title_mcp_server,
            descriptionRes = R.string.desc_mcp_server,
            iconRes = R.drawable.ic_ai_mcp_server,
            fragmentClass = McpFragment::class,
        )
    )
    register(
        EditorToolboxEntry(
            id = "chatai_route",
            titleRes = R.string.title_chatai_route,
            descriptionRes = R.string.desc_chatai_route,
            iconRes = R.drawable.ic_account,
            fragmentClass = RouteFragment::class,
        )
    )
  }

  @Synchronized
  fun register(entry: EditorToolboxEntry) {
    entries[entry.id] = entry
  }

  @Synchronized
  fun unregister(id: String) {
    entries.remove(id)
  }

  @Synchronized
  fun getEntries(): List<EditorToolboxEntry> = entries.values.toList()

  @Synchronized
  fun findEntry(id: String): EditorToolboxEntry? = entries[id]
}
