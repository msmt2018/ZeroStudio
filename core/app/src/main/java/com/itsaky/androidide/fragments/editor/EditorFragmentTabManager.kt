package com.itsaky.androidide.fragments.editor

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.itsaky.androidide.activities.editor.ui.state.EditorPage
import com.itsaky.androidide.activities.editor.ui.state.EditorUiState
import java.io.File
import java.util.UUID

/** Registers Fragment-backed documents; AndroidFragment owns their actual lifecycle. */
class EditorFragmentTabManager(private val state: EditorUiState) {
  data class OpenTab(val id: String, val entry: FragmentTabEntry, val arguments: Bundle?, val filePath: String?)
  private val openTabs = linkedMapOf<String, OpenTab>()

  fun openTab(entry: FragmentTabEntry, filePath: String? = null, args: Bundle? = null): String {
    val id = "$FRAGMENT_TAB_PREFIX${entry.id}:${filePath ?: UUID.randomUUID()}"
    if (id in openTabs) { switchToTab(id); return id }
    val arguments = Bundle(args ?: Bundle.EMPTY).apply { filePath?.let { putString(ARG_FILE_PATH, it) } }
    val title = filePath?.let { File(it).name } ?: entry.title
    openTabs[id] = OpenTab(id, entry, arguments, filePath)
    state.documents.register(EditorPage.FragmentPage(id, title, entry.fragmentClass, arguments))
    val tab = state.tabs.newTab().apply { tag = id; text = title; setIcon(entry.iconRes) }
    state.tabs.addTab(tab)
    switchToTab(id)
    return id
  }
  fun openFileTab(filePath: String, fileExtension: String, args: Bundle? = null): String? =
    FragmentTabRegistry.getByFileExtension(fileExtension).firstOrNull()?.let { openTab(it, filePath, args) }
  fun switchToTab(id: String): Boolean {
    if (id !in openTabs) return false
    state.documents.select(id)
    state.surface = 1
    (0 until state.tabs.tabCount).mapNotNull(state.tabs::getTabAt).firstOrNull { it.tag == id }?.let {
      if (it.position != state.tabs.selectedTabPosition) it.select()
    }
    return true
  }
  fun closeTab(id: String): Boolean {
    if (openTabs.remove(id) == null) return false
    state.documents.unregister(id)
    (0 until state.tabs.tabCount).mapNotNull(state.tabs::getTabAt).firstOrNull { it.tag == id }?.let(state.tabs::removeTab)
    return true
  }
  fun hideAllTabs() = Unit // Leaving the composition saves and destroys the Fragment view via AndroidX.
  fun getCurrentTabId(): String? = state.tabs.getTabAt(state.tabs.selectedTabPosition)?.tag as? String
  fun getOpenTabs(): List<OpenTab> = openTabs.values.toList()
  fun hasOpenTabs(): Boolean = openTabs.isNotEmpty()
  fun isTabOpen(filePath: String): Boolean = openTabs.values.any { it.filePath == filePath }
  fun getTab(id: String): OpenTab? = openTabs[id]
  fun updateTabTitle(id: String, title: String) {
    (0 until state.tabs.tabCount).mapNotNull(state.tabs::getTabAt).firstOrNull { it.tag == id }?.text = title
  }
  fun closeAllTabs() { openTabs.keys.toList().forEach(::closeTab) }
  companion object {
    private const val FRAGMENT_TAB_PREFIX = "fragment:"
    const val ARG_FILE_PATH = "file_path"
    fun isFragmentTabId(id: String?): Boolean = id?.startsWith(FRAGMENT_TAB_PREFIX) == true
  }
}
