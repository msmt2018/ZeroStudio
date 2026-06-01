package com.itsaky.androidide.fragments.toolbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.tabs.TabLayout
import com.itsaky.androidide.R

/**
 * Editor bottom-drawer toolbox. Tabs are metadata only until selected; the active page is created on
 * demand and the previously active fragment is removed to release view/lifecycle resources while
 * keeping its tab mounted for quick re-entry.
 */
class EditorToolboxFragment : Fragment(), EditorToolboxGridFragment.ToolSelectionListener {

  private var tabLayout: TabLayout? = null
  private var container: ViewGroup? = null
  private val openedTabs = linkedMapOf<String, EditorToolboxEntry>()
  private var selectedTabId: String = HOME_TAB_ID
  private var suppressTabCallback = false

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    val context = requireContext()
    return LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams =
          ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
          )

      tabLayout =
          TabLayout(context).apply {
            id = R.id.editor_toolbox_tabs
            tabMode = TabLayout.MODE_SCROLLABLE
            tabGravity = TabLayout.GRAVITY_START
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
          }
      addView(tabLayout)

      this@EditorToolboxFragment.container =
          androidx.fragment.app.FragmentContainerView(context).apply {
            id = R.id.editor_toolbox_fragment_container
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
          }
      addView(this@EditorToolboxFragment.container)
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    restoreTabs(savedInstanceState)
    setupTabs()
    selectTab(selectedTabId)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putStringArrayList(STATE_OPENED_TABS, ArrayList(openedTabs.keys))
    outState.putString(STATE_SELECTED_TAB, selectedTabId)
  }

  override fun onDestroyView() {
    container = null
    tabLayout = null
    super.onDestroyView()
  }

  override fun onToolSelected(entry: EditorToolboxEntry) {
    openedTabs[entry.id] = entry
    val tabs = tabLayout ?: return
    val existingTab = findTabById(entry.id)
    val tab = existingTab ?: tabs.newTab().apply {
      tag = entry.id
      text = getString(entry.titleRes)
      contentDescription = getString(entry.descriptionRes)
      tabs.addTab(this, false)
    }
    tab.select()
  }

  private fun restoreTabs(savedInstanceState: Bundle?) {
    openedTabs.clear()
    savedInstanceState?.getStringArrayList(STATE_OPENED_TABS)?.forEach { id ->
      EditorToolboxRegistry.findEntry(id)?.let { openedTabs[id] = it }
    }
    selectedTabId = savedInstanceState?.getString(STATE_SELECTED_TAB) ?: HOME_TAB_ID
  }

  private fun setupTabs() {
    val tabs = tabLayout ?: return
    tabs.clearOnTabSelectedListeners()
    tabs.removeAllTabs()
    tabs.addTab(
        tabs.newTab().apply {
          tag = HOME_TAB_ID
          text = getString(R.string.title_editor_toolbox_home)
          contentDescription = getString(R.string.desc_editor_toolbox)
        },
        false,
    )
    openedTabs.values.forEach { entry ->
      tabs.addTab(
          tabs.newTab().apply {
            tag = entry.id
            text = getString(entry.titleRes)
            contentDescription = getString(entry.descriptionRes)
          },
          false,
      )
    }
    tabs.addOnTabSelectedListener(
        object : TabLayout.OnTabSelectedListener {
          override fun onTabSelected(tab: TabLayout.Tab) {
            if (!suppressTabCallback) {
              showTab(tab.tag as? String ?: HOME_TAB_ID)
            }
          }

          override fun onTabUnselected(tab: TabLayout.Tab) = Unit

          override fun onTabReselected(tab: TabLayout.Tab) {
            if (!suppressTabCallback) {
              showTab(tab.tag as? String ?: HOME_TAB_ID)
            }
          }
        }
    )
  }

  private fun selectTab(tabId: String) {
    val tab = findTabById(tabId) ?: findTabById(HOME_TAB_ID) ?: return
    suppressTabCallback = true
    tab.select()
    suppressTabCallback = false
    showTab(tab.tag as? String ?: HOME_TAB_ID)
  }

  private fun showTab(tabId: String) {
    selectedTabId = tabId
    val containerId = container?.id ?: return
    val fragment =
        if (tabId == HOME_TAB_ID) {
          EditorToolboxGridFragment()
        } else {
          val entry = openedTabs[tabId] ?: EditorToolboxRegistry.findEntry(tabId) ?: return
          entry.fragmentClass.java.getDeclaredConstructor().newInstance()
        }
    childFragmentManager.commit {
      setReorderingAllowed(true)
      replace(containerId, fragment, tabId)
    }
  }

  private fun findTabById(id: String): TabLayout.Tab? {
    val tabs = tabLayout ?: return null
    for (index in 0 until tabs.tabCount) {
      val tab = tabs.getTabAt(index)
      if (tab?.tag == id) {
        return tab
      }
    }
    return null
  }

  companion object {
    private const val HOME_TAB_ID = "toolbox_home"
    private const val STATE_OPENED_TABS = "opened_tabs"
    private const val STATE_SELECTED_TAB = "selected_tab"
  }
}
