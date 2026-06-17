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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.annotation.GravityInt
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.blankj.utilcode.util.ThreadUtils.runOnUiThread
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.tabs.TabLayoutMediator
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
import kotlin.math.max
import kotlin.math.min
import org.slf4j.LoggerFactory

/**
 * Bottom sheet shown in editor activity.
 * Refactored to coordinate smoothly with Unified Floating Bottom Bar Architecture.
 *
 * @author Akash Yadav
 * @author android_zero (Unified Architecture & Animations & IME Sync)
 */
class EditorBottomSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

  @JvmField var binding: LayoutEditorBottomSheetBinding
  val pagerAdapter: EditorBottomSheetTabAdapter

  // ==== IME 软键盘顶部同步相关字段 ====
  // 记录上一次 IME 可见性, 用于在 IME 状态切换时做 BottomSheet 状态联动.
  // 不使用更复杂的 diff 监听, 因为 WindowInsets API 在动画过程中会一帧一帧回调,
  // 单纯比较 boolean 即可精确捕获"打开/关闭"两个边界.
  private var wasImeVisible = false

  private val behavior: BottomSheetBehavior<EditorBottomSheet> by lazy {
    BottomSheetBehavior.from(this).apply {
      isFitToContents = false
      skipCollapsed = false
      isHideable = false
    }
  }

  private var suppressNextHeaderClickExpand = false
  private var headerExpandEnabled = true
  private var expandBlocked = false
  private var behaviorCallbackAttached = false
  private var wasDraggableBeforeSymbolTouch = true

  var onHeaderPageChanged: ((Int) -> Unit)? = null
  var onActionTextChanged: ((CharSequence) -> Unit)? = null
  var onActionProgressChanged: ((Int) -> Unit)? = null
  var onStatusChanged: ((CharSequence, Int) -> Unit)? = null
  var onSlideAction: ((Float) -> Unit)? = null

  // 软键盘底部安全区域补丁
  private var currentBottomInset = 0
  
  // Header 区域是否可见(供 3D 滑出动画使用)
  private var isHeaderVisible = true

  companion object {
    private val log = LoggerFactory.getLogger(EditorBottomSheet::class.java)

    const val CHILD_HEADER = 0
    const val CHILD_ACTION = 1
  }

  init {
    if (context !is FragmentActivity) {
      throw IllegalArgumentException("EditorBottomSheet must be set up with a FragmentActivity")
    }

    orientation = VERTICAL
    val inflater = LayoutInflater.from(context)
    binding = LayoutEditorBottomSheetBinding.inflate(inflater, this, true)
    
    pagerAdapter = EditorBottomSheetTabAdapter(context)
    binding.pager.adapter = pagerAdapter

    initialize(context)
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

      if (fragment !is ShareableOutputFragment) {
        log.error("Unknown fragment: {}", fragment)
        return@setOnClickListener
      }

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
      if (fragment !is ShareableOutputFragment) {
        log.error("Unknown fragment: {}", fragment)
        return@setOnClickListener
      }
      (fragment as ShareableOutputFragment).clearOutput()
    }

    // 解决气泡启动时错位：延时并在 Layout 完成后重置气泡形态
    binding.pageSwitchGestureBubble.viewTreeObserver.addOnGlobalLayoutListener(
        object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.pageSwitchGestureBubble.viewTreeObserver.removeOnGlobalLayoutListener(this)
                // 确保视图宽高校准后，才应用初始形态
                setupPageSwitchGestureBubble()
                binding.pageSwitchGestureBubble.invalidate()
            }
        }
    )

    // 兼容新版 AdvancedSymbolInputView：不再调用已移除/不稳定的 setImeBottomInset，
    // 改为在符号栏手势期间临时关闭 BottomSheet 拖拽，避免父级手势抢占导致抽屉无法展开。
    binding.externalSymbolInputView.setOnTouchListener { _, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          wasDraggableBeforeSymbolTouch = behavior.isDraggable
          behavior.isDraggable = false
          parent?.requestDisallowInterceptTouchEvent(true)
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          behavior.isDraggable = wasDraggableBeforeSymbolTouch
          parent?.requestDisallowInterceptTouchEvent(false)
        }
      }
      false
    }
  }

  /**
   * 处理软键盘 (IME) 同步和 PeekHeight 自动适配。
   * 让 CoordinatorLayout 与系统的 WindowInsets 联合接管所有位移交互。
   */
  private fun setupDynamicPeekHeightAndIME() {
    // 监听浮动头部的高度变动，更新 BottomSheet 的露头高度 (peekHeight)
    binding.floatingHeaderArea.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
        val newHeight = bottom - top
        if (newHeight > 0 && newHeight != (oldBottom - oldTop)) {
            updatePeekHeight()
        }
    }

    // 将 WindowInsets 拦截用于 IME 同步；BaseEditorActivity 也会直接转发一次，
    // 以覆盖 CoordinatorLayout/BottomSheetBehavior 未把 IME insets 分发到子 View 的设备。
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
      applyEditorWindowInsets(insets)
      insets
    }
    behavior.isGestureInsetBottomIgnored = true
  }

  fun applyEditorWindowInsets(insets: WindowInsetsCompat) {
    val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
    val navInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
    val isImeVisibleNow = insets.isVisible(WindowInsetsCompat.Type.ime())
    val targetBottomInset = if (isImeVisibleNow) imeInsets.bottom else navInsets.bottom

    // ==== IME 软键盘打开/关闭 与 BottomSheet 状态联动 ====
    // 设计目标: IME 弹起时, 底部符号输入控件 (AdvancedSymbolInputView) 的顶部
    // 自动同步到 IME 顶部, 同时四个层级 (bubble / build_status / header / symbol_input)
    // 一起跟着 IME 上下移动, 视觉绑定关系不破坏.
    //
    // 实现: IME 弹起时, 如果 BottomSheet 处于展开/半展开/拖拽中的"打开"状态, 先把
    // 它折叠到 STATE_COLLAPSED. 折叠后, floating_header_area 的底部 (即 symbol_input
    // 的底部) 就被 peekHeight 钉在屏幕底部, peekHeight 包含 IME 高度, 所以 symbol_input
    // 的底部正好落在 IME 顶部. IME 关闭时, 不自动恢复 BottomSheet 状态, 让用户自己
    // 决定要不要再展开抽屉 (避免频繁的自动展开/折叠打扰用户).
    //
    // 不在 onStateChanged 里做: 那个回调在 BottomSheet 自己的动画过程中也会触发, 会
    // 和 IME 状态变化抢着切状态. 在这里做是单点控制, 逻辑最清晰.
    if (isImeVisibleNow != wasImeVisible) {
      if (isImeVisibleNow) {
        if (behavior.state == BottomSheetBehavior.STATE_EXPANDED ||
            behavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED ||
            behavior.state == BottomSheetBehavior.STATE_DRAGGING) {
          behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
      }
      wasImeVisible = isImeVisibleNow
    }

    updateBottomInset(targetBottomInset)
  }

  private fun updateBottomInset(targetBottomInset: Int) {
    if (currentBottomInset == targetBottomInset) {
      return
    }

    currentBottomInset = targetBottomInset
    updatePadding(bottom = 0)
    binding.spaceBottom.updateLayoutParams<ViewGroup.LayoutParams> {
      height = currentBottomInset
    }

    // BottomSheet top is derived from peekHeight in collapsed mode, so include the IME inset to
    // keep floating_header_area/AdvancedSymbolInputView attached to the keyboard top. The drawer
    // content uses spaceBottom so fragments such as ChatAI keep their bottom input above IME too.
    updatePeekHeight()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val parentHeight = (parent as? View)?.height ?: 0
    val displayHeight = resources.displayMetrics.heightPixels
    val maxHeight = when {
      parentHeight > 0 -> parentHeight
      displayHeight > 0 -> displayHeight
      else -> MeasureSpec.getSize(heightMeasureSpec)
    }
    val heightMode = MeasureSpec.getMode(heightMeasureSpec)
    val heightSize = MeasureSpec.getSize(heightMeasureSpec)
    val cappedHeightSpec =
        if (maxHeight > 0 && (heightMode == MeasureSpec.UNSPECIFIED || heightSize > maxHeight)) {
          MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
        } else {
          heightMeasureSpec
        }
    super.onMeasure(widthMeasureSpec, cappedHeightSpec)
  }

  private fun updatePeekHeight() {
      // 只有在 Header 显示时（未被 3D 隐藏），PeekHeight 才包含它
      val headerHeight = binding.floatingHeaderArea.height
      val parentHeight = (parent as? View)?.height ?: resources.displayMetrics.heightPixels
      behavior.peekHeight = min(max(headerHeight, 0) + currentBottomInset, parentHeight)
  }

  private fun setupPageSwitchGestureBubble() {
      val bubble = binding.pageSwitchGestureBubble
      bubble.setOrientation(com.itsaky.androidide.ui.EdgeSnapBubbleView.Orientation.HORIZONTAL)
      bubble.setPosition(com.itsaky.androidide.ui.EdgeSnapBubbleView.Position.TOP)
      
      // 需求：点击事件切换 Header 区域的显示/隐藏（通过 3D 上下平移立体隐藏）
      bubble.setOnBubbleClickListener {
          toggleHeaderVisibilityWithAnimation()
      }
      
      // 需求：手势滑动打开/关闭整个 BottomSheet 抽屉
      bubble.setOnBubbleGestureListener(
          object : com.itsaky.androidide.ui.EdgeSnapBubbleView.OnBubbleGestureListener {
              override fun onDrag(fraction: Float) {
                  // Fraction 为负时表示向上拉（打开抽屉），为正时向下拉（无动作或尝试关闭已经关闭的抽屉）
                  // 注意：拖拽期间主要依赖 BottomSheet 自身的滚动响应
                  // 如果想强制映射 fraction 到 BottomSheet state 也可以，但容易与原生滑动手势冲突
              }

              override fun onRelease(fraction: Float) {
                  if (fraction < -0.15f) { // 向上滑动
                      tryExpandSheetFromControl()
                  } else if (fraction > 0.15f) { // 向下滑动
                      forceCollapse()
                  }
              }
          }
      )
  }

  /**
   * 使用属性动画模拟物理层级（Header 被符号栏遮挡向下/向上滑动隐藏）
   */
  private fun toggleHeaderVisibilityWithAnimation() {
      val contentWrapper = binding.headerContentWrapper
      val innerContent = binding.headerInnerContent
      
      if (innerContent.height <= 0) return // 视图未测量完成时不执行
      
      val contentHeight = innerContent.height.toFloat()
      
      // 如果当前是可见状态，执行隐藏动画：将 innerContent 向下平移，并利用 wrapper 的 clipChildren 进行裁切
      if (isHeaderVisible) {
          val anim = ObjectAnimator.ofFloat(innerContent, View.TRANSLATION_Y, 0f, contentHeight)
          anim.duration = 250
          anim.interpolator = DecelerateInterpolator()
          anim.addListener(object: AnimatorListenerAdapter(){
              override fun onAnimationEnd(animation: Animator) {
                  contentWrapper.visibility = View.GONE
                  isHeaderVisible = false
                  updatePeekHeight() // 高度改变，刷新 Peek
              }
          })
          anim.start()
          binding.pageSwitchGestureBubble.setArrowExpanded(false) // 箭头朝下指示可以展开
      } else {
          // 如果是隐藏状态，执行显示动画：从下方拉起
          contentWrapper.visibility = View.VISIBLE
          val anim = ObjectAnimator.ofFloat(innerContent, View.TRANSLATION_Y, contentHeight, 0f)
          anim.duration = 250
          anim.interpolator = DecelerateInterpolator()
          anim.addListener(object: AnimatorListenerAdapter(){
              override fun onAnimationEnd(animation: Animator) {
                  isHeaderVisible = true
                  updatePeekHeight() // 高度改变，刷新 Peek
              }
          })
          anim.start()
          binding.pageSwitchGestureBubble.setArrowExpanded(true) // 箭头朝上指示可以收起
      }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    setupDynamicPeekHeightAndIME()
    post {
      ViewCompat.requestApplyInsets(this)
      updatePeekHeight()
    }
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

          override fun onSlide(bottomSheet: View, slideOffset: Float) {
            // 需求：抽屉滑动期间，按照百分比同步隐藏/显示气泡与 Header（避免占满空间）
            // 当 slideOffset 从 0 到 1 变化时（从折叠到展开）
            // slideOffset = 0: 完全折叠, Alpha = 1
            // slideOffset = 1: 完全展开, Alpha = 0
            
            // 为了视觉体验，我们让 Header 提前消失（例如滑到一半 0.5 时就全透）
            val alphaValue = max(0f, 1f - (slideOffset * 2f))
            val translateY = slideOffset.coerceIn(0f, 1f) * 8f * resources.displayMetrics.density
            
            val syncedHeaderViews = listOf<View>(
              binding.floatingHeaderArea,
              binding.headerContentWrapper,
              binding.headerInnerContent,
              binding.headerDivider,
              binding.border.root,
              binding.cardView,
              binding.pageSwitchGestureBubble,
            )
            syncedHeaderViews.forEach { view ->
              view.alpha = alphaValue
              view.translationY = translateY
            }

            val hideHeaderArea = slideOffset >= 1f
            binding.floatingHeaderArea.visibility = if (hideHeaderArea) View.GONE else View.VISIBLE

            onSlideAction?.invoke(slideOffset)
          }
        }
    )
    behaviorCallbackAttached = true
  }

  fun showChild(index: Int) {
    binding.headerContainer.displayedChild = index
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

  fun canExpandSheet(): Boolean {
    return !expandBlocked && headerExpandEnabled
  }

  fun tryExpandSheetFromControl(): Boolean {
    if (!canExpandSheet()) return false
    behavior.state = BottomSheetBehavior.STATE_EXPANDED
    return true
  }

  fun forceCollapse() {
    if (behavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
      behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }
  }

  fun suspendHeaderExpandFor(durationMs: Long) {
    headerExpandEnabled = false
    binding.root.removeCallbacks(resumeHeaderExpandRunnable)
    binding.root.postDelayed(resumeHeaderExpandRunnable, durationMs)
  }

  private val resumeHeaderExpandRunnable = Runnable { headerExpandEnabled = true }

  fun setActionText(text: CharSequence) {
    onActionTextChanged?.invoke(text)
    binding.bottomAction.actionText.text = text
  }

  fun setActionProgress(progress: Int) {
    onActionProgressChanged?.invoke(progress)
    binding.bottomAction.progress.setProgressCompat(progress, true)
  }

  fun appendApkLog(line: LogLine) {
    pagerAdapter.logFragment?.appendLog(line)
  }

  fun appendBuildOut(str: String?) {
    pagerAdapter.buildOutputFragment?.appendOutput(str)
  }

  fun clearBuildOutput() {
    pagerAdapter.buildOutputFragment?.clearOutput()
  }

  fun handleDiagnosticsResultVisibility(errorVisible: Boolean) {
    runOnUiThread { pagerAdapter.diagnosticsFragment?.isEmpty = errorVisible }
  }

  fun handleSearchResultVisibility(errorVisible: Boolean) {
    runOnUiThread { pagerAdapter.searchResultFragment?.isEmpty = errorVisible }
  }

  fun setDiagnosticsAdapter(adapter: DiagnosticsAdapter) {
    runOnUiThread { pagerAdapter.diagnosticsFragment?.setAdapter(adapter) }
  }

  fun setSearchResultAdapter(adapter: SearchListAdapter) {
    runOnUiThread { pagerAdapter.searchResultFragment?.setAdapter(adapter) }
  }

  fun setStatus(text: CharSequence, @GravityInt gravity: Int) {
    onStatusChanged?.invoke(text, gravity)
    binding.buildStatus.statusText.gravity = gravity
    binding.buildStatus.statusText.text = text
  }

  private fun shareFile(file: File) {
    shareFile(context, file, "text/plain")
  }

  @Suppress("DEPRECATION")
  private fun shareText(text: String?, type: String) {
    if (text == null || TextUtils.isEmpty(text)) {
      flashError(context.getString(string.msg_output_text_extraction_failed))
      return
    }
    val pd =
        android.app.ProgressDialog.show(
            context,
            null,
            context.getString(string.please_wait),
            true,
            false,
        )
    executeAsyncProvideError(
        Callable { writeTempFile(text, type) },
        CallbackWithError<File> { result: File?, error: Throwable? ->
          pd.dismiss()
          if (result == null || error != null) {
            log.warn("Unable to share output", error)
            return@CallbackWithError
          }
          shareFile(result)
        },
    )
  }

  private fun writeTempFile(text: String, type: String): File {
    val file: Path = context.filesDir.toPath().resolve("$type.txt")
    try {
      if (Files.exists(file)) {
        Files.delete(file)
      }
      Files.write(file, text.toByteArray(StandardCharsets.UTF_8), CREATE_NEW, WRITE)
    } catch (e: IOException) {
      log.error("Unable to write output to file", e)
    }
    return file.toFile()
  }
}
