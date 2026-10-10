/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.activities.editor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller.SessionCallback
import android.os.Bundle
import android.os.Process
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.annotation.GravityInt
import androidx.appcompat.view.menu.MenuBuilder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.ThreadUtils
import com.itsaky.androidide.R
import com.itsaky.androidide.R.string
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.ActionItem.Location.EDITOR_FILE_TABS
import com.itsaky.androidide.actions.ActionsRegistry
import com.itsaky.androidide.actions.FillMenuParams
import com.itsaky.androidide.actions.SidebarActionItem
import com.itsaky.androidide.actions.internal.DefaultActionsRegistry
import com.itsaky.androidide.actions.menu.EditorLineOperations
import com.itsaky.androidide.activities.editor.ui.screen.EditorActivityContent
import com.itsaky.androidide.activities.editor.ui.screen.EditorMenuDialog
import com.itsaky.androidide.activities.editor.ui.state.EditorPage
import com.itsaky.androidide.activities.editor.ui.state.EditorUiState
import com.itsaky.androidide.adapters.DiagnosticsAdapter
import com.itsaky.androidide.adapters.SearchListAdapter
import com.itsaky.androidide.app.IDEActivity
import com.itsaky.androidide.events.InstallationResultEvent
import com.itsaky.androidide.fragments.SearchResultFragment
import com.itsaky.androidide.fragments.sidebar.FileTreeFragment
import com.itsaky.androidide.handlers.EditorActivityLifecyclerObserver
import com.itsaky.androidide.handlers.LspHandler.registerLanguageServers
import com.itsaky.androidide.interfaces.DiagnosticClickListener
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.lsp.models.DiagnosticItem
import com.itsaky.androidide.models.DiagnosticGroup
import com.itsaky.androidide.models.OpenedFile
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.models.SearchResult
import com.itsaky.androidide.preferences.internal.BuildPreferences
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.tasks.cancelIfActive
import com.itsaky.androidide.tasks.runOnUiThread
import com.itsaky.androidide.ui.CodeEditorView
import com.itsaky.androidide.ui.ComposeEditorTabs
import com.itsaky.androidide.ui.ComposeEditorTabs.Tab
import com.itsaky.androidide.ui.SymbolInputVisibilityManager
import com.itsaky.androidide.utils.ApkInstallationSessionCallback
import com.itsaky.androidide.utils.InstallationResultHandler.onResult
import com.itsaky.androidide.utils.IntentUtils
import com.itsaky.androidide.utils.MemoryUsageWatcher
import com.itsaky.androidide.utils.flashInfo
import com.itsaky.androidide.viewmodel.EditorViewModel
import com.itsaky.androidide.xml.resources.ResourceTableRegistry
import com.itsaky.androidide.xml.versions.ApiVersionsRegistry
import com.itsaky.androidide.xml.widgets.WidgetTableRegistry
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import org.slf4j.Logger
import org.slf4j.LoggerFactory



/** Owns editor business callbacks; all editor chrome is rendered by Compose. */
@Suppress("MemberVisibilityCanBePrivate")
abstract class BaseEditorActivity : IDEActivity(), ComposeEditorTabs.OnTabSelectedListener, DiagnosticClickListener {
  protected val mLifecycleObserver = EditorActivityLifecyclerObserver()
  protected val memoryUsageWatcher = MemoryUsageWatcher()
  private val bottomSheetHeaderHideReasons = mutableSetOf<String>()
  protected val editorActivityScope = CoroutineScope(Dispatchers.Default)
  var isDestroying = false
    protected set
  internal var installationCallback: ApkInstallationSessionCallback? = null
  var uiDesignerResultLauncher: ActivityResultLauncher<Intent>? = null
  val editorViewModel by viewModels<EditorViewModel>()
  internal var _editorUi: EditorUiState? = null
  val content get() = checkNotNull(_editorUi)
  override val subscribeToEvents get() = true
  override val useLegacyViewContent get() = false
  override var eteUpdateDecorViewPaddingInLandscape = false
  private var cursorPositionReceipt: SubscriptionReceipt<SelectionChangeEvent>? = null
  val toolbarMenu by lazy { MenuBuilder(this) }
  private val editorMenuProviders = mutableListOf<MenuProvider>()
  private var optionsMenuInvalidator: Runnable? = null
  val symbolInputHeight: Int get() = _editorUi?.symbolHeight ?: 0

  companion object {
    @JvmStatic protected val PROC_IDE = "IDE"
    @JvmStatic protected val PROC_GRADLE_TOOLING = "Gradle Tooling"
    @JvmStatic protected val PROC_GRADLE_DAEMON = "Gradle Daemon"
    @JvmStatic protected val log: Logger = LoggerFactory.getLogger(BaseEditorActivity::class.java)
    protected const val EDITOR_CONTAINER_INDEX = 0
    protected const val FRAGMENT_CONTAINER_INDEX = 1
    protected const val NO_EDITOR_CONTAINER_INDEX = 2
    const val KEY_PROJECT_PATH = "saved_projectPath"
  }

  protected abstract fun provideCurrentEditor(): CodeEditorView?
  protected abstract fun provideEditorAt(index: Int): CodeEditorView?
  protected abstract fun doOpenFile(file: File, selection: Range?)
  protected abstract fun doDismissSearchProgress()
  protected abstract fun getOpenedFiles(): List<OpenedFile>
  internal abstract fun doConfirmProjectClose()
  open fun getCurrentEditor(): CodeEditorView? = provideCurrentEditor()
  open fun openFileAndSelect(file: File, selection: Range?) { doOpenFile(file, selection) }
  open fun showFlashInfo(msg: String?) { flashInfo(msg) }

  @Composable
  final override fun ComposeScreen() {
    _editorUi?.let { EditorActivityContent(this, it) }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    _editorUi = EditorUiState(this)
    super.onCreate(savedInstanceState)
    registerLanguageServers(this)
    savedInstanceState?.getString(KEY_PROJECT_PATH)?.let { IProjectManager.getInstance().openProject(it) }
    lifecycle.addObserver(mLifecycleObserver)
    content.tabs.addOnTabSelectedListener(this)
    toolbarMenu.setCallback(object : MenuBuilder.Callback {
      override fun onMenuItemSelected(menu: MenuBuilder, item: MenuItem): Boolean =
        editorMenuProviders.any { it.onMenuItemSelected(item) }
      override fun onMenuModeChange(menu: MenuBuilder) = Unit
    })
    optionsMenuInvalidator = Runnable {
      if (!isDestroying && _editorUi != null) {
        toolbarMenu.clear()
        onCreateOptionsMenu(toolbarMenu)
        editorMenuProviders.forEach { it.onCreateMenu(toolbarMenu, menuInflater) }
        onPrepareOptionsMenu(toolbarMenu)
        editorMenuProviders.forEach { it.onPrepareMenu(toolbarMenu) }
        content.toolbarVersion++
      }
    }
    setupSidebarPages()
    content.bottomSheet.start(this)
    savedInstanceState?.getString("editor.sidebar.page")?.let(content.sidebar::select)
    savedInstanceState?.getString("editor.bottom.page")?.let(content.bottomSheet.pages::select)
    content.drawerOpen = savedInstanceState?.getBoolean("editor.sidebar.open") ?: false
    editorViewModel.startDrawerOpened = content.drawerOpen
    content.bottomSheet.visible = savedInstanceState?.getBoolean("editor.bottom.open") ?: false
    editorViewModel._isBuildInProgress.observe(this) { updateBuildState() }
    editorViewModel._isInitializing.observe(this) { updateBuildState() }
    editorViewModel._statusText.observe(this) { _editorUi?.bottomSheet?.setStatus(it.first, it.second) }
    editorViewModel.observeFiles(this) { files ->
      if (_editorUi != null) {
        content.tabs.visible = !files.isNullOrEmpty() || hasNonEditorTabs()
        if (!content.tabs.visible) content.surface = NO_EDITOR_CONTAINER_INDEX
      }
      invalidateOptionsMenu()
    }
    memoryUsageWatcher.listener = memoryUsageListener
    memoryUsageWatcher.watchProcess(Process.myPid(), PROC_IDE)
    resetMemUsageChart()
    invalidateOptionsMenu()
    
    content.tabs.onCloseTab = { tab -> closeTabAt(tab.position) }
content.tabs.onCloseOtherTabs = { tab -> closeOtherTabs(tab.position) }
content.tabs.onCloseAllTabs = { closeAll {} }
  }

  fun registerEditorMenuProvider(provider: MenuProvider) {
    editorMenuProviders.add(provider)
    invalidateOptionsMenu()
  }

  /** Register Fragment or Compose contributions for this editor Activity. IDs must be unique. */
  fun registerSidebarPage(page: EditorPage) = content.sidebar.register(page)
  fun unregisterSidebarPage(id: String) = content.sidebar.unregister(id)
  fun registerIdePage(page: EditorPage) = content.bottomSheet.pages.register(page)
  fun unregisterIdePage(id: String) = content.bottomSheet.pages.unregister(id)

  private fun setupSidebarPages() {
    ActionsRegistry.getInstance()
      .getActions(ActionItem.Location.EDITOR_SIDEBAR).values
      .sortedBy { it.order }.forEach { action ->
        val sidebarAction = action as? SidebarActionItem ?: return@forEach
        val type = sidebarAction.fragmentClass
        if (type != null) content.sidebar.register(
          EditorPage.FragmentPage(action.id, action.label.ifBlank { action.id.substringAfterLast('.') }, type.java)
        ) else content.sidebar.register(
          EditorPage.ActionPage(action.id, action.label) {
            val data = ActionData().apply { put(Context::class.java, this@BaseEditorActivity) }
            action.prepare(data)
            if (action.enabled && action.visible) {
              content.drawerOpen = false
              (ActionsRegistry.getInstance() as DefaultActionsRegistry).executeAction(action, data)
            }
          }
        )
      }
  }

  private fun updateBuildState() {
    _editorUi?.busy = editorViewModel.isBuildInProgress || editorViewModel.isInitializing
    invalidateOptionsMenu()
  }

  private val memoryUsageListener = MemoryUsageWatcher.MemoryUsageListener { usages ->
    val samples = mutableListOf<Pair<String, List<Float>>>()
    usages.forEachValue { proc -> samples.add(proc.pname to proc.usageHistory.map { it / (1024f * 1024f) }) }
    runOnUiThread { _editorUi?.memories = samples }
  }
  protected fun resetMemUsageChart() { _editorUi?.memories = emptyList() }

  override fun onPause() {
    super.onPause()
    memoryUsageWatcher.listener = null
    memoryUsageWatcher.stopWatching(false)
    isDestroying = isFinishing
    getFileTreeFragment()?.saveTreeState()
  }
  override fun onResume() {
    super.onResume()
    memoryUsageWatcher.listener = memoryUsageListener
    memoryUsageWatcher.startWatching()
    invalidateOptionsMenu()
    runCatching { getFileTreeFragment()?.listProjectFiles() }
  }
  override fun onSaveInstanceState(outState: Bundle) {
    outState.putString("editor.sidebar.page", content.sidebar.selectedId)
    outState.putString("editor.bottom.page", content.bottomSheet.pages.selectedId)
    outState.putBoolean("editor.sidebar.open", content.drawerOpen)
    outState.putBoolean("editor.bottom.open", content.bottomSheet.visible)
    outState.putString(KEY_PROJECT_PATH, IProjectManager.getInstance().projectDirPath.orEmpty())
    super.onSaveInstanceState(outState)
  }
  override fun onDestroy() {
    // Project shutdown is reserved for an explicit finish, matching the existing lifecycle.
    isDestroying = isFinishing
    cursorPositionReceipt?.unsubscribe()
    preDestroy()
    isDestroying = true
    super.onDestroy()
    postDestroy()
  }
  protected open fun preDestroy() {
    optionsMenuInvalidator?.let { ThreadUtils.getMainHandler().removeCallbacks(it) }
    optionsMenuInvalidator = null
    installationCallback?.destroy()
    installationCallback = null
    SymbolInputVisibilityManager.unregister()
    memoryUsageWatcher.stopWatching(true)
    memoryUsageWatcher.listener = null
    editorActivityScope.cancelIfActive("Activity is being destroyed")
    _editorUi?.bottomSheet?.close()
    _editorUi = null
  }
  protected open fun postDestroy() {
    if (isFinishing) {
      Lookup.getDefault().unregisterAll()
      ApiVersionsRegistry.getInstance().clear()
      ResourceTableRegistry.getInstance().clear()
      WidgetTableRegistry.getInstance().clear()
    }
  }
  override fun invalidateOptionsMenu() {
    if (isDestroying || isFinishing) return
    optionsMenuInvalidator?.let {
      ThreadUtils.getMainHandler().removeCallbacks(it)
      ThreadUtils.getMainHandler().postDelayed(it, 150)
    }
  }
  protected fun releaseBottomSheetHeaderHide(reason: String) {
    bottomSheetHeaderHideReasons.remove(reason)
    _editorUi?.headerVisible = bottomSheetHeaderHideReasons.isEmpty()
  }
  protected fun requestBottomSheetHeaderHide(reason: String) {
    bottomSheetHeaderHideReasons.add(reason)
    _editorUi?.headerVisible = false
  }
  override fun onTabSelected(tab: Tab) {
    if (isDestroying || _editorUi == null) return
    val position = resolveEditorIndexForTab(tab)
    if (position < 0) return
    editorViewModel.displayedFileIndex = position
    val view = provideEditorAt(position) ?: return
    content.currentEditor = view
    EditorLineOperations.applyReadOnlyState(view.editor!!, this)
    view.onEditorSelected()
    bindCursorPositionSync(view)
    updateCursorPositionIndicator(view)
    editorViewModel.setCurrentFile(position, view.file)
    refreshSymbolInput(view)
    invalidateOptionsMenu()
  }
  protected open fun resolveEditorIndexForTab(tab: Tab): Int = tab.position
  protected open fun hasNonEditorTabs(): Boolean = false
  override fun onTabUnselected(tab: Tab) = Unit
  override fun onTabReselected(tab: Tab) {
    if (isDestroying) return
    val menu = MenuBuilder(this)
    val data = ActionData().apply { put(Context::class.java, this@BaseEditorActivity) }
    ActionsRegistry.getInstance().fillMenu(FillMenuParams(data, EDITOR_FILE_TABS, menu))
    content.dialog = { EditorMenuDialog(menu) { content.dialog = null } }
  }
  fun refreshSymbolInput() { provideCurrentEditor()?.let(::refreshSymbolInput) }
  fun refreshSymbolInput(editor: CodeEditorView) { _editorUi?.currentEditor = editor }
  private fun bindCursorPositionSync(editorView: CodeEditorView) {
    cursorPositionReceipt?.unsubscribe()
    cursorPositionReceipt = editorView.editor?.subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
      if (_editorUi != null) updateCursorPositionIndicator(editorView)
    }
  }
  private fun updateCursorPositionIndicator(editorView: CodeEditorView) {
    editorView.editor?.cursor?.let { _editorUi?.cursor = "${it.leftLine + 1}:${it.leftColumn + 1}" }
  }
  open fun hideBottomSheet() { _editorUi?.bottomSheet?.forceCollapse() }
  open fun showSearchResults() { content.bottomSheet.selectTabByFragmentClass(SearchResultFragment::class.java); content.bottomSheet.tryExpandSheetFromControl() }
  open fun openDebuggerTab(fragmentClass: Class<out Fragment>) {
    content.bottomSheet.selectTabByFragmentClass(fragmentClass)
    content.bottomSheet.tryExpandSheetFromControl()
  }
  open fun setSearchResultAdapter(adapter: SearchListAdapter) { content.bottomSheet.setSearchResultAdapter(adapter) }
  open fun setDiagnosticsAdapter(adapter: DiagnosticsAdapter) { content.bottomSheet.setDiagnosticsAdapter(adapter) }
  open fun handleDiagnosticsResultVisibility(errorVisible: Boolean) { content.bottomSheet.handleDiagnosticsResultVisibility(errorVisible) }
  open fun handleSearchResultVisibility(errorVisible: Boolean) { content.bottomSheet.handleSearchResultVisibility(errorVisible) }
  open fun getFileTreeFragment(): FileTreeFragment? {
    if (isDestroying) return null
    // AndroidFragment owns the container tag and recreates the page after selection changes.
    return supportFragmentManager.fragments.filterIsInstance<FileTreeFragment>()
      .firstOrNull { it.isAdded && it.view != null }
  }
  fun doSetStatus(text: CharSequence, @GravityInt gravity: Int) { editorViewModel.statusText = text; editorViewModel.statusGravity = gravity }
  open fun showFirstBuildNotice() { showEditorMessage(getString(string.title_first_build), getString(string.msg_first_build)) }
  fun showEditorMessage(title: String, message: String, confirm: () -> Unit = {}) {
    content.dialog = {
      AlertDialog(onDismissRequest = { content.dialog = null }, title = { Text(title) }, text = { SelectionContainer { Text(message, Modifier.verticalScroll(rememberScrollState())) } }, confirmButton = {
        TextButton(onClick = { content.dialog = null; confirm() }) { Text(getString(android.R.string.ok)) }
      })
    }
  }
  fun confirmEditorAction(title: String, message: String, yes: () -> Unit, no: () -> Unit = {}) {
    content.dialog = {
      AlertDialog(onDismissRequest = { content.dialog = null }, title = { Text(title) }, text = { SelectionContainer { Text(message, Modifier.verticalScroll(rememberScrollState())) } }, confirmButton = {
        TextButton(onClick = { content.dialog = null; yes() }) { Text(getString(string.yes)) }
      }, dismissButton = {
        TextButton(onClick = { content.dialog = null; no() }) { Text(getString(string.no)) }
      })
    }
  }
  open fun installationSessionCallback(): SessionCallback = ApkInstallationSessionCallback(this).also { installationCallback = it }
  override fun onGroupClick(group: DiagnosticGroup?) {
    if (isDestroying || _editorUi == null) return
    if (group?.file?.exists() == true && FileUtils.isUtf8(group.file)) {
      doOpenFile(group.file, null)
      hideBottomSheet()
    }
  }

  override fun onDiagnosticClick(file: File, diagnostic: DiagnosticItem) {
    if (isDestroying || _editorUi == null) return
    doOpenFile(file, diagnostic.range)
    hideBottomSheet()
  }

  open fun handleSearchResults(map: Map<File, List<SearchResult>>?) {
    if (isDestroying || _editorUi == null) return
    val results = map ?: emptyMap()
    setSearchResultAdapter(
        SearchListAdapter(
            results,
            { file ->
              doOpenFile(file, null)
              hideBottomSheet()
            },
        ) { match ->
          doOpenFile(match.file, match)
          hideBottomSheet()
        }
    )
    showSearchResults()
    doDismissSearchProgress()
  }

  @Subscribe(threadMode = MAIN)
  open fun onInstallationResult(event: InstallationResultEvent) {
    val intent = event.intent
    if (isDestroying || _editorUi == null) return

    val packageName = onResult(this, intent) ?: return

    if (BuildPreferences.launchAppAfterInstall) {
      IntentUtils.launchApp(this, packageName)
      return
    }

    confirmEditorAction(getString(string.app_name), getString(string.msg_action_open_application), { IntentUtils.launchApp(this, packageName) })
  }

}
