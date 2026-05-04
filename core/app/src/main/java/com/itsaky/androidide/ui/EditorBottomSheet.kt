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

package com.itsaky.androidide.ui

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.RelativeLayout
import androidx.annotation.GravityInt
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.transition.TransitionManager
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.ThreadUtils.runOnUiThread
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.transition.MaterialSharedAxis
import com.itsaky.androidide.R
import com.itsaky.androidide.adapters.DiagnosticsAdapter
import com.itsaky.androidide.adapters.EditorBottomSheetTabAdapter
import com.itsaky.androidide.adapters.SearchListAdapter
import com.itsaky.androidide.databinding.LayoutEditorBottomSheetBinding
import com.itsaky.androidide.fragments.output.ShareableOutputFragment
import com.itsaky.androidide.models.LogLine
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.tasks.TaskExecutor.CallbackWithError
import com.itsaky.androidide.tasks.TaskExecutor.executeAsync
import com.itsaky.androidide.tasks.TaskExecutor.executeAsyncProvideError
import com.itsaky.androidide.utils.IntentUtils.shareFile
import com.itsaky.androidide.utils.flashError
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.Callable
import org.slf4j.LoggerFactory

class EditorBottomSheet
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr, defStyleRes) {

  val behavior: BottomSheetBehavior<EditorBottomSheet> by lazy {
    BottomSheetBehavior.from(this).apply {
      isFitToContents = false
      skipCollapsed = true
      state = BottomSheetBehavior.STATE_HIDDEN
    }
  }

  @JvmField var binding: LayoutEditorBottomSheetBinding
  val pagerAdapter: EditorBottomSheetTabAdapter

  private var isImeVisible = false
  private var windowInsets: Insets? = null
  private var suppressNextHeaderClickExpand = false
  private var headerExpandEnabled = true
  private var expandBlocked = false
  private var behaviorCallbackAttached = false
  
  private var symbolInputPage: View? = null
  var isExternalSymbolMode = false

  var onHeaderPageChanged: ((Int) -> Unit)? = null
  var onActionTextChanged: ((CharSequence) -> Unit)? = null
  var onActionProgressChanged: ((Int) -> Unit)? = null
  var onStatusChanged: ((CharSequence, Int) -> Unit)? = null

  companion object {
    private val log = LoggerFactory.getLogger(EditorBottomSheet::class.java)
    const val CHILD_HEADER = 0
    const val CHILD_ACTION = 1
    const val STATE_EXTERNAL_SYMBOL = -1
  }

  private fun initialize(context: FragmentActivity) {

    val mediator =
        TabLayoutMediator(binding.tabs, binding.pager, true, true) { tab, position ->
          tab.text = pagerAdapter.getTitle(position)
        }

    mediator.attach()
    binding.pager.isUserInputEnabled = false
    binding.pager.offscreenPageLimit = pagerAdapter.itemCount - 1

    binding.tabs.addOnTabSelectedListener(
        object : OnTabSelectedListener {
          override fun onTabSelected(tab: Tab) {
            val fragment: Fragment = pagerAdapter.getFragmentAtIndex(tab.position)
            if (fragment is ShareableOutputFragment) {
              binding.clearFab.show()
              binding.shareOutputFab.show()
            } else {
              binding.clearFab.hide()
              binding.shareOutputFab.hide()
            }
          }
          override fun onTabUnselected(tab: Tab) {}
          override fun onTabReselected(tab: Tab) {}
        }
    )

    binding.shareOutputFab.setOnClickListener {
      val fragment = pagerAdapter.getFragmentAtIndex(binding.tabs.selectedTabPosition)
      if (fragment !is ShareableOutputFragment) return@setOnClickListener
      val filename = fragment.getFilename()

      @Suppress("DEPRECATION")
      val progress =
          android.app.ProgressDialog.show(context, null, context.getString(string.please_wait))
      executeAsync(fragment::getContent) {
        progress.dismiss()
        shareText(it, filename)
      }
    }

    TooltipCompat.setTooltipText(binding.clearFab, context.getString(string.title_clear_output))
    binding.clearFab.setOnClickListener {
      val fragment: Fragment = pagerAdapter.getFragmentAtIndex(binding.tabs.selectedTabPosition)
      if (fragment is ShareableOutputFragment) {
        fragment.clearOutput()
      }
    }

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
      this.windowInsets = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
      insets
    }
  }

  init {
    if (context !is FragmentActivity) throw IllegalArgumentException("Requires FragmentActivity")
    val inflater = LayoutInflater.from(context)
    binding = LayoutEditorBottomSheetBinding.inflate(inflater)
    pagerAdapter = EditorBottomSheetTabAdapter(context)
    binding.pager.adapter = pagerAdapter

    removeAllViews()
    addView(binding.root)
    initialize(context)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    ensureBehaviorCallbackAttached()
  }

  private fun ensureBehaviorCallbackAttached() {
    if (behaviorCallbackAttached) return
    behavior.addBottomSheetCallback(
        object : BottomSheetBehavior.BottomSheetCallback() {
          override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (!canExpandSheet() && newState == BottomSheetBehavior.STATE_EXPANDED) {
              forceCollapse()
            }
          }

          override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
        }
    )
    behaviorCallbackAttached = true
  }

  fun setImeVisible(isVisible: Boolean) {
    isImeVisible = isVisible
    behavior.isGestureInsetBottomIgnored = isVisible
  }

  fun setOffsetAnchor(appBarLayout: View, symbolInputPage: View) {
    this.symbolInputPage = symbolInputPage
    val listener =
        object : ViewTreeObserver.OnGlobalLayoutListener {
          override fun onGlobalLayout() {
            appBarLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
            behavior.expandedOffset = appBarLayout.height
            behavior.isGestureInsetBottomIgnored = isImeVisible
          }
        }
    appBarLayout.viewTreeObserver.addOnGlobalLayoutListener(listener)
  }

  // 修复核心：监听 BottomSheet 滑动并计算物理偏移，完美向上推起 symbolInputPage
  fun onSlide(sheetOffset: Float) {
    if (isExternalSymbolMode) return
    val parentView = parent as? View ?: return
    val visibleHeight = parentView.height - top
    
    // 强制将整个输入控制页向上平移，保持紧贴 BottomSheet 的顶部
    symbolInputPage?.translationY = -visibleHeight.toFloat()
  }

  fun showChild(index: Int) {
    onHeaderPageChanged?.invoke(if (index == CHILD_ACTION) CHILD_ACTION else CHILD_HEADER)
  }

  fun suppressNextHeaderExpand() {
    suppressNextHeaderClickExpand = true
  }

  fun setBottomSheetDragEnabled(enabled: Boolean) {
    behavior.isDraggable = enabled
  }

  fun setExpandBlocked(blocked: Boolean) = setExpandAllowed(!blocked)

  fun setExpandAllowed(allowed: Boolean) {
    expandBlocked = !allowed
    behavior.isDraggable = allowed
    if (!allowed) {
      suppressNextHeaderExpand()
      forceCollapse()
    }
  }

  fun canExpandSheet(): Boolean = !expandBlocked && headerExpandEnabled

  fun tryExpandSheetFromControl(): Boolean {
    if (!canExpandSheet()) return false
    behavior.state = BottomSheetBehavior.STATE_EXPANDED
    return true
  }

  fun forceCollapse() {
    if (behavior.state != BottomSheetBehavior.STATE_HIDDEN) {
      behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }
  }

  fun suspendHeaderExpandFor(durationMs: Long) {
    headerExpandEnabled = false
    binding.root.removeCallbacks(resumeHeaderExpandRunnable)
    binding.root.postDelayed(resumeHeaderExpandRunnable, durationMs)
  }

  private val resumeHeaderExpandRunnable = Runnable { headerExpandEnabled = true }

  fun setActionText(text: CharSequence) { onActionTextChanged?.invoke(text) }
  fun setActionProgress(progress: Int) { onActionProgressChanged?.invoke(progress) }
  fun appendApkLog(line: LogLine) { pagerAdapter.logFragment?.appendLog(line) }
  fun appendBuildOut(str: String?) { pagerAdapter.buildOutputFragment?.appendOutput(str) }
  fun clearBuildOutput() { pagerAdapter.buildOutputFragment?.clearOutput() }
  fun handleDiagnosticsResultVisibility(errorVisible: Boolean) { runOnUiThread { pagerAdapter.diagnosticsFragment?.isEmpty = errorVisible } }
  fun handleSearchResultVisibility(errorVisible: Boolean) { runOnUiThread { pagerAdapter.searchResultFragment?.isEmpty = errorVisible } }
  fun setDiagnosticsAdapter(adapter: DiagnosticsAdapter) { runOnUiThread { pagerAdapter.diagnosticsFragment?.setAdapter(adapter) } }
  fun setSearchResultAdapter(adapter: SearchListAdapter) { runOnUiThread { pagerAdapter.searchResultFragment?.setAdapter(adapter) } }
  fun refreshSymbolInput(@Suppress("UNUSED_PARAMETER") editor: CodeEditorView) {}

  fun onSoftInputChanged() {
    if (context !is Activity) return
    TransitionManager.beginDelayedTransition(binding.root, MaterialSharedAxis(MaterialSharedAxis.Y, false))
    if (KeyboardUtils.isSoftInputVisible(context as Activity)) {
      onHeaderPageChanged?.invoke(STATE_EXTERNAL_SYMBOL)
    } else {
      onHeaderPageChanged?.invoke(CHILD_HEADER)
    }
  }

  fun setStatus(text: CharSequence, @GravityInt gravity: Int) {
    onStatusChanged?.invoke(text, gravity)
  }

  private fun shareFile(file: File) { shareFile(context, file, "text/plain") }

  @Suppress("DEPRECATION")
  private fun shareText(text: String?, type: String) {
    if (text == null || TextUtils.isEmpty(text)) return
    val pd = android.app.ProgressDialog.show(context, null, context.getString(string.please_wait), true, false)
    executeAsyncProvideError(Callable { writeTempFile(text, type) }, CallbackWithError<File> { result: File?, _: Throwable? ->
      pd.dismiss()
      if (result != null) shareFile(result)
    })
  }

  private fun writeTempFile(text: String, type: String): File {
    val file: Path = context.filesDir.toPath().resolve("$type.txt")
    try {
      if (Files.exists(file)) Files.delete(file)
      Files.write(file, text.toByteArray(StandardCharsets.UTF_8), CREATE_NEW, WRITE)
    } catch (e: IOException) {
      log.error("Unable to write output to file", e)
    }
    return file.toFile()
  }
}