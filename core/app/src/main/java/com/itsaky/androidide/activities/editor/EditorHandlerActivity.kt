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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup.LayoutParams
import androidx.appcompat.view.menu.MenuBuilder
import androidx.collection.MutableIntObjectMap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout.Tab
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager
import com.itsaky.androidide.utils.EditorFragmentTabRegistrar
import com.itsaky.androidide.resources.R
import com.blankj.utilcode.util.ImageUtils
import com.itsaky.androidide.R.string
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem.Location.EDITOR_TOOLBAR
import com.itsaky.androidide.actions.ActionsRegistry.Companion.getInstance
import com.itsaky.androidide.actions.FillMenuParams
import com.itsaky.androidide.editor.language.treesitter.AidlLanguage
import com.itsaky.androidide.editor.language.treesitter.CLang
import com.itsaky.androidide.editor.language.treesitter.CmakeLanguage
import com.itsaky.androidide.editor.language.treesitter.CppLang
import com.itsaky.androidide.editor.language.treesitter.JavaLanguage
import com.itsaky.androidide.editor.language.treesitter.JsonLanguage
import com.itsaky.androidide.editor.language.treesitter.KotlinLanguage
import com.itsaky.androidide.editor.language.treesitter.LogLanguage
import com.itsaky.androidide.editor.language.treesitter.TSLanguageRegistry
import com.itsaky.androidide.editor.language.treesitter.TreeSitterLanguage
import com.itsaky.androidide.editor.language.treesitter.TomlLanguage
import com.itsaky.androidide.editor.language.treesitter.XMLLanguage
import com.itsaky.androidide.editor.language.treesitter.YamlLanguage
import com.itsaky.androidide.editor.schemes.IDEColorSchemeProvider
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.eventbus.events.editor.DocumentChangeEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentSaveEvent
import com.itsaky.androidide.eventbus.events.file.FileRenameEvent
import com.itsaky.androidide.eventbus.events.preferences.PreferenceChangeEvent
import com.itsaky.androidide.interfaces.IEditorHandler
import com.itsaky.androidide.lsp.kotlin.ui.events.LspEventBus
import com.itsaky.androidide.lsp.kotlin.ui.events.LspInstallRequestEvent
import com.itsaky.androidide.lsp.kotlin.ui.LspInstallerDialog
import com.itsaky.androidide.lsp.kotlin.KotlinLspIntegration
import com.itsaky.androidide.models.FileExtension
import com.itsaky.androidide.models.OpenedFile
import com.itsaky.androidide.models.OpenedFilesCache
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.models.SaveResult
import com.itsaky.androidide.preferences.internal.EditorPreferences
import com.itsaky.androidide.projects.internal.ProjectManagerImpl
import com.itsaky.androidide.tasks.executeAsync
import com.itsaky.androidide.ui.CodeEditorView
import com.itsaky.androidide.utils.DialogUtils.newMaterialDialogBuilder
import com.itsaky.androidide.utils.DialogUtils.newYesNoDialog
import com.itsaky.androidide.utils.IntentUtils.openImage
import com.itsaky.androidide.utils.UniqueNameBuilder
import com.itsaky.androidide.utils.flashSuccess
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Base class for EditorActivity. Handles logic for working with file editors.
 *
 * @author Akash Yadav
 * @author android_zero
 */
open class EditorHandlerActivity : ProjectHandlerActivity(), IEditorHandler,
  com.itsaky.androidide.debugger.menu.DebuggerActionMenuProvider.Host {

  protected val isOpenedFilesSaved = AtomicBoolean(false)
  private var kotlinLspInstallDialog: androidx.appcompat.app.AlertDialog? = null
  private var kotlinLspInstallCollectorJob: Job? = null
  private var openedFilesCacheWriteJob: Job? = null
  private var lastOpenedFilesCacheSignature: String? = null

  /** Fragment tab manager for managing fragment tabs like Markdown Preview */
  var fragmentTabManager: EditorFragmentTabManager? = null
    private set

  init {
    EditorFragmentTabRegistrar.registerAll()
  }

  override fun doOpenFile(file: File, selection: Range?) {
    openFileAndSelect(file, selection)
  }

  override fun doCloseAll(runAfter: () -> Unit) {
    closeAll(runAfter)
  }

  override fun provideCurrentEditor(): CodeEditorView? {
    return getCurrentEditor()
  }

  override fun provideEditorAt(index: Int): CodeEditorView? {
    return getEditorAtIndex(index)
  }

  /** Handles both file editor tabs and lifecycle-backed fragment tabs in the same TabLayout. */
  override fun onTabSelected(tab: Tab) {
    val tabId = tab.tag as? String
    if (EditorFragmentTabManager.isFragmentTabId(tabId)) {
      content.viewContainer.displayedChild = FRAGMENT_CONTAINER_INDEX
      fragmentTabManager?.switchToTab(tabId!!)
      invalidateOptionsMenu()
      return
    }

    content.viewContainer.displayedChild = EDITOR_CONTAINER_INDEX
    fragmentTabManager?.hideAllTabs()
    super.onTabSelected(tab)
  }

  override fun hasNonEditorTabs(): Boolean = fragmentTabManager?.hasOpenTabs() == true

  override fun resolveEditorIndexForTab(tab: Tab): Int {
    val tag = tab.tag as? String
    if (tag?.startsWith(EDITOR_TAB_PREFIX) == true) {
      return findIndexOfEditorByFile(File(tag.removePrefix(EDITOR_TAB_PREFIX)))
    }
    return tab.position
  }

  override fun preDestroy() {
    super.preDestroy()
    TSLanguageRegistry.instance.destroy()
    editorViewModel.removeAllFiles()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    mBuildEventListener.setActivity(this)
    super.onCreate(savedInstanceState)

    // PR-D4: 挂载调试器 Action 菜单。
    // 4 个子菜单 (运行控制 / 单步 / 断点 / 视图) 会被 MenuProvider 添加到
    // Activity 的 toolbar (与 Build/Run/Debug 等 EDITOR_TOOLBAR action 并列)
    // 以及 bottom ActionMode 中。`addMenuProvider` 是 androidx.core 提供的
    // 菜单挂载点,只需一行就能让菜单在 toolbar + overflow + ActionMode 三个
    // 出现位置都可见。
    addMenuProvider(
        com.itsaky.androidide.debugger.menu.DebuggerActionMenuProvider(this),
    )

    fragmentTabManager = EditorFragmentTabManager(
      activity = this,
      binding = content,
      containerId = content.fragmentContainer.id
    )

    editorViewModel._displayedFile.observe(this) {
      this.content.editorContainer.displayedChild = it
    }
    editorViewModel._startDrawerOpened.observe(this) { opened ->
      this.binding.editorDrawerLayout.apply {
        if (opened) openDrawer(GravityCompat.START) else closeDrawer(GravityCompat.START)
      }
    }

    editorViewModel._filesModified.observe(this) { invalidateOptionsMenu() }
    editorViewModel._filesSaving.observe(this) { invalidateOptionsMenu() }

    editorViewModel.observeFiles(this) {
      // rewrite the cached files index if there are any opened files
      val currentFile =
          getCurrentEditor()?.editor?.file?.absolutePath
              ?: run {
                editorViewModel.writeOpenedFiles(null)
                editorViewModel.openedFilesCache = null
                return@observeFiles
              }
      getOpenedFiles().also {
        val selectedTabIndex = editorViewModel.getCurrentFileIndex()
        val cache = OpenedFilesCache(currentFile, selectedTabIndex, it)
        editorViewModel.openedFilesCache = cache
        scheduleOpenedFilesCacheWrite(cache)
      }
    }

    registerTreeSitterLanguages()
    executeAsync {
      CodeEditorView.ensureTreeSitterLoaded()
      IDEColorSchemeProvider.initIfNeeded()
    }
  }

  private fun registerTreeSitterLanguages() {
    registerTreeSitterLanguage(JavaLanguage.TS_TYPE, JavaLanguage.FACTORY)
    registerTreeSitterLanguage(KotlinLanguage.TS_TYPE_KT, KotlinLanguage.FACTORY)
    registerTreeSitterLanguage(KotlinLanguage.TS_TYPE_KTS, KotlinLanguage.FACTORY)
    registerTreeSitterLanguage(LogLanguage.TS_TYPE, LogLanguage.FACTORY)
    registerTreeSitterLanguage(JsonLanguage.TS_TYPE, JsonLanguage.FACTORY)
    registerTreeSitterLanguage(TomlLanguage.TOML_TYPE, TomlLanguage.FACTORY)
    registerTreeSitterLanguage(AidlLanguage.TS_TYPE, AidlLanguage.FACTORY)
    registerTreeSitterLanguage(YamlLanguage.TS_TYPE, YamlLanguage.FACTORY)
    registerTreeSitterLanguage(YamlLanguage.TS_TYPE_YML, YamlLanguage.FACTORY)
    registerTreeSitterLanguage(XMLLanguage.TS_TYPE, XMLLanguage.FACTORY)
    registerTreeSitterLanguage(XMLLanguage.TS_TYPE_QRC, XMLLanguage.FACTORY)
    registerTreeSitterLanguage(XMLLanguage.TS_TYPE_UI, XMLLanguage.FACTORY)
    registerTreeSitterLanguage(XMLLanguage.TS_TYPE_POML, XMLLanguage.FACTORY)
    registerTreeSitterLanguage(XMLLanguage.TS_TYPE_KML, XMLLanguage.FACTORY)
    registerTreeSitterLanguage(XMLLanguage.TS_TYPE_SVG, XMLLanguage.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_CPP, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_C, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_H_small, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_H_CAPITAL_LETTERS, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_HPP, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_CP, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_CC, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_HH, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_CXX, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_CJJ, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_HXX, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_HJJ, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_CPPM, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_MPP, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_mm, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_HIN, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_HXXIN, CppLang.FACTORY)
    registerTreeSitterLanguage(CppLang.TS_TYPE_CXXIN, CppLang.FACTORY)
    registerTreeSitterLanguage(CLang.TS_TYPE_C, CLang.FACTORY)
    registerTreeSitterLanguage(CLang.TS_TYPE_M_small, CLang.FACTORY)
    registerTreeSitterLanguage(CLang.TS_TYPE_M_CAPITAL_LETTERS, CLang.FACTORY)
    registerTreeSitterLanguage(CmakeLanguage.TS_TYPE, CmakeLanguage.FACTORY)
    registerTreeSitterLanguage(CmakeLanguage.TS_TYPE_CMAKE_IN, CmakeLanguage.FACTORY)
    registerTreeSitterLanguage(CmakeLanguage.TS_TYPE_CMAKE_CTEST, CmakeLanguage.FACTORY)
    registerTreeSitterLanguage(CmakeLanguage.TS_TYPE_CMAKE_CPACK, CmakeLanguage.FACTORY)
    registerTreeSitterLanguage(CmakeLanguage.TS_TYPE_CMAKE_CBPS, CmakeLanguage.FACTORY)
    registerTreeSitterLanguage(CmakeLanguage.TS_TYPE_CMAKE_CMLISTSTXT, CmakeLanguage.FACTORY)
    registerTreeSitterLanguage(CmakeLanguage.TS_TYPE_CMAKE_CMCACHE, CmakeLanguage.FACTORY)
  }

  private fun <T : TreeSitterLanguage> registerTreeSitterLanguage(
      type: String,
      factory: TreeSitterLanguage.Factory<T>,
  ) {
    if (!TSLanguageRegistry.instance.hasLanguage(type)) {
      TSLanguageRegistry.instance.register(type, factory)
    }
  }

  override fun onPause() {
    super.onPause()

    // if the user manually closes the project, this will be true
    // in this case, don't overwrite the already saved cache
    if (!isOpenedFilesSaved.get()) {
      saveOpenedFiles()
    }
  }

  override fun onResume() {
    super.onResume()
    isOpenedFilesSaved.set(false)
  }

  override fun saveOpenedFiles() {
    writeOpenedFilesCache(
        getOpenedFiles(),
        getCurrentEditor()?.editor?.file,
        editorViewModel.getCurrentFileIndex(),
    )
  }

  private fun writeOpenedFilesCache(
      openedFiles: List<OpenedFile>,
      selectedFile: File?,
      selectedTabIndex: Int,
  ) {
    if (selectedFile == null || openedFiles.isEmpty()) {
      editorViewModel.writeOpenedFiles(null)
      editorViewModel.openedFilesCache = null
      log.debug("[onPause] No opened files. Opened files cache reset to null.")
      isOpenedFilesSaved.set(true)
      return
    }

    val cache =
        OpenedFilesCache(
            selectedFile = selectedFile.absolutePath,
            selectedTabIndex = selectedTabIndex,
            allFiles = openedFiles,
        )

    scheduleOpenedFilesCacheWrite(cache)
    editorViewModel.openedFilesCache = if (!isDestroying) cache else null
    log.debug("[onPause] Opened files cache reset to {}", editorViewModel.openedFilesCache)
    isOpenedFilesSaved.set(true)
  }

  override fun onStart() {
    super.onStart()
    startKotlinLspInstallDialogCollector()

    try {
      editorViewModel.getOrReadOpenedFilesCache(this::onReadOpenedFilesCache)
      editorViewModel.openedFilesCache = null
    } catch (err: Throwable) {
      log.error("Failed to reopen recently opened files", err)
    }
  }

  override fun onStop() {
    kotlinLspInstallCollectorJob?.cancel()
    kotlinLspInstallCollectorJob = null
    kotlinLspInstallDialog?.dismiss()
    kotlinLspInstallDialog = null
    super.onStop()
  }

  private fun startKotlinLspInstallDialogCollector() {
    if (kotlinLspInstallCollectorJob?.isActive == true) return

    kotlinLspInstallCollectorJob = lifecycleScope.launch {
      LspEventBus.installRequests.collect { request -> showKotlinLspInstallerDialog(request) }
    }
  }

  private fun showKotlinLspInstallerDialog(request: LspInstallRequestEvent) {
    kotlinLspInstallDialog?.dismiss()

    val composeView =
        ComposeView(this).apply {
          setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
          setContent {
            LspInstallerDialog(request = request) {
              kotlinLspInstallDialog?.dismiss()
              kotlinLspInstallDialog = null
            }
          }
        }

    kotlinLspInstallDialog =
        newMaterialDialogBuilder(this).setCancelable(false).setView(composeView).create().also {
          it.show()
        }
  }

  private fun onReadOpenedFilesCache(cache: OpenedFilesCache?) {
    cache ?: return
    cache.allFiles.forEach { file -> openFile(File(file.filePath), file.selection) }
    val restoreTabIndex =
        cache.selectedTabIndex.takeIf { it in 0 until cache.allFiles.size }
            ?: cache.allFiles.indexOfFirst { it.filePath == cache.selectedFile }
    if (restoreTabIndex >= 0) {
      content.tabs.getTabAt(restoreTabIndex)?.select()
    }
  }

  private fun scheduleOpenedFilesCacheWrite(cache: OpenedFilesCache) {
    val signature = "${cache.selectedFile}#${cache.selectedTabIndex}#${cache.allFiles.hashCode()}"
    if (lastOpenedFilesCacheSignature == signature) return
    lastOpenedFilesCacheSignature = signature

    openedFilesCacheWriteJob?.cancel()
    openedFilesCacheWriteJob =
        lifecycleScope.launch(Dispatchers.IO) {
          kotlinx.coroutines.delay(120)
          editorViewModel.writeOpenedFiles(cache)
        }
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    prepareOptionsMenu(menu)
    return true
  }

  @SuppressLint("RestrictedApi")
  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    if (menu is MenuBuilder) {
      menu.setOptionalIconsVisible(true)
    }

    val data = createToolbarActionData()
    getInstance().fillMenu(FillMenuParams(data, EDITOR_TOOLBAR, menu))
    return true
  }

  open fun prepareOptionsMenu(menu: Menu) {
    val data = createToolbarActionData()
    val actions = getInstance().getActions(EDITOR_TOOLBAR)
    actions.forEach { (_, action) ->
      menu.findItem(action.itemId)?.let { item ->
        action.prepare(data)

        item.isVisible = action.visible
        item.isEnabled = action.enabled
        item.title = action.label

        item.icon =
            action.icon?.apply {
              colorFilter = action.createColorFilter(data)
              alpha = if (action.enabled) 255 else 76
            }

        var showAsAction = action.getShowAsActionFlags(data)
        if (showAsAction == -1) {
          showAsAction =
              if (action.icon != null) {
                MenuItem.SHOW_AS_ACTION_IF_ROOM
              } else {
                MenuItem.SHOW_AS_ACTION_NEVER
              }
        }

        if (!action.enabled) {
          showAsAction = MenuItem.SHOW_AS_ACTION_NEVER
        }

        item.setShowAsAction(showAsAction)

        action.createActionView(data)?.let { item.actionView = it }
      }
    }
    content.editorToolbar.updateMenuDisplay()
  }

  private fun createToolbarActionData(): ActionData {
    val data = ActionData()
    val currentEditor = getCurrentEditor()

    data.put(Context::class.java, this)
    data.put(CodeEditorView::class.java, currentEditor)

    if (currentEditor != null) {
      data.put(IDEEditor::class.java, currentEditor.editor)
      data.put(File::class.java, currentEditor.file)
    }
    return data
  }

  override fun getCurrentEditor(): CodeEditorView? {
    return if (editorViewModel.getCurrentFileIndex() != -1) {
      getEditorAtIndex(editorViewModel.getCurrentFileIndex())
    } else null
  }

  /**
   * PR-D4: 实现 [com.itsaky.androidide.debugger.menu.DebuggerActionMenuProvider.Host]
   * 接口。`Activity` 本身已经是 `Context` 子类,直接 `return this` 即可;
   * 调试器菜单中的 `flashInfo(...)` 等调用会把它转成 `Activity` 用作
   * 扩展函数 receiver。
   */
  override fun requireContext(): Context = this

  override fun getEditorAtIndex(index: Int): CodeEditorView? {
    return _binding?.content?.editorContainer?.getChildAt(index) as CodeEditorView?
  }

  override fun openFileAndSelect(file: File, selection: Range?) {
    openFile(file, selection)

    getEditorForFile(file)?.editor?.also { editor ->
      editor.postInLifecycle {
        if (selection == null) {
          editor.setSelection(0, 0)
          return@postInLifecycle
        }

        editor.validateRange(selection)
        editor.setSelection(selection)
      }
    }
  }

  override fun openFile(file: File, selection: Range?): CodeEditorView? {
    val range = selection ?: Range.NONE
    if (ImageUtils.isImage(file)) {
      openImage(this, file)
      return null
    }

    val index = openFileAndGetIndex(file, range)
    if (index < 0) return null
    val tab = getEditorTabAtIndex(index)
    if (tab != null && index >= 0 && !tab.isSelected) {
      tab.select()
    }

    editorViewModel.startDrawerOpened = false
    editorViewModel.displayedFileIndex = index

    return try {
      getEditorAtIndex(index)
    } catch (th: Throwable) {
      log.error("Unable to get editor fragment at opened file index {}", index, th)
      null
    }
  }

  override fun openFileAndGetIndex(file: File, selection: Range?): Int {
    val openedFileIndex = findIndexOfEditorByFile(file)
    if (openedFileIndex != -1) {
      return openedFileIndex
    }

    if (!file.exists()) {
      return -1
    }

    val position = editorViewModel.getOpenedFileCount()

    log.info("Opening file at index {} file:{}", position, file)
    if (isKotlinSourceFile(file)) {
      KotlinLspIntegration.setup(this)
    }

    val editor = CodeEditorView(this, file, selection!!)
    editor.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    content.editorContainer.addView(editor)
    content.tabs.addTab(content.tabs.newTab().apply { tag = editorTabId(file) })

    // PR-D4: 把 BreakpointGutterManager 挂到 CodeEditor 上,点击行号 gutter
    // 弹出 BreakpointTypePicker 让用户选择"普通/条件/日志点"3 种断点类型。
    // 这是在 PR-2 引入 BreakpointGutterManager 后一直没有补上的 hook:
    // 之前只有 attach()/detach() API 暴露给外部,但实际没有人调用,
    // 所以 onBreakpointClick / onBreakpointLongClick 永远收不到事件。
    attachBreakpointGutter(editor, file)

    editorViewModel.addFile(file)
    editorViewModel.setCurrentFile(position, file)

    updateTabs()
    // onFileLoaded(editor, file)

    return position
  }

  /**
   * Phase 23 续: 这是当前唯一的 attach 入口 (之前有一份 1-arg `attachBreakpointGutter(editor)`
   * 在 line 617 是 PR-D6 留的, 跟这里签名不同导致两版并存并互相覆盖 listener — 修后保留
   * 这一份 (2-arg view, file), openFile 入口只调一次。
   *
   * <p>这一行 hook 是修复"点击行号后断点类型选择弹窗不响应"的根本 —
   * 之前 manager 内部已经有事件分发链路 (`setOnActionListener → sidebar → click`),
   * 但 `setOnActionListener` 一直没人调用,事件链路在第一站就断了。
   */

  /** PR-D6: 长按已有断点弹上下文菜单 (toggle / 编辑条件 / 编辑日志 / 删除) */
  private fun showBreakpointContextMenu(bp: com.itsaky.androidide.debugger.model.IdeBreakpoint) {
    val mgr = com.itsaky.androidide.debugger.model.BreakpointManager.getInstance()
    val items = arrayOf<CharSequence>(
        getString(com.itsaky.androidide.R.string.debugger_action_toggle_bp),
        getString(com.itsaky.androidide.R.string.debugger_action_edit_condition),
        getString(com.itsaky.androidide.R.string.debugger_action_edit_log),
        getString(com.itsaky.androidide.R.string.debugger_action_bp_delete)
    )
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle("断点 @ ${bp.file.substringAfterLast('/')}:${bp.line}")
        .setItems(items) { _, which ->
            when (which) {
                0 -> mgr.setEnabled(bp.id, !bp.isActive())
                1 -> promptCondition(File(bp.file), bp.line, bp.condition)
                2 -> promptLogMessage(File(bp.file), bp.line, bp.logMessage)
                3 -> mgr.remove(bp.id)
            }
        }
        .setNegativeButton(com.itsaky.androidide.R.string.debugger_bcd_btn_cancel, null)
        .show()
  }

  /** PR-D6: 让用户输入条件表达式。 */
  private fun promptCondition(file: File, line: Int, current: String? = null) {
    val input = android.widget.EditText(this)
    input.setText(current ?: "")
    input.hint = "i > 0 && !done"
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle(com.itsaky.androidide.R.string.debugger_bcd_condition_label)
        .setView(input)
        .setPositiveButton(com.itsaky.androidide.R.string.debugger_bcd_btn_save) { _, _ ->
            val expr = input.text.toString().trim()
            if (expr.isNotEmpty()) {
                val bm = com.itsaky.androidide.debugger.model.BreakpointManager.getInstance()
                val bp = bm.findAt(file.absolutePath, line)
                if (bp != null) {
                    // 用 manager 的 setCondition 触发 reinstallOnDebugger
                    // (直接 bp.setCondition 只改状态,JDWP 端不会更新)
                    bm.setCondition(bp.id, expr)
                } else {
                    val newBp = com.itsaky.androidide.debugger.model.IdeBreakpoint(
                        file.absolutePath, line)
                    newBp.setCondition(expr)
                    bm.add(newBp)
                }
            }
        }
        .setNegativeButton(com.itsaky.androidide.R.string.debugger_bcd_btn_cancel, null)
        .show()
  }

  /** PR-D6: 让用户输入日志消息表达式。 */
  private fun promptLogMessage(file: File, line: Int, current: String? = null) {
    val input = android.widget.EditText(this)
    input.setText(current ?: "")
    input.hint = "\"x=\" + x"
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle(com.itsaky.androidide.R.string.debugger_bcd_log_label)
        .setView(input)
        .setPositiveButton(com.itsaky.androidide.R.string.debugger_bcd_btn_save) { _, _ ->
            val expr = input.text.toString().trim()
            val bm = com.itsaky.androidide.debugger.model.BreakpointManager.getInstance()
            val bp = bm.findAt(file.absolutePath, line)
            if (bp != null) {
                // 走 manager 触发 reinstallOnDebugger
                bm.setLogMessage(bp.id, expr)
            } else if (expr.isNotEmpty()) {
                val newBp = com.itsaky.androidide.debugger.model.IdeBreakpoint(
                    file.absolutePath, line)
                newBp.setLogMessage(expr)
                bm.add(newBp)
            }
        }
        .setNegativeButton(com.itsaky.androidide.R.string.debugger_bcd_btn_cancel, null)
        .show()
  }

  private fun isKotlinSourceFile(file: File): Boolean {
    val name = file.name
    return name.endsWith(".kt", ignoreCase = true) || name.endsWith(".kts", ignoreCase = true)
  }

  override fun getEditorForFile(file: File): CodeEditorView? {
    for (i in 0 until editorViewModel.getOpenedFileCount()) {
      val editor = content.editorContainer.getChildAt(i) as? CodeEditorView
      if (file == editor?.file) return editor
    }
    return null
  }

  /**
   * PR-D4: 把 [com.itsaky.androidide.debugger.view.BreakpointGutterManager]
   * 挂到刚打开的 [CodeEditorView] 上,并注册一个 [BreakpointGutterManager.OnBreakpointActionListener]
   * 用于在用户点击行号 gutter 时弹 [com.itsaky.androidide.debugger.view.BreakpointTypePicker]。
   *
   * <p>这一行 hook 是修复"点击行号后断点类型选择弹窗不响应"的根本 —
   * 之前 manager 内部已经有事件分发链路 (`setActionListener → sidebar → click`),
   * 但 `setActionListener` 一直没人调用,事件链路在第一站就断了。
   */
  private fun attachBreakpointGutter(
    view: CodeEditorView,
    file: File,
  ) {
    val codeEditor = view.editor ?: return
    val gutter =
        com.itsaky.androidide.debugger.view.BreakpointGutterManager.attach(
            codeEditor,
            file.absolutePath,
        )
    // 修 Phase 23 续: 之前调 setActionListener 是错的, BreakpointGutterManager 实际
    // 暴露的是 setOnActionListener。修对之后 listener 才会被 manager 记录,事件
    // 链路才能跑通 (之前事件永远断在第一站)。
    //
    // Phase 23 续: listener 接口也升级到 3 方法版本 (onAddBreakpoint /
    // onEditBreakpoint / onBreakpointLongClick), BreakpointGutterManager 内部
    // 走统一接口 — 旧版 2 方法 (onBreakpointClick / onBreakpointLongClick) 不
    // 再被调用。
    gutter.setOnActionListener(
        object : com.itsaky.androidide.debugger.view.BreakpointGutterManager
            .OnBreakpointActionListener {
          override fun onAddBreakpoint(
              f: String,
              line: Int,
              entry: com.itsaky.androidide.debugger.model.BreakpointTypeCatalog.Entry,
              x: Float,
              y: Float,
          ) {
            // 用户在 gutter 点空白行,想加新断点。
            //   1) LINE fast path:直接 toggle (即时反馈, 无 dialog)
            //   2) BROWSER 拦截: needsInjector 入口 (DOM/XHR/EVENT) 暂未接 frida/xposed
            //   3) 其它: 弹 BreakpointDetailDialog (Phase 22 引入的高斯模糊磨砂配置面板)
            val bm = com.itsaky.androidide.debugger.model.BreakpointManager.getInstance()
            if (entry === com.itsaky.androidide.debugger.model.BreakpointTypeCatalog.ENTRY_LINE) {
              bm.toggle(f, line)
              return
            }
            if (entry.needsInjector) {
              com.itsaky.androidide.utils.flashInfo(
                  "BROWSER 断点需要 frida/xposed 注入器, 暂未接入")
              return
            }
            com.itsaky.androidide.debugger.view.BreakpointDetailDialog.showForNew(
                this@EditorHandlerActivity, f, line, entry, null) {}
          }

          override fun onEditBreakpoint(
              bp: com.itsaky.androidide.debugger.model.IdeBreakpoint,
              x: Float,
              y: Float,
          ) {
            // 短按已有断点 → 弹完整配置面板 (跟 Phase 22 一致)
            com.itsaky.androidide.debugger.view.BreakpointDetailDialog.showForEdit(
                this@EditorHandlerActivity, bp)
          }

          override fun onBreakpointLongClick(
              bp: com.itsaky.androidide.debugger.model.IdeBreakpoint,
              x: Float,
              y: Float,
          ) {
            // 长按已有断点 → 弹 BreakpointConditionDialog (Phase 23 续, "从 gutter 快速编辑"
            // 的最短路径)。支持 enable / 条件 / 日志 / 命中次数 / 高级 kind / dependent / 等。
            com.itsaky.androidide.debugger.BreakpointConditionDialog.showDialog(
                supportFragmentManager,
                bp.id,
            )
          }
        }
    )
    gutter.showSidebar()
  }

  override fun findIndexOfEditorByFile(file: File?): Int {
    if (file == null) {
      log.error("Cannot find index of a null file.")
      return -1
    }

    for (i in 0 until editorViewModel.getOpenedFileCount()) {
      val opened: File = editorViewModel.getOpenedFile(i)
      if (opened == file) {
        return i
      }
    }

    return -1
  }

  override fun saveAllAsync(
      notify: Boolean,
      requestSync: Boolean,
      processResources: Boolean,
      progressConsumer: ((Int, Int) -> Unit)?,
      runAfter: (() -> Unit)?,
  ) {
    editorActivityScope.launch {
      saveAll(notify, requestSync, processResources, progressConsumer)
      runAfter?.invoke()
    }
  }

  override suspend fun saveAll(
      notify: Boolean,
      requestSync: Boolean,
      processResources: Boolean,
      progressConsumer: ((Int, Int) -> Unit)?,
  ): Boolean {
    val result = saveAllResult(progressConsumer)

    // don't bother to switch the context if we don't need to
    if (notify || (result.gradleSaved && requestSync)) {
      withContext(Dispatchers.Main) {
        if (notify) {
          flashSuccess(string.all_saved)
        }

        if (result.gradleSaved && requestSync) {
          editorViewModel.isSyncNeeded = true
        }
      }
    }

    if (processResources) {
      ProjectManagerImpl.getInstance().generateSources()
    }

    return result.gradleSaved
  }

  override suspend fun saveAllResult(progressConsumer: ((Int, Int) -> Unit)?): SaveResult {
    return performFileSave {
      val result = SaveResult()
      for (i in 0 until editorViewModel.getOpenedFileCount()) {
        saveResultInternal(i, result)
        progressConsumer?.invoke(i + 1, editorViewModel.getOpenedFileCount())
      }

      return@performFileSave result
    }
  }

  override suspend fun saveResult(index: Int, result: SaveResult) {
    performFileSave { saveResultInternal(index, result) }
  }

  private suspend fun saveResultInternal(index: Int, result: SaveResult): Boolean {
    if (index < 0 || index >= editorViewModel.getOpenedFileCount()) {
      return false
    }

    val frag = getEditorAtIndex(index) ?: return false
    val fileName = frag.file?.name ?: return false

    run {
      // Must be called before frag.save()
      // Otherwise, it'll always return false
      val modified = frag.isModified
      if (!frag.save()) {
        return false
      }

      val isGradle = fileName.endsWith(".gradle") || fileName.endsWith(".gradle.kts")
      val isXml: Boolean = fileName.endsWith(".xml")
      if (!result.gradleSaved) {
        result.gradleSaved = modified && isGradle
      }

      if (!result.xmlSaved) {
        result.xmlSaved = modified && isXml
      }
    }

    val hasUnsaved = hasUnsavedFiles()

    withContext(Dispatchers.Main) {
      updateModificationState()
      // editorViewModel.areFilesModified = hasUnsaved

      // set tab as unmodified
      val tab = getEditorTabAtIndex(index) ?: return@withContext
      if (tab.text!!.startsWith('*')) {
        tab.text = tab.text!!.substring(startIndex = 1)
      }
    }

    return true
  }

  private fun hasUnsavedFiles() =
      editorViewModel.getOpenedFiles().any { file -> getEditorForFile(file)?.isModified == true }

  /** Central method to check if any open editor has modifications and update the ViewModel. */
  private fun updateModificationState() {
    editorViewModel.areFilesModified = hasUnsavedFiles()
  }

  private suspend inline fun <T : Any?> performFileSave(crossinline action: suspend () -> T): T {
    setFilesSaving(true)
    try {
      return action()
    } finally {
      setFilesSaving(false)
    }
  }

  private suspend fun setFilesSaving(saving: Boolean) {
    withContext(Dispatchers.Main.immediate) { editorViewModel.areFilesSaving = saving }
  }

  override fun areFilesModified(): Boolean {
    return editorViewModel.areFilesModified
  }

  override fun areFilesSaving(): Boolean {
    return editorViewModel.areFilesSaving
  }

  override fun closeFile(index: Int, runAfter: () -> Unit) {
    if (index < 0 || index >= editorViewModel.getOpenedFileCount()) {
      log.error("Invalid file index. Cannot close.")
      return
    }

    val opened = editorViewModel.getOpenedFile(index)
    log.info("Closing file: {}", opened)

    val editor = getEditorAtIndex(index)
    if (editor?.isModified == true) {
      log.info("File has been modified: {}", opened)
      notifyFilesUnsaved(listOf(editor)) { closeFile(index, runAfter) }
      return
    }

    editor?.close() ?: run { log.error("Cannot save file before close. Editor instance is null") }

    // !!! Bug fix !!!
    // 之前: editorViewModel.removeFile(index) 在前, 然后 getEditorTabAtIndex(index).
    //   getEditorTabAtIndex 内部用 editorViewModel.getOpenedFile(index) 算 expectedTag,
    //   但 removeFile 之后 index 已经指向"下一个文件", 所以 expectedTag 是下一个文件的 tag.
    //   迭代 TabLayout 时会找到下一个文件的 tab 并 removeTab 掉, 而真正要关闭的文件的
    //   tab 仍然留在 TabLayout 里, 它的 view 却被 editorContainer.removeViewAt(index)
    //   移走了, 变成一个"幽灵 tab" - tag 还在但对应的 editor view 已经没了, 内容被下一
    //   个文件接管, 用户就看到"关闭后 tab 还在, 内容变成邻近 tab"的现象.
    //
    // 正确做法: 先按文件的 tag 找到要删除的 tab, 再 removeFile, 再 removeTab.
    val expectedTag = editorTabId(opened)
    val tabToRemove = (0 until content.tabs.tabCount)
      .mapNotNull { content.tabs.getTabAt(it) }
      .firstOrNull { it.tag == expectedTag }

    editorViewModel.removeFile(index)
    // PR-D4: 在 editorContainer.removeViewAt 之前先取一次 CodeEditorView,
    // removeViewAt 之后 index 位置已被"下一个 editor"接管, getEditorAtIndex
    // 拿到的就不是被关闭的 editor 了。拿到 CodeEditor 引用后立即 detach
    // BreakpointGutterManager,避免侧边栏继续占用屏幕 + 事件订阅命中已
    // 销毁的 view。
    val closingEditor = getEditorAtIndex(index)
    val closingCodeEditor = closingEditor?.editor
    content.apply {
      tabToRemove?.let { tabs.removeTab(it) }
      // PR-D6: 关闭前先取 CodeEditor,detach 断点侧边栏(并取消 Sora 事件订阅),
      // 避免侧边栏继续占用已销毁 view + NPE。
      val closingEditor = getEditorAtIndex(index)
      closingEditor?.editor?.let { codeEditor ->
        com.itsaky.androidide.debugger.view.BreakpointGutterManager.detach(codeEditor)
      }
      editorContainer.removeViewAt(index)
    }
    if (closingCodeEditor != null) {
      com.itsaky.androidide.debugger.view.BreakpointGutterManager.detach(closingCodeEditor)
    }

    editorViewModel.areFilesModified = hasUnsavedFiles()

    updateTabs()
    runAfter()
  }

  /**
   * Close the tab at the given [tabIndex] in [content.tabs], dispatching to either the
   * editor file close path or the fragment tab close path based on the tab's tag.
   *
   * The TabLayout position of a fragment tab is NOT a valid index for [closeFile]
   * (which operates on the [editorViewModel] file list), so this method exists to
   * give the tab-close actions a single entry point that understands both kinds of
   * tabs.
   */
  override fun closeTabAt(tabIndex: Int, runAfter: () -> Unit) {
    if (isFinishing || isDestroyed) return
    val tab = content.tabs.getTabAt(tabIndex) ?: run {
      // Fall back to the legacy file-index behaviour for any caller that may still
      // hand us a stale file index (e.g. notifications, last-tab cleanup).
      closeFile(tabIndex, runAfter)
      return
    }
    val tabId = tab.tag as? String
    if (EditorFragmentTabManager.isFragmentTabId(tabId)) {
      log.info("Closing fragment tab at index {}: {}", tabIndex, tabId)
      fragmentTabManager?.closeTab(tabId!!)
      runAfter()
      return
    }
    // Editor file tab: convert the TabLayout position to a file index.
    val fileIndex =
        if (tabId != null && tabId.startsWith(EDITOR_TAB_PREFIX)) {
          val file = File(tabId.removePrefix(EDITOR_TAB_PREFIX))
          findIndexOfEditorByFile(file)
        } else {
          // Last-resort fallback: assume the tab position maps 1:1 to the file index.
          // This should not normally happen because every editor tab is tagged in
          // [updateTabs] / [getEditorTabAtIndex], but keeps us correct for any
          // legacy or 3rd-party callers.
          tabIndex
        }
    if (fileIndex < 0) {
      log.warn("Cannot resolve file index for tab at position {}. Tab is not closed.", tabIndex)
      runAfter()
      return
    }
    closeFile(fileIndex, runAfter)
  }

  override fun closeOthers() {
    if (editorViewModel.getOpenedFileCount() == 0) {
      return
    }

    val unsavedFiles =
        editorViewModel.getOpenedFiles().map(::getEditorForFile).filter {
          it != null && it.isModified
        }

    if (unsavedFiles.isNotEmpty()) {
      notifyFilesUnsaved(unsavedFiles) { closeOthers() }
      return
    }

    val file = editorViewModel.getCurrentFile()
    var index = 0

    // keep closing the file at index 0
    // if openedFiles[0] == file, then keep closing files at index 1
    while (editorViewModel.getOpenedFileCount() != 1) {
      val editor = getEditorAtIndex(index)

      if (editor == null) {
        log.error("Unable to save file at index {}", index)
        continue
      }

      // Index of files changes as we keep close files
      // So we compare the files instead of index
      if (file != editor.file) {
        closeFile(index)
      } else {
        index = 1
      }
    }
  }

  override fun closeAll(runAfter: () -> Unit) {
    val count = editorViewModel.getOpenedFileCount()
    val unsavedFiles =
        editorViewModel.getOpenedFiles().map(this::getEditorForFile).filter {
          it != null && it.isModified
        }

    if (unsavedFiles.isNotEmpty()) {
      // There are unsaved files
      notifyFilesUnsaved(unsavedFiles) { closeAll(runAfter) }
      return
    }

    // Files were already saved, close all files one by one
    for (i in 0 until count) {
      getEditorAtIndex(i)?.close() ?: run { log.error("Unable to close file at index {}", i) }
    }

    editorViewModel.removeAllFiles()
    fragmentTabManager?.closeAllTabs()
    content.apply {
      // PR-D6: 在 removeAllViews 之前 detach 所有已注册的断点侧边栏
      // + 取消它们的 Sora 事件订阅,避免 NPE / 内存泄漏。
      for (i in 0 until editorContainer.childCount) {
        val ed = editorContainer.getChildAt(i) as? CodeEditorView
        ed?.editor?.let { codeEditor ->
          com.itsaky.androidide.debugger.view.BreakpointGutterManager.detach(codeEditor)
        }
      }
      tabs.removeAllTabs()
      tabs.requestLayout()
      editorContainer.removeAllViews()
    }

    runAfter()
  }

  /**
   * Close every tab in the editor's TabLayout except the one at [keepTabIndex]. The
   * existing [closeOthers] only iterates over file indices in [editorViewModel] and
   * therefore silently ignores fragment tabs (Markdown preview, etc.). This method
   * walks the TabLayout itself so that both editor file tabs and fragment tabs are
   * closed correctly.
   */
  override fun closeOtherTabs(keepTabIndex: Int) {
    if (isFinishing || isDestroyed) return
    if (!hasOpenTabs()) return
    if (keepTabIndex < 0 || keepTabIndex >= content.tabs.tabCount) {
      log.warn("Invalid keep tab index {}. Falling back to closeOthers().", keepTabIndex)
      closeOthers()
      return
    }

    val keepTab = content.tabs.getTabAt(keepTabIndex)
    val keepTabId = keepTab?.tag as? String

    val unsavedFiles =
        editorViewModel.getOpenedFiles().map(this::getEditorForFile).filter {
          it != null && it.isModified
        }
    if (unsavedFiles.isNotEmpty()) {
      notifyFilesUnsaved(unsavedFiles) { closeOtherTabs(keepTabIndex) }
      return
    }

    // Snapshot the tab ids to close before mutating the TabLayout, because closing a
    // tab can shift positions and the caller expects the "keep" tab to remain at the
    // same position when the operation completes.
    val toClose = mutableListOf<String>()
    for (i in 0 until content.tabs.tabCount) {
      if (i == keepTabIndex) continue
      val tag = content.tabs.getTabAt(i)?.tag as? String ?: continue
      toClose.add(tag)
    }

    // Close fragment tabs first; their lifecycle fragments are independent of the
    // editor file indices so the order with the file-tab close loop does not matter.
    val manager = fragmentTabManager
    toClose.forEach { tabId ->
      if (EditorFragmentTabManager.isFragmentTabId(tabId) && tabId != keepTabId) {
        manager?.closeTab(tabId)
      }
    }

    // Now close file-editor tabs. We close from the highest file index to the lowest
    // so that the indices remain valid while the list shrinks.
    val fileIndices = mutableListOf<Int>()
    for (i in 0 until content.tabs.tabCount) {
      if (i == keepTabIndex) continue
      val tabId = content.tabs.getTabAt(i)?.tag as? String ?: continue
      if (tabId.startsWith(EDITOR_TAB_PREFIX)) {
        val idx = findIndexOfEditorByFile(File(tabId.removePrefix(EDITOR_TAB_PREFIX)))
        if (idx >= 0) fileIndices.add(idx)
      }
    }
    fileIndices.sortDescending()
    fileIndices.forEach { idx -> closeFile(idx) }
  }

  /**
   * Returns `true` if the editor has any open tab - either an editor file tab in
   * [editorViewModel] or a lifecycle-backed fragment tab managed by
   * [fragmentTabManager]. Used by the file tab close actions to decide whether the
   * menu item should be visible.
   */
  override fun hasOpenTabs(): Boolean {
    return editorViewModel.getOpenedFiles().isNotEmpty() ||
        (fragmentTabManager?.hasOpenTabs() == true)
  }

  override fun getOpenedFiles() =
      editorViewModel.getOpenedFiles().mapNotNull {
        val editor = getEditorForFile(it)?.editor ?: return@mapNotNull null
        OpenedFile(it.absolutePath, editor.cursorLSPRange)
      }

  private fun notifyFilesUnsaved(unsavedEditors: List<CodeEditorView?>, invokeAfter: Runnable) {
    if (isDestroying) {
      // Do not show unsaved files dialog if the activity is being destroyed
      // TODO Use a service to save files and to avoid file content loss
      for (editor in unsavedEditors) {
        editor?.markUnmodified()
      }
      invokeAfter.run()
      return
    }

    val mapped = unsavedEditors.mapNotNull { it?.file?.absolutePath }
    val builder =
        newYesNoDialog(
            context = this,
            title = getString(string.title_files_unsaved),
            message = getString(string.msg_files_unsaved, TextUtils.join("\n", mapped)),
            positiveClickListener = { dialog, _ ->
              dialog.dismiss()
              saveAllAsync(notify = true, runAfter = { runOnUiThread(invokeAfter) })
            },
        ) { dialog, _ ->
          dialog.dismiss()
          // Mark all the files as saved, then try to close them all
          for (editor in unsavedEditors) {
            editor?.markAsSaved()
          }
          invokeAfter.run()
        }
    builder.show()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onFileRenamed(event: FileRenameEvent) {
    val index = findIndexOfEditorByFile(event.file)
    if (index < 0 || index >= content.tabs.tabCount) {
      return
    }

    val editor = getEditorAtIndex(index) ?: return
    editorViewModel.updateFile(index, event.newFile)
    editor.updateFile(event.newFile)

    updateTabs()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onDocumentChange(event: DocumentChangeEvent) {
    // This now serves as a trigger to re-evaluate the modification state for the whole UI.
    updateModificationState()

    val index = findIndexOfEditorByFile(event.file.toFile())
    if (index == -1) {
      return
    }

    val tab = getEditorTabAtIndex(index) ?: return
    val editorView = getEditorAtIndex(index)

    // Update the tab's text based on the new isModified state
    if (editorView?.isModified == true) {
      if (tab.text?.startsWith('*') == false) {
        tab.text = "*${tab.text}"
      }
    } else {
      if (tab.text?.startsWith('*') == true) {
        tab.text = tab.text!!.substring(startIndex = 1)
      }
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onDocumentSaved(event: DocumentSaveEvent) {
    // When auto-save occurs, update the UI
    onFileSaved(event.file.toFile())
    // Also re-evaluate global modified state
    updateModificationState()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  override fun onBasePreferenceChanged(event: PreferenceChangeEvent) {
    // Delegate to BaseIDEActivity first for Theme handling
    super.onBasePreferenceChanged(event)

    // Handle Editor specific preferences here
    when (event.key) {
      EditorPreferences.AUTO_SAVE_ENABLED,
      EditorPreferences.AUTO_SAVE_DELAY_VALUE,
      EditorPreferences.AUTO_SAVE_DELAY_UNIT -> {
        log.debug(
            "Activity received Auto-Save Preference change: Key=${event.key}, Value=${event.value}"
        )
      }
    }
  }

  internal fun onFileModified(file: File?) {
    file ?: return
    val index = findIndexOfEditorByFile(file)
    if (index == -1) return
    val tab = getEditorTabAtIndex(index) ?: return
    val editorView = getEditorAtIndex(index)

    if (editorView?.isModified == true) {
      if (tab.text?.startsWith('*') == false) {
        tab.text = "*${tab.text}"
      }
    }
  }

  internal fun onFileSaved(file: File?) {
    file ?: return
    val index = findIndexOfEditorByFile(file)
    if (index == -1) return
    val tab = getEditorTabAtIndex(index) ?: return

    if (tab.text?.startsWith('*') == true) {
      tab.text = tab.text!!.substring(startIndex = 1)
    }
  }

  private fun getEditorTabAtIndex(editorIndex: Int): Tab? {
    if (editorIndex < 0 || editorIndex >= editorViewModel.getOpenedFileCount()) return null
    val file = editorViewModel.getOpenedFile(editorIndex)
    val expectedTag = editorTabId(file)
    for (i in 0 until content.tabs.tabCount) {
      val tab = content.tabs.getTabAt(i) ?: continue
      if (tab.tag == expectedTag) return tab
    }
    return null
  }

  private fun editorTabId(file: File): String = EDITOR_TAB_PREFIX + file.absolutePath

  companion object {
    private const val EDITOR_TAB_PREFIX = "editor:"
  }

  private fun updateTabs() {
    editorActivityScope.launch {
      val files = editorViewModel.getOpenedFiles()
      val dupliCount = mutableMapOf<String, Int>()
      // val names = MutableIntObjectMap<Pair<String, @DrawableRes Int>>()
      val names = MutableIntObjectMap<Pair<String, Int>>()
      val nameBuilder = UniqueNameBuilder<File>("", File.separator)

      files.forEach {
        var count = dupliCount[it.name] ?: 0
        dupliCount[it.name] = ++count
        nameBuilder.addPath(it, it.path)
      }

      for (index in files.indices) {
        val file = files.getOrNull(index) ?: continue
        val count = dupliCount[file.name] ?: 0

        val isModified = getEditorAtIndex(index)?.isModified == true
        var name = if (count > 1) nameBuilder.getShortPath(file) else file.name
        if (isModified) {
          name = "*$name"
        }

        names[index] = name to FileExtension.Factory.forFile(file).icon
      }

      withContext(Dispatchers.Main) {
        names.forEach { index, (name, iconId) ->
          val tab = getEditorTabAtIndex(index) ?: return@forEach
          tab.tag = editorTabId(editorViewModel.getOpenedFile(index))
          tab.icon = ResourcesCompat.getDrawable(resources, iconId, theme)
          tab.text = name
        }

        // !!! Bug fix - 清理"幽灵 tab" !!!
        // 之前 updateTabs 只负责"按 tag 找 tab, 更新 text/icon". 关闭文件时
        // 如果上一个版本的 closeFile 漏删了某个 tab (例如上面已修的 removeFile/
        // getEditorTabAtIndex 顺序问题), 那个 tab 会以"已关闭文件"的 tag 永远
        // 留在 TabLayout 里 - 它的 view 已经被 editorContainer.removeViewAt
        // 移走, 但 TabLayout 还显示着它的 text/icon, 用户看到的就是"关闭后
        // tab 还在"+"内容变成邻近 tab".
        //
        // 这里加一道保险: 扫一遍 TabLayout, 凡是 tag 不在当前文件列表里的 tab
        // (并且不是 fragment tab - 那些由 fragmentTabManager 单独管理), 一律
        // 移除. 配合 closeFile 修法, 保证 TabLayout 和 editorViewModel 永远
        // 1:1 对齐, 不会再有幽灵 tab.
        val currentFileTags = files.map { editorTabId(it) }.toSet()
        val staleTabs = mutableListOf<Tab>()
        for (i in 0 until content.tabs.tabCount) {
            val t = content.tabs.getTabAt(i) ?: continue
            val tag = t.tag as? String ?: continue
            // fragment tab 由 fragmentTabManager 单独管理, 这里不碰
            if (EditorFragmentTabManager.isFragmentTabId(tag)) continue
            if (tag !in currentFileTags) {
                log.warn("updateTabs: removing stale editor tab with tag={} (file no longer in model)", tag)
                staleTabs.add(t)
            }
        }
        staleTabs.forEach { content.tabs.removeTab(it) }
      }
    }
  }
}
