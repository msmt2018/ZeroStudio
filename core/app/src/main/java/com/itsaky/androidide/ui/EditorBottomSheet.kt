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
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.annotation.GravityInt
import androidx.annotation.NonNull
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
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

  // 日志分享/清空 FAB: 已经从 layout_editor_bottom_sheet.xml 移到 content_editor.xml
  // (CoordinatorLayout 直接子节点 + app:layout_anchor 锚定到本 BottomSheet 右下角),
  // 通过 setLogActionFabButtons() 由 BaseEditorActivity 注入. 在没注入时所有引用都是
  // null, 不会 NPE - 等 BaseEditorActivity.setupBottomSheet() 注入后才有实际效果.
  private var logClearFab: com.google.android.material.floatingactionbutton.FloatingActionButton? = null
  private var logShareFab: com.google.android.material.floatingactionbutton.FloatingActionButton? = null

  // 自定义 BottomSheetBehavior, 它的 onInterceptTouchEvent 会在
  // "触摸落在 AdvancedSymbolInputView 区域" 时直接 return false,
  // 阻止 IDE 抽屉抢走符号栏自己的内部展开/折叠手势.
  //
  // 必须用 lazy 而不是 init 时直接赋值, 因为 CoordinatorLayout.LayoutParams
  // 必须等到本 View 被父布局 attach 之后才存在 (XML inflate 阶段
  // LayoutInflater 是先创建 View, 再调用 setLayoutParams; 而 init {} 是在
  // View 构造期间执行的, 此时 layoutParams 还是 null 或者默认的
  // ViewGroup.LayoutParams, 直接强转 CoordinatorLayout.LayoutParams 会失败).
  // 所以下面这行 lazy 只是"占位", 真正在 onAttachedToWindow() 里把 behavior
  // 装到 layoutParams 上, 保证 CoordinatorLayout 拿到的就是 SymbolInputAware 版本.
  private val behavior: SymbolInputAwareBottomSheetBehavior<EditorBottomSheet> by lazy {
    SymbolInputAwareBottomSheetBehavior<EditorBottomSheet>(context, null).apply {
      isFitToContents = false
      skipCollapsed = false
      isHideable = false
      halfExpandedRatio = 0.5f
      // 触摸落在 AdvancedSymbolInputView 区域时不要拦截, 让符号栏自己处理.
      isEventOnExcludedArea = { event -> isTouchOnSymbolInput(event) }
    }
  }

  private var suppressNextHeaderClickExpand = false
  private var headerExpandEnabled = true
  private var expandBlocked = false
  private var behaviorCallbackAttached = false
  private var customBehaviorAttached = false

  var onHeaderPageChanged: ((Int) -> Unit)? = null
  var onActionTextChanged: ((CharSequence) -> Unit)? = null
  var onActionProgressChanged: ((Int) -> Unit)? = null
  var onStatusChanged: ((CharSequence, Int) -> Unit)? = null
  var onSlideAction: ((Float) -> Unit)? = null

  // 软键盘底部安全区域补丁
  private var currentBottomInset = 0

  // Header 区域是否可见(供 3D 滑出动画使用)
  private var isHeaderVisible = true

  // 跟踪 IME 上一次可见状态, 仅在状态切换 (false->true / true->false) 时才
  // 触发"关闭 IDE 抽屉"动作, 避免每次 WindowInsets 分发都重新设置 state.
  private var lastImeVisible = false

  /**
   * 当用户开始拖拽气泡展开 IDE 抽屉时, 主动隐藏 IME 软键盘.
   * Bug 2 修复: 拖拽手势和 IME 软键盘互斥, 展开抽屉时收起键盘.
   */
  private fun hideImeIfShown() {
    if (!lastImeVisible) return
    val activity = context as? androidx.appcompat.app.AppCompatActivity ?: return
    val decor = activity.window?.decorView ?: return
    val controller = ViewCompat.getWindowInsetsController(decor) ?: return
    controller.hide(WindowInsetsCompat.Type.ime())
  }

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
              showLogActionFabs()
            } else {
              hideLogActionFabs()
            }
          }

          override fun onTabUnselected(tab: Tab) {}
          override fun onTabReselected(tab: Tab) {}
        }
    )

    // 默认隐藏日志 FAB, 等 Tab 选中 ShareableOutputFragment 时再显示.
    hideLogActionFabs()

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

    // 不再在 AdvancedSymbolInputView 上挂 OnTouchListener 手动调用
    // requestDisallowInterceptTouchEvent. 原因: CoordinatorLayout.Behavior
    // (也就是 BottomSheetBehavior.onInterceptTouchEvent) 是 CoordinatorLayout
    // 在分发 MotionEvent 之前最先调用的钩子, 任何挂在子 view 上的 OnTouchListener
    // 都比 Behavior 晚, 此时 IDE 抽屉已经决定了要不要拦截, 符号栏再也拿不到
    // 这个手势序列.
    //
    // 正确做法: 通过 SymbolInputAwareBottomSheetBehavior.onInterceptTouchEvent
    // 重写拦截逻辑, 在触摸落在 AdvancedSymbolInputView 区域时直接 return false,
    // 把整个手势交还给符号栏, 让符号栏自己的 onInterceptTouchEvent / onTouchEvent
    // 完整处理内部展开/折叠抽屉. 这是 EditorBottomSheet.onAttachedToWindow()
    // 那里安装的, 不要再在这重复挂 OnTouchListener.
  }

  /**
   * 由 BaseEditorActivity 调用, 把 CoordinatorLayout 上的日志分享/清空 FAB 注入进来.
   * 注入之后, EditorBottomSheet 内部会根据当前选中的 Tab 自行 show/hide 这两个按钮,
   * 并把点击事件绑定到当前选中的 ShareableOutputFragment 上.
   */
  fun setLogActionFabButtons(
      clearFab: com.google.android.material.floatingactionbutton.FloatingActionButton,
      shareFab: com.google.android.material.floatingactionbutton.FloatingActionButton,
  ) {
    this.logClearFab = clearFab
    this.logShareFab = shareFab

    // 绑定 click listener (使用注入的 FAB).
    TooltipCompat.setTooltipText(clearFab, context.getString(string.title_clear_output))
    clearFab.setOnClickListener {
      val fragment: Fragment = pagerAdapter.getFragmentAtIndex(binding.tabs.selectedTabPosition)
      if (fragment !is ShareableOutputFragment) {
        log.error("Unknown fragment: {}", fragment)
        return@setOnClickListener
      }
      (fragment as ShareableOutputFragment).clearOutput()
    }

    shareFab.setOnClickListener {
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

    // 根据当前选中的 Tab 决定是否显示.
    val fragment: Fragment? = try {
      pagerAdapter.getFragmentAtIndex(binding.tabs.selectedTabPosition)
    } catch (t: Throwable) {
      null
    }
    if (fragment is ShareableOutputFragment) {
      showLogActionFabs()
    } else {
      hideLogActionFabs()
    }
  }

  private fun showLogActionFabs() {
    logClearFab?.show()
    logShareFab?.show()
  }

  private fun hideLogActionFabs() {
    logClearFab?.hide()
    logShareFab?.hide()
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

    // 把 fully expanded 的位置从全屏 (expandedOffset = 0) 改成编辑器界面
    // toolbar 下方 progress_indicator 的底部. 这样展开抽屉时, 用户能看到
    // 上方 toolbar + progress_indicator, 下方是 IDE 抽屉 (构建输出/文件/搜索等),
    // 不再被抽屉整个盖住.
    setupExpandedOffset()

    // 将 WindowInsets 拦截用于 IME 同步；BaseEditorActivity 也会直接转发一次，
    // 以覆盖 CoordinatorLayout/BottomSheetBehavior 未把 IME insets 分发到子 View 的设备。
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
      applyEditorWindowInsets(insets)
      insets
    }
    behavior.isGestureInsetBottomIgnored = true
  }

  /**
   * 把 [BottomSheetBehavior.getExpandedOffset] 设为 progress_indicator.bottom
   * 在 EditorBottomSheet.parent (CoordinatorLayout) 坐标里的位置.
   *
   * XML 结构 (content_editor.xml):
   *   CoordinatorLayout (realContainer, parent)
   *     AppBarLayout (editor_appBarLayout)
   *       editor_toolbar
   *       progress_indicator    <-- 我们要把抽屉顶边放在这里
   *       tabs
   *     EditorBottomSheet (this)
   *
   * 几何关系:
   *   expandedOffset  = progress_indicator.bottom - CoordinatorLayout.top
   *
   * 用 [View.getLocationOnScreen] 算绝对屏幕坐标差, 而不是 progressIndicator.bottom
   * 直接取值, 因为 progress_indicator.bottom 是它自己在 AppBarLayout 内的相对值,
   * 还要加上 AppBarLayout 自身在 CoordinatorLayout 里的 offset, 计算起来更绕.
   * 屏幕坐标差是最直接的, 且对 AppBarLayout 的 scroll/collapse 行为 (即使将来
   * 启用) 都能正确跟随.
   */
  private fun setupExpandedOffset() {
    val parentView = parent as? View ?: return
    val progressIndicator = parentView.findViewById<View>(R.id.progress_indicator) ?: run {
      log.warn("setupExpandedOffset: R.id.progress_indicator not found in parent; expandedOffset left as default 0")
      return
    }
    // progress_indicator 还没 measure/layout 完时, 不能算. 用 doOnLayout 等下一帧.
    progressIndicator.doOnLayout {
      applyExpandedOffset(parentView, progressIndicator)
    }
    // 后续 toolbar / progress_indicator / tabs 任何一项尺寸变化都要重算.
    // (比如将来 toolbar 菜单图标数变化让 toolbar 高度变化, progress_indicator 底
    // 部就跟着动.)
    progressIndicator.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
      if (bottom != oldBottom) {
        applyExpandedOffset(parentView, progressIndicator)
      }
    }
  }

  private fun applyExpandedOffset(parentView: View, progressIndicator: View) {
    if (progressIndicator.width == 0 || progressIndicator.height == 0) return
    val parentLoc = IntArray(2)
    val progressLoc = IntArray(2)
    parentView.getLocationOnScreen(parentLoc)
    progressIndicator.getLocationOnScreen(progressLoc)
    // progress_indicator.bottom 在 parentView 坐标里 = progress 屏幕 y + height - parent 屏幕 y
    val newOffset = (progressLoc[1] + progressIndicator.height) - parentLoc[1]
    val clamped = max(0, newOffset)
    if (behavior.expandedOffset != clamped) {
      log.info("applyExpandedOffset: progress_indicator.bottom={} (parent coords) -> expandedOffset={}", newOffset, clamped)
      behavior.expandedOffset = clamped
    }
  }

  fun applyEditorWindowInsets(insets: WindowInsetsCompat) {
      val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
      val navInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
      val isImeVisibleNow = insets.isVisible(WindowInsetsCompat.Type.ime())
      val targetBottomInset = if (isImeVisibleNow) imeInsets.bottom else navInsets.bottom
      // 同步 IME 状态到底层 BottomSheetBehavior. 旧版 v2.0.7 的 setImeVisible
      // 就是这个作用: IME 可见时让 BottomSheet 忽略底部手势 inset (用户的滑动
      // 不会触发系统 home 手势, 全部交给 BottomSheet), IME 隐藏时恢复
      // (尊重系统 nav bar 区域的手势).
      behavior.isGestureInsetBottomIgnored = isImeVisibleNow
      updateBottomInset(targetBottomInset)

      // Bug 1 修复: IME 弹出时关闭 IDE 抽屉.
      //
      // 用户手动验证结论: "IME 弹出 -> 关闭 IDE 抽屉" 这一动作触发后, 现有的
      // IME 同步代码 (BaseEditorActivity.onApplyWindowInsets 里手动 resize
      // contentCard.height = contentCardRealHeight - imeBottom) 会自动把
      // 符号栏顶部贴到 IME 顶部. 不需要再改 IME 同步代码本身.
      //
      // 也就是说: IME 和 IDE 抽屉应该互斥, 不能同时占满屏幕. 用户在编辑器
      // 输入文本时只该看到 IME, 在看 IDE 抽屉时只该看到抽屉.
      //
      // 只在 lastImeVisible -> isImeVisibleNow 的状态切换瞬间触发, 避免
      // 每次 WindowInsets 分发都重新设 state 干扰行为.
      if (isImeVisibleNow != lastImeVisible) {
          lastImeVisible = isImeVisibleNow
          if (isImeVisibleNow) {
              val currentState = behavior.state
              if (currentState == BottomSheetBehavior.STATE_HALF_EXPANDED ||
                  currentState == BottomSheetBehavior.STATE_EXPANDED) {
                  log.info("IME shown while drawer open (state={}), force-collapsing drawer", currentState)
                  behavior.state = BottomSheetBehavior.STATE_COLLAPSED
              }
          }
      }
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
    // 必须通过 doOnLayout 等待 floatingHeaderArea 真实高度, 否则
    // floatingHeaderArea.height == 0 时, peekHeight 只有 imeBottom, 符号栏
    // 会被推到 IME 下方而不是贴在 IME 顶部.
    schedulePeekHeightUpdate()
  }

  /**
   * 安排一次 peekHeight 更新:
   *   1) 先用当前 (可能为 0 的) 高度 + IME inset 设置 peekHeight, 触发 layout.
   *   2) 通过 doOnLayout 在下一帧布局完成时, 用真实高度再设一次, 让符号栏底部
   *      精准落在 IME 顶部.
   *
   * 这个双重写入是必须的:
   *   - 第一次写入让 BottomSheet 立即开始向上位移 (跟着 peekHeight 变化).
   *   - 第二次写入修复 insets 分发时 height 还是 0 的时间窗, 避免 peekHeight
   *     只是 imeBottom 导致符号栏被压在 IME 下面.
   */
  private fun schedulePeekHeightUpdate() {
    removeCallbacks(deferredPeekHeightRunnable)
    // 第一次: 用当前高度立刻设置一次, 触发 layout.
    updatePeekHeight()
    // 第二次: 等下一帧布局完成, 用真实高度再设一次.
    binding.floatingHeaderArea.doOnLayout {
        removeCallbacks(deferredPeekHeightRunnable)
        post(deferredPeekHeightRunnable)
    }
  }

  private val deferredPeekHeightRunnable = Runnable { updatePeekHeight() }

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
      // 【IME 同步原理】
      //
      // 之前这里用 `peekHeight = headerHeight + imeBottom` 的累加写法, 假设
      // parentHeight 在 IME 弹出前后保持不变. 这个假设是错的:
      //   - IDEActivity.enableEdgeToEdge() 调了
      //     WindowCompat.setDecorFitsSystemWindows(window, false) 开了 edge-to-edge
      //   - edge-to-edge 模式下, `windowSoftInputMode="adjustResize"` 不会再
      //     自动 resize activity content view, 跟旧版 v2.0.7 之前的 IDE 行为不一致
      //   - 旧版 v2.0.7 的解法是手动 `contentCard.height = contentCardRealHeight - imeBottom`
      //
      // 现在 BaseEditorActivity.onApplyWindowInsets 恢复了手动 resize. 配合这里的
      // `peekHeight = headerHeight`, 几何关系就是:
      //   BottomSheet.top    = parentHeight - peekHeight
      //                    = (contentCardRealHeight - imeBottom) - headerHeight
      //   符号栏底部 (即 AdvancedSymbolInputView.bottom)
      //                    = BottomSheet.top + headerHeight
      //                    = contentCardRealHeight - imeBottom
      //                    = contentCard.bottom (因为 contentCard 已被手动 resize 到这个高度)
      //                    = IME 顶部
      //
      // 不再叠加 currentBottomInset 到 peekHeight: parentHeight 已经包含了
      // IME 调整 (由 BaseEditorActivity 手动减 imeBottom), 再叠加就偏移了.
      val headerHeight = binding.floatingHeaderArea.height
      val parentHeight = (parent as? View)?.height ?: resources.displayMetrics.heightPixels
      behavior.peekHeight = min(max(headerHeight, 0), parentHeight)
      requestLayout()
  }

  /**
   * 旧版 v2.0.7 的代码片段 - 仅作历史参考保留在注释里, 不要恢复成可执行代码.
   * 在 IME insets 变化后, 等下一帧布局完成再更新一次 peekHeight.
   * 用于覆盖 insets 分发早于布局完成的时间窗, 保证 floatingHeaderArea.height
   * 已经是新值, 符号栏底部才能精确对齐到 IME 顶部.
   *
   * 注意: 真正的 deferredPeekHeightRunnable 已经在文件上方声明 (第 356 行),
   * 在 schedulePeekHeightUpdate() 之前声明以满足 Kotlin 类初始化顺序的
   * forward reference 要求, 此处不要再重复声明.
   *
   * private val deferredPeekHeightRunnable by lazy { Runnable { updatePeekHeight() } }
   */

  private fun setupPageSwitchGestureBubble() {
      val bubble = binding.pageSwitchGestureBubble
      bubble.setOrientation(com.itsaky.androidide.ui.EdgeSnapBubbleView.Orientation.HORIZONTAL)
      bubble.setPosition(com.itsaky.androidide.ui.EdgeSnapBubbleView.Position.TOP)

      // 需求：点击事件根据抽屉状态切换:
      //   - STATE_HALF_EXPANDED / STATE_EXPANDED (抽屉打开) -> 关闭抽屉, 回到 COLLAPSED.
      //     这是用户原始需求: "在 50% 时点击 -> 关闭 IDE 抽屉", 关闭后 IME 同步
      //     就能正常工作 (因为符号栏上方不再被抽屉遮挡). Bug 1 修复.
      //   - STATE_COLLAPSED / STATE_DRAGGING (抽屉折叠中) -> 切换 Header 区域
      //     的显示/隐藏, 用 3D 上下平移立体隐藏 Header.
      bubble.setOnBubbleClickListener {
          val state = behavior.state
          if (state == BottomSheetBehavior.STATE_HALF_EXPANDED ||
              state == BottomSheetBehavior.STATE_EXPANDED) {
              // 关闭抽屉. 不需要清 IME, 这里的"点击"通常是用户在查看抽屉
              // 不想同时弹键盘; 之后用户主动点编辑器, IME 弹出时 Bug 1 修复
              // 已经能再次保证抽屉关闭.
              log.info("Bubble click at state={}, force-collapsing drawer", state)
              behavior.state = BottomSheetBehavior.STATE_COLLAPSED
          } else {
              toggleHeaderVisibilityWithAnimation()
          }
      }

      // 需求：手势滑动打开/关闭整个 BottomSheet 抽屉
      // 注意: 此处不再用 setOnBubbleGestureListener 做"松手后二值吸附".
      // 我们把拖拽接管到自定义 OnTouchListener (drawerDragListener) 里:
      //   - 拖拽期间 1:1 跟随手指, 不会在 80% 位置被 BottomSheetBehavior 自动吸附到 STATE_EXPANDED.
      //   - 松手时按 sheet.top 位置做三段阈值判定 (COLLAPSED / HALF_EXPANDED / EXPANDED).
      // EdgeSnapBubbleView 自己的 onBubbleGestureListener 留空, 避免重复驱动抽屉.
      bubble.setOnBubbleGestureListener(
          object : com.itsaky.androidide.ui.EdgeSnapBubbleView.OnBubbleGestureListener {
              override fun onDrag(fraction: Float) {
                  // 由 drawerDragListener 接管, 这里不做事.
              }

              override fun onRelease(fraction: Float) {
                  // 由 drawerDragListener 接管, 这里不做事.
              }
          }
      )

      // 安装自定义拖拽监听器, 覆盖默认的 BottomSheetBehavior 拖拽吸附行为.
      bubble.setOnTouchListener(drawerDragListener)
  }

  // === 抽屉自定义拖拽状态 ===
  private var drawerDragInitialY = 0f
  private var drawerDragInitialSheetTop = 0
  private var drawerDragInitialTranslationY = 0f
  private var drawerDragActive = false
  private val drawerTouchSlopPx by lazy {
      ViewConfiguration.get(context).scaledTouchSlop
  }
  // Bug 5.1: 预览隐藏态下, 标记本次手势序列是否已经触发过上滑恢复符号输入控件,
  // 防止 showFromPreview() 在同一手势里被多次调用.
  private var previewSwipeRestoreConsumed = false

  /**
   * 抽屉拖拽监听器, 挂在顶部手势气泡上.
   *
   * 行为契约:
   * 1. ACTION_DOWN: 记录初始手指 y、sheet.top、sheet.translationY; 调用
   *    requestDisallowInterceptTouchEvent(true) 阻断 CoordinatorLayout/
   *    BottomSheetBehavior 的拖拽拦截, 让 IDE 抽屉不会被系统默认 ViewDragHelper
   *    抢走手势; 返回 false, 气泡自己的 onTouchEvent 仍能处理点击 (无拖拽时).
   * 2. ACTION_MOVE: 越过 touchSlop 后视为进入拖拽态, 期间 sheet 通过 translationY
   *    跟随手指 deltaY 1:1 变化, 不会在 80% 位置被 BottomSheetBehavior 默认吸附到
   *    STATE_EXPANDED.
   *
   *    注意: 这里**不能**用 `behavior.state = STATE_DRAGGING` 来防止
   *    BottomSheetBehavior.onLayoutChild 重新定位 sheet.top. 因为 STATE_DRAGGING
   *    是 BottomSheetBehavior 内部状态, setState() 会直接抛 IllegalArgumentException
   *    "STATE_DRAGGING should not be set externally" (com.google.android.material
   *    BottomSheetBehavior.java:1480). 也不能直接用 offsetTopAndBottom 改 sheet.top,
   *    因为下一次 layout pass 时 onLayoutChild 会按 state 把 sheet.top 重置回
   *    state 对应位置, 把我们的 offset 抵消.
   *
   *    正确做法: 用 translationY 移动 view (这是 view 绘制时的平移, 不影响 layout),
   *    sheet.top 保持 state 对应位置不变. 视觉位置 = sheet.top + translationY.
   *    松手时 reset translationY=0 再 setState(newState), onLayoutChild 会把
   *    sheet.top 重新设到 newState 对应位置, translationY=0 保证视觉上从当前
   *    位置无缝过渡到 newState 位置, 不会出现跳变.
   * 3. ACTION_UP/CANCEL: 根据当前 sheet 的视觉位置 (initialTop + translationY)
   *    与 [collapsedTop, halfTop, expandedTop] 三段阈值决定最终 state. 用户要求:
   *    - 上滑到 50% 以上 (visualTop <= halfTop) -> STATE_EXPANDED (完全展开)
   *    - 下滑到 50% 以下但还没接近 collapsed -> STATE_HALF_EXPANDED (停靠一半)
   *    - 继续下滑到接近 collapsed 位置 -> STATE_COLLAPSED (完全折叠)
   *    行为只依赖位置, 不依赖 velocity, 避免误吸附.
   */
  private val drawerDragListener = View.OnTouchListener { v, event ->
      when (event.actionMasked) {
          MotionEvent.ACTION_DOWN -> {
              drawerDragInitialY = event.rawY
              drawerDragInitialSheetTop = top
              drawerDragInitialTranslationY = translationY
              drawerDragActive = false
              // Bug 5.1: 预览隐藏态下, 标记本次手势是否已经触发"上滑恢复符号输入控件".
              // 用于保证 showFromPreview() 只调用一次, 后续 MOVE 继续走正常抽屉拖拽.
              previewSwipeRestoreConsumed = false
              // requestDisallowInterceptTouchEvent 是 ViewParent 的方法, 不是 View 的.
              // 调用 v.parent 才能阻断 CoordinatorLayout/BottomSheetBehavior 的拦截.
              v.parent?.requestDisallowInterceptTouchEvent(true)
              // 不消费, 让气泡自己的 onTouchEvent 继续处理点击/长按.
              false
          }
          MotionEvent.ACTION_MOVE -> {
              val deltaY = event.rawY - drawerDragInitialY

              // === Bug 5.1: 预览隐藏态下的手势分流 ===
              // 进入图片/Markdown 预览 fragment 时, SymbolInputVisibilityManager 已经
              // 把符号输入控件 (以及 header_content_wrapper / header_divider) 隐藏,
              // 屏幕只剩 EdgeSnapBubbleView 可见. 此时手势分两种:
              //   1. 上滑 (deltaY < -touchSlop) -> 恢复符号输入控件到正常可见位置,
              //      然后继续走正常抽屉拖拽, 让用户能顺势把抽屉拉起来.
              //   2. 下滑 (deltaY > touchSlop)  -> 保持屏外 (默认位置), 消费事件
              //      不让抽屉被进一步拉低 (本来就已经 COLLAPSED, 再拉没意义且会
              //      让气泡脱离手指产生视觉错位).
              if (SymbolInputVisibilityManager.previewHidden) {
                  if (deltaY < -drawerTouchSlopPx) {
                      // 上滑: 恢复符号输入控件. 只触发一次, 之后允许正常拖拽.
                      if (!previewSwipeRestoreConsumed) {
                          previewSwipeRestoreConsumed = true
                          SymbolInputVisibilityManager.showFromPreview()
                          // 恢复后, drawerDragInitialSheetTop 仍然是按下时的 top,
                          // 但 peekHeight 已经因为符号栏重新可见而变大, 抽屉视觉
                          // 位置会由 BottomSheetBehavior 下一帧重新对齐. 这里直接
                          // 让本次手势结束, 不再继续 translationY 拖拽, 避免和
                          // onLayoutChild 的重新定位打架产生跳变.
                          v.parent?.requestDisallowInterceptTouchEvent(false)
                          drawerDragActive = false
                          return@OnTouchListener true
                      }
                  } else if (deltaY > drawerTouchSlopPx) {
                      // 下滑: 保持屏外, 消费事件不让抽屉被拖动.
                      return@OnTouchListener true
                  }
                  // 未越过 touchSlop 时也不消费, 让气泡自己处理点击.
                  return@OnTouchListener false
              }
              // === 预览隐藏态分支结束 ===

              if (!drawerDragActive && kotlin.math.abs(deltaY) > drawerTouchSlopPx) {
                  drawerDragActive = true
                  // 不再设 behavior.state = STATE_DRAGGING (会被 BottomSheetBehavior
                  // 拒绝并抛 IllegalArgumentException). 改用 translationY 移动
                  // view 视觉位置, sheet.top 保持 state 对应位置不被覆盖.
                  // Bug 2 修复: 拖拽开始瞬间, 如果 IME 软键盘已弹出, 主动隐藏它.
                  // 原因: 抽屉和 IME 应该互斥, 拖拽展开抽屉时不应该让键盘同时占
                  // 屏, 否则用户看不到拖拽结果, 体验割裂.
                  hideImeIfShown()
              }
              if (drawerDragActive) {
                  val parentHeight = (parent as? View)?.height ?: height
                  val initialTop = drawerDragInitialSheetTop
                  val newTop = (initialTop + deltaY).toInt()
                      .coerceIn(behavior.expandedOffset, parentHeight)
                  // 计算 translationY = visualTop - initialTop. 这样视觉位置
                  // = top (state 对应) + translationY = newTop.
                  val newTranslationY = (newTop - initialTop).toFloat()
                  if (newTranslationY != translationY) {
                      translationY = newTranslationY
                  }
                  return@OnTouchListener true
              }
              false
          }
          MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
              v.parent?.requestDisallowInterceptTouchEvent(false)
              if (drawerDragActive) {
                  drawerDragActive = false
                  val parentHeight = (parent as? View)?.height ?: height
                  // 当前视觉位置 = 初始 sheet.top + 当前 translationY.
                  val initialTop = drawerDragInitialSheetTop
                  val currentTop = initialTop + translationY.toInt()
                  val collapsedTop = parentHeight - behavior.peekHeight
                  val expandedTop = behavior.expandedOffset
                  val halfTop = (expandedTop + collapsedTop) / 2
                  val newState = decideDrawerState(currentTop, expandedTop, halfTop, collapsedTop)
                  // 先重置 translationY=0 再设 state. setState 会触发 onLayoutChild
                  // 把 sheet.top 重新定位到 newState 对应位置, 这时 translationY=0
                  // 保证视觉上从当前位置无缝过渡到 newState 位置, 不会出现跳变.
                  translationY = 0f
                  behavior.state = newState
                  return@OnTouchListener true
              }
              false
          }
          else -> false
      }
  }

  /**
   * 根据 sheet.top 在三段阈值中的位置决定 BottomSheet 的最终 state.
   *
   * 用户原始需求 (中文翻译):
   * - 松手在 50% 以上 (sheet.top 较小, 抽屉更展开) -> 完全展开
   * - 松手在 50% 以下但还没接近 collapsed -> 停靠一半 (HALF_EXPANDED)
   * - 松手在接近 collapsed 位置 (例如已经下滑到 collapsedTop 的 80% 以下) -> 完全折叠
   *
   * 不使用 velocity 判定, 完全按位置, 避免快速下滑时误吸附.
   */
  private fun decideDrawerState(
      sheetTop: Int,
      expandedTop: Int,
      halfTop: Int,
      collapsedTop: Int,
  ): Int {
      // 1. sheet.top 处于 expanded 半区 -> EXPANDED
      if (sheetTop <= halfTop) {
          return BottomSheetBehavior.STATE_EXPANDED
      }
      // 2. sheet.top 处于 half 与 collapsed 之间的上 20% 区段 -> HALF_EXPANDED (用户要求"停靠一半")
      val halfToCollapsedSpan = (collapsedTop - halfTop).coerceAtLeast(1)
      val upperFifthOfLowerHalf = collapsedTop - halfToCollapsedSpan / 5
      if (sheetTop <= upperFifthOfLowerHalf) {
          return BottomSheetBehavior.STATE_HALF_EXPANDED
      }
      // 3. 其余 (非常接近 collapsed) -> COLLAPSED
      return BottomSheetBehavior.STATE_COLLAPSED
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

  override fun onFinishInflate() {
    super.onFinishInflate()
    // 此时 LayoutInflater 已经把 layoutParams 替换成 CoordinatorLayout.LayoutParams
    // (来自 XML 的 app:layout_behavior="BottomSheetBehavior" 也已经解析好), 是最早
    // 能稳妥替换 Behavior 的时机. 一定要在 BaseEditorActivity.setupBottomSheet()
    // 通过 BottomSheetBehavior.from() 拿引用之前安装, 否则基类缓存的就是默认 Behavior,
    // 后续基类读 state/expandedOffset 时拿不到 SymbolInputAware 版本的状态.
    installCustomBehavior()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    // 【关键】在 layoutParams 已经是 CoordinatorLayout.LayoutParams 之后, 把
    // SymbolInputAwareBottomSheetBehavior 装上去, 替换 XML 默认的 BottomSheetBehavior.
    //
    // 之前在 init {} (或 lazy 第一次访问) 时机太早: 此时 CoordinatorLayout 还没有
    // 调 setLayoutParams 把本 view 的 layoutParams 换成 CoordinatorLayout.LayoutParams,
    // lp?.behavior = it 这一行实际上没有生效, CoordinatorLayout 后面照旧使用
    // XML 里的默认 BottomSheetBehavior, 这才是符号栏抽屉手势打不开的根本原因.
    //
    // 现在先在 onFinishInflate() 里装填一次 (确保 BaseEditorActivity.setupBottomSheet()
    // 通过 BottomSheetBehavior.from() 拿到的是 SymbolInputAware 版本), 这里再保险一次.
    installCustomBehavior()

    setupDynamicPeekHeightAndIME()
    post {
      ViewCompat.requestApplyInsets(this)
      updatePeekHeight()
    }
    ensureBehaviorCallbackAttached()
  }

  /**
   * 把 SymbolInputAwareBottomSheetBehavior 装到 layoutParams 上, 替换 XML 默认的
   * BottomSheetBehavior. 这个方法是幂等的, 多次调用只会真正执行一次.
   */
  private fun installCustomBehavior() {
    if (customBehaviorAttached) return
    val lp =
        layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            ?: return
    // 替换为 SymbolInputAware 版本. 必须在 attach 之前, 否则 CoordinatorLayout
    // 第一次 layout pass 还会走默认 Behavior 的状态机 (STATE_DRAGGING 等),
    // 等下次状态更新才换过来, 中间有一帧错误状态.
    lp.behavior = behavior
    behavior.isGestureInsetBottomIgnored = true
    customBehaviorAttached = true
  }

  /**
   * 判断一个 MotionEvent 是否落在 AdvancedSymbolInputView 的屏幕矩形内.
   *
   * 注意: 这里用 event.rawX/rawY (屏幕绝对坐标) 与 AdvancedSymbolInputView 的
   * getLocationOnScreen() (同样是屏幕绝对坐标) 比较. 不要用 getX/getY (相对父布局),
   // 也不要用 event.x/event.y (相对自身), 这两种都会导致判断错误.
   */
  private fun isTouchOnSymbolInput(event: MotionEvent): Boolean {
    val symbolInput = binding.externalSymbolInputView
    if (symbolInput.width == 0 || symbolInput.height == 0 || !symbolInput.isShown) {
      return false
    }
    val location = IntArray(2)
    symbolInput.getLocationOnScreen(location)
    val x = event.rawX.toInt()
    val y = event.rawY.toInt()
    return x >= location[0] && x <= location[0] + symbolInput.width &&
        y >= location[1] && y <= location[1] + symbolInput.height
  }

  private fun ensureBehaviorCallbackAttached() {
    if (behaviorCallbackAttached) return
    behavior.addBottomSheetCallback(
        object : BottomSheetBehavior.BottomSheetCallback() {
          override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (!canExpandSheet() && newState == BottomSheetBehavior.STATE_EXPANDED) {
              forceCollapse()
            }
            // 半展开 (50% 停靠) 与完全展开都允许: 用户在抽屉上滑到 50% 时会
            // 自动停靠, 继续上滑超过 50% 才完全展开. STATE_HALF_EXPANDED 与
            // STATE_EXPANDED 都是有效的"打开"状态, 后续返回键/手势关闭抽屉时
            // 会从这两个状态折叠回 peek.
            // 不在此处做强制状态切换, 保持 BottomSheetBehavior 默认的吸附行为
            // (释放手指时按位置和速度吸附到最近的状态), 这样抽屉能精确跟随手指.
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

  /**
   * 半展开抽屉 (用于 onboarding 等自动化场景).
   *
   * 与 [tryExpandSheetFromControl] 的区别是: 这里直接设置 [BottomSheetBehavior.STATE_HALF_EXPANDED]
   * (不检查 [canExpandSheet]), 给引导教程一个稳定可重现的中间状态用于演示.
   */
  fun expandToHalf() {
    behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
  }

  /**
   * 取得当前 BottomSheetBehavior 状态 (用于 onboarding 引导判断).
   *
   * 暴露此方法是为了让 [com.itsaky.androidide.ui.EditorBottomSheetOnboardingController]
   * 之类的辅助类能读取当前抽屉状态, 而无需破坏 [behavior] 的封装.
   */
  fun getCurrentBehaviorState(): Int = behavior.state

  /**
   * 取得底层 [BottomSheetBehavior] (供 onboarding 等扩展使用).
   *
   * 注意: 该方法只用于"读取 + 受控设置 state", 调用方不应该在
   * 这里 setState 干扰 BottomSheet 自身的拖拽逻辑. 若需要自动展开,
   * 优先使用 [tryExpandSheetFromControl] / [expandToHalf] / [forceCollapse].
   */
  fun getBehavior(): BottomSheetBehavior<EditorBottomSheet> = behavior

  fun forceCollapse() {
    if (behavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
      behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }
  }

  /**
   * PR-D7: 切到指定 fragment class 所在的 tab,找不到则 no-op。
   * 不会自动展开底部 sheet —— 调用方自己决定要不要 [tryExpandSheetFromControl]/
   * [expandToHalf]。
   *
   * @return true 表示确实切了;false 表示没有这个 tab 或没初始化
   */
  fun selectTabByFragmentClass(@NonNull clazz: Class<out androidx.fragment.app.Fragment>): Boolean {
    val idx = pagerAdapter.findIndexOfFragmentByClass(clazz)
    if (idx < 0) return false
    return runCatching {
      binding.pager.setCurrentItem(idx, false)
      val tab = binding.tabs.getTabAt(idx)
      if (tab != null && binding.tabs.selectedTabPosition != idx) tab.select()
      true
    }.getOrDefault(false)
  }

  /**
   * PR-D7: 调试时用户期望看到断点/变量/调用栈/日志点等调试器 UI。
   * 当前 sheet 只有 LogpointFragment 注册了 tab;展开 + 切到该 tab。
   * 调用方应确保 [com.itsaky.androidide.debugger.DebuggerController]
   * 实际连上了 / 暂停了,否则只是空 logpoint 列表。
   */
  fun openDebuggerTab() {
    if (!selectTabByFragmentClass(com.itsaky.androidide.debugger.fragment.LogpointFragment::class.java)) {
      // 找不到 tab,什么都不做,不要强行 expand 一个空 sheet
      return
    }
    if (canExpandSheet()) {
      behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
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
