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

import android.content.Intent
import android.content.pm.PackageInstaller.SessionCallback
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.zero.studio.widget.editor.symbolinput.SymbolManagerActivity
import android.os.Process
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.annotation.GravityInt
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.collection.MutableIntIntMap
import androidx.core.graphics.Insets
import androidx.core.view.GravityCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import com.blankj.utilcode.constant.MemoryConstants
import com.blankj.utilcode.util.ConvertUtils.byte2MemorySize
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.ThreadUtils
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.Tab
import com.itsaky.androidide.R
import com.itsaky.androidide.R.string
import com.itsaky.androidide.actions.ActionItem.Location.EDITOR_FILE_TABS
import com.itsaky.androidide.actions.menu.EditorLineOperations
import com.itsaky.androidide.adapters.DiagnosticsAdapter
import com.itsaky.androidide.adapters.SearchListAdapter
import com.itsaky.androidide.app.EdgeToEdgeIDEActivity
import com.itsaky.androidide.databinding.ActivityEditorBinding
import com.itsaky.androidide.databinding.ContentEditorBinding
import com.itsaky.androidide.databinding.LayoutDiagnosticInfoBinding
import com.itsaky.androidide.events.InstallationResultEvent
import com.itsaky.androidide.fragments.SearchResultFragment
import com.itsaky.androidide.fragments.sidebar.EditorSidebarFragment
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
import com.itsaky.androidide.ui.ContentTranslatingDrawerLayout
import com.itsaky.androidide.ui.SwipeRevealLayout
import com.itsaky.androidide.utils.ActionMenuUtils.createMenu
import com.itsaky.androidide.utils.ApkInstallationSessionCallback
import com.itsaky.androidide.utils.DialogUtils.newMaterialDialogBuilder
import com.itsaky.androidide.utils.DialogUtils.newYesNoDialog
import com.itsaky.androidide.utils.InstallationResultHandler.onResult
import com.itsaky.androidide.utils.IntentUtils
import com.itsaky.androidide.utils.MemoryUsageWatcher
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.resolveAttr
import com.itsaky.androidide.viewmodel.EditorViewModel
import com.itsaky.androidide.xml.resources.ResourceTableRegistry
import com.itsaky.androidide.xml.versions.ApiVersionsRegistry
import com.itsaky.androidide.xml.widgets.WidgetTableRegistry
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Base class for EditorActivity which handles most of the view related things.
 * Refactored to coordinate smoothly with Unified Floating Bottom Bar Architecture.
 *
 * @author Akash Yadav
 * @author android_zero (Refactored)
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class BaseEditorActivity :
    EdgeToEdgeIDEActivity(), TabLayout.OnTabSelectedListener, DiagnosticClickListener {

  protected val mLifecycleObserver = EditorActivityLifecyclerObserver()
  protected var diagnosticInfoBinding: LayoutDiagnosticInfoBinding? = null
  protected var filesTreeFragment: FileTreeFragment? = null
  // 改为每次都从 view 实时读取, 不要缓存. EditorBottomSheet 在 onFinishInflate
  // 时会把默认的 BottomSheetBehavior 替换为 SymbolInputAwareBottomSheetBehavior;
  // 缓存字段会拿不到这个新 Behavior 的 state/expandedOffset, 后续基类与派生类
  // (例如 EditorHandlerActivity 通过 mBuildEventListener.setActivity(this) 在
  // build 完成时读这个状态) 都会读旧 Behavior 的 state, 引发"状态不同步"问题.
  // 改成"懒属性 + 实时从 view 读"是唯一稳的写法.
  protected val editorBottomSheet: BottomSheetBehavior<out View?>?
    get() = _binding?.let { BottomSheetBehavior.from(it.content.bottomSheet) }
  protected val memoryUsageWatcher = MemoryUsageWatcher()
  protected val pidToDatasetIdxMap = MutableIntIntMap(initialCapacity = 3)
  
  private val bottomSheetHeaderHideReasons = mutableSetOf<String>()

  var isDestroying = false
    protected set

  protected val editorActivityScope = CoroutineScope(Dispatchers.Default)

  internal var installationCallback: ApkInstallationSessionCallback? = null

  var uiDesignerResultLauncher: ActivityResultLauncher<Intent>? = null
  val editorViewModel by viewModels<EditorViewModel>()

  internal var _binding: ActivityEditorBinding? = null
  val binding: ActivityEditorBinding
    get() = checkNotNull(_binding) { "Activity has been destroyed" }

  val content: ContentEditorBinding
    get() = binding.content

  override val subscribeToEvents: Boolean
    get() = true

  private val onBackPressedCallback: OnBackPressedCallback =
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (isDestroying || isFinishing) return // Protect
          if (_binding == null) return

          if (binding.editorDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.editorDrawerLayout.closeDrawer(GravityCompat.START)
          } else if (editorBottomSheet?.state == BottomSheetBehavior.STATE_EXPANDED ||
              editorBottomSheet?.state == BottomSheetBehavior.STATE_HALF_EXPANDED ||
              editorBottomSheet?.state == BottomSheetBehavior.STATE_DRAGGING) {
            // IDE 抽屉在展开/半展开/正在拖拽任一状态时, 返回键都先关闭抽屉,
            // 而不是直接退出项目. STATE_DRAGGING 也要兜底: 用户在拖拽过程中按返回,
            // 也视为"抽屉处于打开状态", 关闭抽屉.
            content.bottomSheet.forceCollapse()
          } else if (binding.swipeReveal.isOpen) {
            binding.swipeReveal.close()
          } else {
            doConfirmProjectClose()
          }
        }
      }

  private val memoryUsageListener = MemoryUsageWatcher.MemoryUsageListener { memoryUsage ->
    if (isDestroying || _binding == null) return@MemoryUsageListener
    memoryUsage.forEachValue { proc ->
      _binding?.memUsageView?.chart?.apply {
        val dataset =
            (data.getDataSetByIndex(pidToDatasetIdxMap[proc.pid]) as LineDataSet?)
                ?: run {
                  log.error("No dataset found for process: {}: {}", proc.pid, proc.pname)
                  return@forEachValue
                }

        dataset.entries.forEachIndexed { index, entry ->
          entry.y = byte2MemorySize(proc.usageHistory[index], MemoryConstants.MB).toFloat()
        }

        dataset.label = "%s - %.2fMB".format(proc.pname, dataset.entries.last().y)
        dataset.notifyDataSetChanged()
        data.notifyDataChanged()
        notifyDataSetChanged()
        invalidate()
      }
    }
  }

  private var isImeVisible = false
  private val editorSurfaceContainerBackground by lazy { resolveAttr(R.attr.colorSurfaceDim) }

  private var optionsMenuInvalidator: Runnable? = null
  private var cursorPositionReceipt: SubscriptionReceipt<SelectionChangeEvent>? = null

  companion object {
    @JvmStatic protected val PROC_IDE = "IDE"
    @JvmStatic protected val PROC_GRADLE_TOOLING = "Gradle Tooling"
    @JvmStatic protected val PROC_GRADLE_DAEMON = "Gradle Daemon"
    @JvmStatic protected val log: Logger = LoggerFactory.getLogger(BaseEditorActivity::class.java)

    protected const val EDITOR_CONTAINER_INDEX = 0
    protected const val FRAGMENT_CONTAINER_INDEX = 1
    protected const val NO_EDITOR_CONTAINER_INDEX = 2

    private const val OPTIONS_MENU_INVALIDATION_DELAY = 150L
    const val EDITOR_CONTAINER_SCALE_FACTOR = 0.87f
    const val KEY_BOTTOM_SHEET_SHOWN = "editor_bottomSheetShown"
    const val KEY_PROJECT_PATH = "saved_projectPath"
  }

  protected abstract fun provideCurrentEditor(): CodeEditorView?
  protected abstract fun provideEditorAt(index: Int): CodeEditorView?
  protected abstract fun doOpenFile(file: File, selection: Range?)
  protected abstract fun doDismissSearchProgress()
  protected abstract fun getOpenedFiles(): List<OpenedFile>
  internal abstract fun doConfirmProjectClose()

  protected open fun preDestroy() {
    _binding = null
    optionsMenuInvalidator?.also { ThreadUtils.getMainHandler().removeCallbacks(it) }
    optionsMenuInvalidator = null
    installationCallback?.destroy()
    installationCallback = null
    if (isDestroying) {
      memoryUsageWatcher.stopWatching(true)
      memoryUsageWatcher.listener = null
      editorActivityScope.cancelIfActive("Activity is being destroyed")
    }
  }

  protected open fun postDestroy() {
    if (isDestroying) {
      Lookup.getDefault().unregisterAll()
      ApiVersionsRegistry.getInstance().clear()
      ResourceTableRegistry.getInstance().clear()
      WidgetTableRegistry.getInstance().clear()
    }
  }

  override fun bindLayout(): View {
    this._binding = ActivityEditorBinding.inflate(layoutInflater)
    this.diagnosticInfoBinding = this.content.diagnosticInfo
    return this.binding.root
  }

  override fun onApplyWindowInsets(insets: WindowInsetsCompat) {
    super.onApplyWindowInsets(insets)
    if (_binding == null) return

    val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
    val navInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    val isImeVisibleNow = insets.isVisible(WindowInsetsCompat.Type.ime())

    // 让 adjustResize 接管 IME 调整. 之前的做法是手动给 contentCard 设置
    // bottomMargin = imeBottom, 这与 adjustResize 双重 resize, 导致
    // EditorBottomSheet 的 parentHeight 被错误地减去两倍 imeBottom,
    // 符号输入控件就偏离了 IME 顶部 imeBottom 像素.
    //
    // 现在改成: 只把 insets 转发给 EditorBottomSheet, 让它内部的 peekHeight
    // 计算 + adjustResize 联合完成"软键盘顶部同步跟随".
    val bottomInset = if (isImeVisibleNow) imeInsets.bottom else navInsets.bottom
    _binding?.content?.bottomSheet?.applyEditorWindowInsets(insets)
    // bottomInset 仅供外部可能的扩展使用 (比如全屏手势/侧滑抽屉), 此处不再手动写
    // margin, 避免重复 resize. 如果将来需要, 应改用 WindowInsetsCompat.Type.ime()
    // 在 ViewCompat.setOnApplyWindowInsetsListener 里分发给具体子 view, 而不是
    // 改 contentCard 的 layout_margin.
    @Suppress("UNUSED_VARIABLE")
    val unused = bottomInset

    if (this.isImeVisible != isImeVisibleNow) {
      this.isImeVisible = isImeVisibleNow
      onSoftInputChanged()
      invalidateOptionsMenu()
    }
  }

  override fun onApplySystemBarInsets(insets: Insets) {
    super.onApplySystemBarInsets(insets)
    if (_binding == null) return
    this._binding?.apply {
      drawerSidebar.getFragment<EditorSidebarFragment>().onApplyWindowInsets(insets)

      content.apply {
        editorAppBarLayout.updatePadding(top = insets.top)
        editorToolbar.updatePaddingRelative(
            start = editorToolbar.paddingStart + insets.left,
            end = editorToolbar.paddingEnd + insets.right,
        )
      }
    }
  }

  protected fun releaseBottomSheetHeaderHide(reason: String) {
    runOnUiThread {
      if (_binding == null || isDestroying) return@runOnUiThread
      val wasRemoved = bottomSheetHeaderHideReasons.remove(reason)
      if (!wasRemoved || bottomSheetHeaderHideReasons.isNotEmpty()) return@runOnUiThread
      content.bottomSheet.binding.floatingHeaderArea.visibility = View.VISIBLE
    }
  }

  protected fun requestBottomSheetHeaderHide(reason: String) {
    runOnUiThread {
      if (_binding == null || isDestroying) return@runOnUiThread
      val wasAdded = bottomSheetHeaderHideReasons.add(reason)
      if (!wasAdded || bottomSheetHeaderHideReasons.size > 1) return@runOnUiThread
      content.bottomSheet.binding.floatingHeaderArea.visibility = View.INVISIBLE
    }
  }

  @Subscribe(threadMode = MAIN)
  open fun onInstallationResult(event: InstallationResultEvent) {
    val intent = event.intent
    if (isDestroying || _binding == null) return

    val packageName = onResult(this, intent) ?: return

    if (BuildPreferences.launchAppAfterInstall) {
      IntentUtils.launchApp(this, packageName)
      return
    }

    Snackbar.make(content.realContainer, string.msg_action_open_application, Snackbar.LENGTH_LONG)
        .setAction(string.yes) { IntentUtils.launchApp(this, packageName) }
        .show()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    this.optionsMenuInvalidator = Runnable {
      if (!isDestroying && !isFinishing) super.invalidateOptionsMenu()
    }

    registerLanguageServers(this)

    if (savedInstanceState != null && savedInstanceState.containsKey(KEY_PROJECT_PATH)) {
      IProjectManager.getInstance().openProject(savedInstanceState.getString(KEY_PROJECT_PATH)!!)
    }

    onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    lifecycle.addObserver(mLifecycleObserver)

    if (_binding != null) {
      setSupportActionBar(content.editorToolbar)
      supportActionBar?.setDisplayShowTitleEnabled(false)
      setupDrawers()
      content.tabs.addOnTabSelectedListener(this)
      setupViews()
      setupContainers()
      setupDiagnosticInfo()
      setupMemUsageChart()
      watchMemory()
    }
  }

  private fun onSwipeRevealDragProgress(progress: Float) {
    if (_binding == null) return
    _binding?.apply {
      val insetsTop = systemBarInsets?.top ?: 0
      content.editorAppBarLayout.updatePadding(top = (insetsTop * (1f - progress)).roundToInt())
      memUsageView.chart.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = (insetsTop * progress).roundToInt()
      }
    }
  }

  private fun setupMemUsageChart() {
    binding.memUsageView.chart.apply {
      val colorAccent = resolveAttr(R.attr.colorAccent)
      isDragEnabled = false
      description.isEnabled = false
      xAxis.axisLineColor = colorAccent
      axisRight.axisLineColor = colorAccent
      setPinchZoom(false)
      setBackgroundColor(editorSurfaceContainerBackground)
      setDrawGridBackground(true)
      setScaleEnabled(true)
      axisLeft.isEnabled = false
      axisRight.valueFormatter =
          object : IAxisValueFormatter {
            override fun getFormattedValue(value: Float, axis: AxisBase?): String {
              return "%dMB".format(value.roundToLong())
            }
          }
    }
  }

  private fun watchMemory() {
    memoryUsageWatcher.listener = memoryUsageListener
    memoryUsageWatcher.watchProcess(Process.myPid(), PROC_IDE)
    resetMemUsageChart()
  }

  protected fun resetMemUsageChart() {
    if (_binding == null) return
    val processes = memoryUsageWatcher.getMemoryUsages()
    val datasets =
        Array(processes.size) { index ->
          LineDataSet(
              List(MemoryUsageWatcher.MAX_USAGE_ENTRIES) { Entry(it.toFloat(), 0f) },
              processes[index].pname,
          )
        }

    val bgColor = editorSurfaceContainerBackground
    val textColor = resolveAttr(R.attr.colorOnSurface)

    for ((index, proc) in processes.withIndex()) {
      val dataset = datasets[index]
      dataset.color = getMemUsageLineColorFor(proc)
      dataset.setDrawIcons(false)
      dataset.setDrawCircles(false)
      dataset.setDrawCircleHole(false)
      dataset.setDrawValues(false)
      dataset.formLineWidth = 1f
      dataset.formSize = 15f
      dataset.isHighlightEnabled = false
      pidToDatasetIdxMap[proc.pid] = index
    }

    binding.memUsageView.chart.setBackgroundColor(bgColor)

    binding.memUsageView.chart.apply {
      data = LineData(*datasets)
      axisRight.textColor = textColor
      axisLeft.textColor = textColor
      legend.textColor = textColor

      data.setValueTextColor(textColor)
      setBackgroundColor(bgColor)
      setGridBackgroundColor(bgColor)
      notifyDataSetChanged()
      invalidate()
    }
  }

  private fun getMemUsageLineColorFor(proc: MemoryUsageWatcher.ProcessMemoryInfo): Int {
    return when (proc.pname) {
      PROC_IDE -> Color.BLUE
      PROC_GRADLE_TOOLING -> Color.RED
      PROC_GRADLE_DAEMON -> Color.GREEN
      else -> throw IllegalArgumentException("Unknown process: $proc")
    }
  }

  override fun onPause() {
    super.onPause()
    memoryUsageWatcher.listener = null
    memoryUsageWatcher.stopWatching(false)

    this.isDestroying = isFinishing
    getFileTreeFragment()?.saveTreeState()
  }

  override fun onResume() {
    super.onResume()
    invalidateOptionsMenu()

    memoryUsageWatcher.listener = memoryUsageListener
    memoryUsageWatcher.startWatching()

    try {
      getFileTreeFragment()?.listProjectFiles()
    } catch (th: Throwable) {
      log.error("Failed to update files list", th)
      flashError(string.msg_failed_list_files)
    }
  }

  override fun onStop() {
    super.onStop()
    checkIsDestroying()
  }

  override fun onDestroy() {
    checkIsDestroying()
    preDestroy()
    cursorPositionReceipt?.unsubscribe()
    cursorPositionReceipt = null
    super.onDestroy()
    postDestroy()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    // 治本：projectDirPath 改 nullable 后，用 ?: "" 兜底（Kotlin 编译器 Bundle.putString 也接受 nullable，
    // 但保持显式空字符串语义对历史行为更友好）
    outState.putString(KEY_PROJECT_PATH, IProjectManager.getInstance().projectDirPath ?: "")
    super.onSaveInstanceState(outState)
  }

  override fun invalidateOptionsMenu() {
    if (isDestroying || isFinishing) return
    val mainHandler = ThreadUtils.getMainHandler()
    optionsMenuInvalidator?.also {
      mainHandler.removeCallbacks(it)
      mainHandler.postDelayed(it, OPTIONS_MENU_INVALIDATION_DELAY)
    }
  }

  override fun onTabSelected(tab: Tab) {
    if (isDestroying || _binding == null) return
    val position = resolveEditorIndexForTab(tab)
    if (position < 0) return
    editorViewModel.displayedFileIndex = position

    val editorView = provideEditorAt(position) ?: return
    EditorLineOperations.applyReadOnlyState(editorView.editor!!, this)

    editorView.onEditorSelected()
    bindCursorPositionSync(editorView)
    updateCursorPositionIndicator(editorView)

    editorViewModel.setCurrentFile(position, editorView.file)
    refreshSymbolInput(editorView)
    invalidateOptionsMenu()
  }

  protected open fun resolveEditorIndexForTab(tab: Tab): Int = tab.position

  protected open fun hasNonEditorTabs(): Boolean = false

  override fun onTabUnselected(tab: Tab) {}

  override fun onTabReselected(tab: Tab) {
    if (isDestroying || _binding == null) return
    createMenu(this, tab.view, EDITOR_FILE_TABS, true).show()
  }

  override fun onGroupClick(group: DiagnosticGroup?) {
    if (isDestroying || _binding == null) return
    if (group?.file?.exists() == true && FileUtils.isUtf8(group.file)) {
      doOpenFile(group.file, null)
      hideBottomSheet()
    }
  }

  override fun onDiagnosticClick(file: File, diagnostic: DiagnosticItem) {
    if (isDestroying || _binding == null) return
    doOpenFile(file, diagnostic.range)
    hideBottomSheet()
  }

  open fun handleSearchResults(map: Map<File, List<SearchResult>>?) {
    if (isDestroying || _binding == null) return
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

  open fun setSearchResultAdapter(adapter: SearchListAdapter) {
    if (isDestroying || _binding == null) return
    content.bottomSheet.setSearchResultAdapter(adapter)
  }

  open fun setDiagnosticsAdapter(adapter: DiagnosticsAdapter) {
    if (isDestroying || _binding == null) return
    content.bottomSheet.setDiagnosticsAdapter(adapter)
  }

  open fun hideBottomSheet() {
    if (isDestroying || _binding == null) return
    content.bottomSheet.forceCollapse()
  }

  open fun showSearchResults() {
    if (isDestroying || _binding == null) return
    content.bottomSheet.tryExpandSheetFromControl()

    val index =
        content.bottomSheet.pagerAdapter.findIndexOfFragmentByClass(
            SearchResultFragment::class.java
        )

    if (index >= 0 && index < content.bottomSheet.binding.tabs.tabCount) {
      content.bottomSheet.binding.tabs.getTabAt(index)?.select()
    }
  }

  open fun handleDiagnosticsResultVisibility(errorVisible: Boolean) {
    if (isDestroying || _binding == null) return
    content.bottomSheet.handleDiagnosticsResultVisibility(errorVisible)
  }

  open fun handleSearchResultVisibility(errorVisible: Boolean) {
    if (isDestroying || _binding == null) return
    content.bottomSheet.handleSearchResultVisibility(errorVisible)
  }

  open fun showFirstBuildNotice() {
    if (isDestroying || isFinishing) return
    newMaterialDialogBuilder(this)
        .setPositiveButton(android.R.string.ok, null)
        .setTitle(string.title_first_build)
        .setMessage(string.msg_first_build)
        .setCancelable(false)
        .create()
        .show()
  }

  open fun getFileTreeFragment(): FileTreeFragment? {
    if (isDestroying) return null
    if (filesTreeFragment == null) {
      filesTreeFragment =
          supportFragmentManager.findFragmentByTag(FileTreeFragment.TAG) as FileTreeFragment?
    }
    return filesTreeFragment
  }

  fun doSetStatus(text: CharSequence, @GravityInt gravity: Int) {
    if (isDestroying || _binding == null) return
    editorViewModel.statusText = text
    editorViewModel.statusGravity = gravity
  }

  fun refreshSymbolInput() {
    if (isDestroying || _binding == null) return
    provideCurrentEditor()?.also { refreshSymbolInput(it) }
  }

  fun refreshSymbolInput(editor: CodeEditorView) {
    if (isDestroying || _binding == null) return
    val codeEditor = editor.editor ?: return
    // 直接指向底栏内部绑定的符号工具栏
    content.bottomSheet.binding.externalSymbolInputView.bindEditor(codeEditor)
    content.bottomSheet.binding.externalSymbolInputView.onOpenManagerListener = {
      startActivity(Intent(this, SymbolManagerActivity::class.java))
    }
  }

  private fun checkIsDestroying() {
    if (!isDestroying && isFinishing) {
      isDestroying = true
    }
  }

  private fun setupDrawers() {
    if (_binding == null) return
    val toggle =
        ActionBarDrawerToggle(
            this,
            binding.editorDrawerLayout,
            content.editorToolbar,
            string.app_name,
            string.app_name,
        )

    binding.editorDrawerLayout.addDrawerListener(toggle)
    toggle.syncState()
    binding.apply {
      editorDrawerLayout.apply {
        childId = contentCard.id
        translationBehaviorStart = ContentTranslatingDrawerLayout.TranslationBehavior.FULL
        translationBehaviorEnd = ContentTranslatingDrawerLayout.TranslationBehavior.FULL
        setScrimColor(Color.TRANSPARENT)
      }
    }
  }

  private fun onBuildStatusChanged() {
    if (isDestroying || _binding == null) return
    val visible = editorViewModel.isBuildInProgress || editorViewModel.isInitializing
    content.progressIndicator.visibility = if (visible) View.VISIBLE else View.GONE
    invalidateOptionsMenu()
  }

  private fun setupViews() {
    editorViewModel._isBuildInProgress.observe(this) { onBuildStatusChanged() }
    editorViewModel._isInitializing.observe(this) { onBuildStatusChanged() }
    editorViewModel._statusText.observe(this) {
      if (!isDestroying && _binding != null) {
        content.bottomSheet.setStatus(it.first, it.second)
      }
    }

    editorViewModel.observeFiles(this) { files ->
      if (isDestroying || _binding == null) return@observeFiles
      content.apply {
        if (files.isNullOrEmpty() && !hasNonEditorTabs()) {
          tabs.visibility = View.GONE
          viewContainer.displayedChild = NO_EDITOR_CONTAINER_INDEX
        } else {
          tabs.visibility = View.VISIBLE
          viewContainer.displayedChild = EDITOR_CONTAINER_INDEX
        }
      }
      invalidateOptionsMenu()
    }

    setupNoEditorView()
    setupBottomSheet()

    if (
        !app.prefManager.getBoolean(KEY_BOTTOM_SHEET_SHOWN)
    ) {
      content.bottomSheet.tryExpandSheetFromControl()
      ThreadUtils.runOnUiThreadDelayed(
          {
            if (!isDestroying) {
              content.bottomSheet.forceCollapse()
              app.prefManager.putBoolean(KEY_BOTTOM_SHEET_SHOWN, true)
            }
          },
          1500,
      )
    }

    binding.swipeReveal.dragListener =
        object : SwipeRevealLayout.OnDragListener {
          override fun onDragStateChanged(swipeRevealLayout: SwipeRevealLayout, state: Int) {}
          override fun onDragProgress(swipeRevealLayout: SwipeRevealLayout, progress: Float) {
            onSwipeRevealDragProgress(progress)
          }
        }
  }

  private fun setupNoEditorView() {
    if (_binding == null) return
    content.noEditorSummary.movementMethod = LinkMovementMethod()
    val filesSpan: ClickableSpan =
        object : ClickableSpan() {
          override fun onClick(widget: View) {
            if (!isDestroying && _binding != null) binding.editorDrawerLayout.openDrawer(GravityCompat.START)
          }
        }
    val bottomSheetSpan: ClickableSpan =
        object : ClickableSpan() {
          override fun onClick(widget: View) {
            if (!isDestroying && _binding != null)
                content.bottomSheet.tryExpandSheetFromControl()
          }
        }
    val sb = SpannableStringBuilder()
    appendClickableSpan(sb, string.msg_drawer_for_files, filesSpan)
    appendClickableSpan(sb, string.msg_swipe_for_output, bottomSheetSpan)
    content.noEditorSummary.text = sb
  }

  private fun appendClickableSpan(
      sb: SpannableStringBuilder,
      @StringRes textRes: Int,
      span: ClickableSpan,
  ) {
    val str = getString(textRes)
    val split = str.split("@@", limit = 3)
    if (split.size != 3) {
      sb.append(str)
      sb.append('\n')
      return
    }
    sb.append(split[0])
    sb.append(split[1], span, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.append(split[2])
    sb.append('\n')
  }

  private fun setupBottomSheet() {
    if (_binding == null) return
    // editorBottomSheet 已经是实时从 view 读取的 getter, 这里不需要再赋值.
    // 直接通过 BottomSheetBehavior.from() 拿本地的 behavior 引用用于下面
    // 设置 expandedOffset 等配置, 拿到的就是 SymbolInputAware 版本
    // (EditorBottomSheet.onFinishInflate 已经替换过 layoutParams.behavior).
    val behavior = BottomSheetBehavior.from<View>(content.bottomSheet)

    val applyExpandedOffset = {
      val progressBottom = content.progressIndicator.bottom
      if (progressBottom > 0) {
        behavior.expandedOffset = progressBottom
      }
    }
    content.progressIndicator.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      applyExpandedOffset()
    }
    content.editorAppBarLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      applyExpandedOffset()
    }
    content.realContainer.post { applyExpandedOffset() }

    content.bottomSheet.onSlideAction = { slideOffset ->
      if (!isDestroying && _binding != null) {
          val editorScale = 1 - slideOffset * (1 - EDITOR_CONTAINER_SCALE_FACTOR)
          content.viewContainer.scaleX = editorScale
          content.viewContainer.scaleY = editorScale
      }
    }

    // 把 CoordinatorLayout 上的日志分享/清空 FAB 注入到 EditorBottomSheet.
    // 这两个 FAB 之前在 layout_editor_bottom_sheet.xml 里 (放在 drawer_content_area
    // 内部), 抽屉在 50% 停靠或半展开时按钮会落到布局可见区域之外. 现在改成
    // CoordinatorLayout 的直接子节点 + app:layout_anchor, 始终跟随 BottomSheet 右下角,
    // 但仍然由 EditorBottomSheet 根据当前选中的 Tab 自行 show/hide.
    // binding 是 ActivityEditorBinding (从 activity_editor.xml 生成), FAB 实际定义在
    // content_editor.xml 里, 所以走 content.clearFab / content.shareOutputFab.
    content.bottomSheet.setLogActionFabButtons(content.clearFab, content.shareOutputFab)
  }

  private fun setupDiagnosticInfo() {
    if (diagnosticInfoBinding == null) return
    val gd = GradientDrawable()
    gd.shape = GradientDrawable.RECTANGLE
    gd.setColor(-0xdededf)
    gd.setStroke(1, -0x1)
    gd.cornerRadius = 8f
    diagnosticInfoBinding?.root?.background = gd
    diagnosticInfoBinding?.root?.visibility = View.GONE
  }

  private fun setupContainers() {
    if (_binding == null) return
    handleDiagnosticsResultVisibility(true)
    handleSearchResultVisibility(true)
  }

  private fun onSoftInputChanged() {
    if (!isDestroying) {
      invalidateOptionsMenu()
      // BottomSheet 自身由于动态 padding 会自行适配 IME 响应
    }
  }

  open fun installationSessionCallback(): SessionCallback {
    return ApkInstallationSessionCallback(this).also { installationCallback = it }
  }

  private fun bindCursorPositionSync(editorView: CodeEditorView) {
    cursorPositionReceipt?.unsubscribe()
    val soraEditor = editorView.editor ?: return
    cursorPositionReceipt =
        soraEditor.subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
          if (_binding == null || isDestroying) return@subscribeEvent
          updateCursorPositionIndicator(editorView)
        }
  }

  private fun updateCursorPositionIndicator(editorView: CodeEditorView) {
    val cursor = editorView.editor?.cursor ?: return
    val line = cursor.leftLine + 1
    val column = cursor.leftColumn + 1
    content.bottomSheet.binding.tvCursorPosition.text = "$line:$column"
  }
}