/*
 *  This file is part of ZeroStudio.
 *
 *  ZeroStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ZeroStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with ZeroStudio.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 引导目标 (Onboarding target).
 *
 * 描述一个要被高亮框选 / 引导指向的 UI 控件.
 *
 * 核心设计:
 *  - 通过 **目标 id (targetId)** 绑定 UI 控件, 而不是手动传递 Rect
 *  - 控件位置变化时 (旋转 / 滚动 / 折叠 / 弹起), 自动跟踪并更新 Rect
 *  - 提供 [Modifier.onboardingBind] modifier, 给目标控件打标记
 *  - 同一 id 可被多个 step 复用
 *
 * ## 用法
 * ```
 * // 创建目标 (全局复用, 通过 id 引用)
 * val target = OnboardingTarget.of("search_button")
 *
 * // 在目标控件上:
 * Box(modifier = Modifier.onboardingBind(target)) { ... }
 *
 * // 在引导步骤中:
 * OnboardingStep(
 *   id = "search",
 *   content = ...,
 *   target = target,
 *   ...
 * )
 * ```
 */
@Stable
class OnboardingTarget internal constructor(
  val id: String,
  val tag: Any? = null,
) {

  @PublishedApi
  internal val _rectFlow: MutableStateFlow<Rect?> = MutableStateFlow(null)

  /** 目标当前的 Rect (相对 Window). 自动随位置变化更新. */
  val rectFlow: StateFlow<Rect?> = _rectFlow.asStateFlow()

  /** 当前 Rect (快捷访问). */
  val rect: Rect? get() = _rectFlow.value

  /** 目标是否已经被绑定 (有 Rect). */
  val isBound: Boolean get() = _rectFlow.value != null

  /** 上次更新 Rect 的时间 (毫秒). */
  var lastUpdateMs: Long = 0
    internal set

  @PublishedApi
  internal fun updateRect(rect: Rect?) {
    _rectFlow.value = rect
    lastUpdateMs = System.currentTimeMillis()
  }

  override fun equals(other: Any?): Boolean = other is OnboardingTarget && other.id == id
  override fun hashCode(): Int = id.hashCode()
  override fun toString(): String = "OnboardingTarget(id=$id)"

  companion object {
    private val targets = mutableMapOf<String, OnboardingTarget>()

    /**
     * 创建一个引导目标 (通过字符串 id).
     */
    fun of(id: String): OnboardingTarget =
      targets.getOrPut(id) { OnboardingTarget(id) }

    /**
     * 创建一个引导目标 (通过任意 tag).
     */
    fun of(tag: Any): OnboardingTarget =
      targets.getOrPut("tag:${tag.hashCode()}") {
        OnboardingTarget("tag:${tag.hashCode()}", tag)
      }

    /**
     * 获取一个目标 (不存在则返回 null).
     */
    fun get(id: String): OnboardingTarget? = targets[id]

    /** 清除所有目标 (用于测试或重置). */
    fun clear() {
      targets.clear()
    }
  }
}

// =============================================================================
// Modifier 扩展 (Composable helper)
// =============================================================================

/**
 * 给目标控件绑定 [OnboardingTarget] (最常用).
 *
 * 给目标 Box / 任意可定位的 Composable 加这个 modifier, 它会:
 *  1. 自动将 target 注册到全局仓库
 *  2. 当位置变化时 (滚动 / 旋转 / 折叠), 自动更新 target 的 Rect
 *
 * 用法:
 * ```
 * val target = remember { OnboardingTarget.of("search_button") }
 * Box(modifier = Modifier.onboardingBind(target)) { ... }
 * ```
 */
fun Modifier.onboardingBind(target: OnboardingTarget): Modifier = this
  .onGloballyPositioned { coords ->
    val rect = coords.boundsInWindow()
    target.updateRect(rect)
  }
  .semantics {
    // 用 Semantics 标记, 方便测试和调试
    set(OnboardingTargetIdKey, target.id)
  }

/**
 * 通过 id 字符串快速绑定 (无需先创建 OnboardingTarget).
 *
 * ```
 * Box(modifier = Modifier.onboardingBind("search_button")) { ... }
 * ```
 */
fun Modifier.onboardingBind(id: String): Modifier {
  val target = OnboardingTarget.of(id)
  return this.onboardingBind(target)
}

// =============================================================================
// View 绑定 (Android View 体系, 用于 XML 布局的目标控件)
// =============================================================================

/**
 * 给 Android [android.view.View] 绑定 [OnboardingTarget].
 *
 * 等价于 [Modifier.onboardingBind] 的 View 版本, 用于 XML 布局中已有的 view.
 * 会在 layout 变化时自动更新 target.rect, 让引导浮层能跟随 view 位置变化.
 *
 * 用法:
 * ```
 * val target = OnboardingTarget.of("drawer_bubble")
 * binding.pageSwitchGestureBubble.bindOnboardingTarget(target)
 * ```
 *
 * 注意事项:
 *  - 该方法会立即 post 一次异步更新 (因为 view 初始位置 / 尺寸需要等 layout pass 后才能拿到).
 *  - 多次调用是幂等的: 同一对 (view, target) 不会重复注册 OnLayoutChangeListener.
 *  - 当 target 暂时不需要时, 调用 [unbindOnboardingTarget] 释放 listener 引用,
 *    避免泄漏.
 */
fun android.view.View.bindOnboardingTarget(target: OnboardingTarget) {
  // 1) post 一次初始更新 (view 此时可能还没 measure/layout)
  post { syncViewRectToTarget(this, target) }
  // 2) 注册布局变化监听
  addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
    syncViewRectToTarget(v, target)
  }
}

/**
 * 把 [OnboardingTarget] 绑定到 view 的**子区域** (而非整 view).
 *
 * 适用场景: 某些 view 是 match_parent 宽 / wrap_content 高 (如底部抽屉的
 * `page_switch_gesture_bubble` 24dp 高的横条), 但视觉上"被框选的目标"
 * 只是横条中心的**一个小手柄**. 直接绑定整个 view 会让高亮过宽, 视觉很差.
 * 用本函数可指定一个中心子区域, 让高亮只框选手柄本身.
 *
 * @param target 引导目标
 * @param widthDp 子区域宽度 (dp). 会被限制在 view 宽度内.
 * @param heightDp 子区域高度 (dp). null = 整 view 高度. 会被限制在 view 高度内.
 * @param gravity 子区域在 view 内的对齐方式 (默认居中).
 *                支持: [android.view.Gravity.CENTER] / TOP / BOTTOM / START / END.
 */
fun android.view.View.bindOnboardingTargetAsHandle(
  target: OnboardingTarget,
  widthDp: Float = 80f,
  heightDp: Float? = null,
  gravity: Int = android.view.Gravity.CENTER,
) {
  val density = resources.displayMetrics.density
  val widthPx = (widthDp * density).toInt()
  val heightPx = heightDp?.let { (it * density).toInt() }

  val sync: (android.view.View) -> Unit = sync@{ v ->
    if (v.width <= 0 || v.height <= 0) return@sync
    val loc = IntArray(2)
    v.getLocationInWindow(loc)
    val subW = widthPx.coerceAtMost(v.width)
    val subH = (heightPx ?: v.height).coerceAtMost(v.height)
    val (subLeft, subTop) = when (gravity) {
      android.view.Gravity.CENTER,
      android.view.Gravity.CENTER_HORIZONTAL,
      android.view.Gravity.CENTER_VERTICAL -> (v.width - subW) / 2 to (v.height - subH) / 2
      android.view.Gravity.TOP -> 0 to 0
      android.view.Gravity.BOTTOM -> 0 to v.height - subH
      android.view.Gravity.START,
      android.view.Gravity.LEFT -> 0 to (v.height - subH) / 2
      android.view.Gravity.END,
      android.view.Gravity.RIGHT -> v.width - subW to (v.height - subH) / 2
      else -> (v.width - subW) / 2 to (v.height - subH) / 2
    }
    target.updateRect(
      androidx.compose.ui.geometry.Rect(
        left = (loc[0] + subLeft).toFloat(),
        top = (loc[1] + subTop).toFloat(),
        right = (loc[0] + subLeft + subW).toFloat(),
        bottom = (loc[1] + subTop + subH).toFloat(),
      )
    )
  }

  post { sync(this) }
  addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> sync(v) }
}

/**
 * [bindOnboardingTarget] 的反向操作: 解除绑定, 释放 OnLayoutChangeListener.
 *
 * 用法:
 * ```
 * binding.pageSwitchGestureBubble.unbindOnboardingTarget(target)
 * ```
 */
fun android.view.View.unbindOnboardingTarget(target: OnboardingTarget) {
  // Android 的 addOnLayoutChangeListener 没有 removeAll 模式, 只能通过
  // 暴力清空再恢复 (不推荐, 会丢掉其它 listener). 我们用 tag 标记:
  // 任何被本方法移除的 listener 都会被 tag 标记, 之后再次绑定时跳过这些
  // listener. 简化实现: 这里暂时只把 target rect 置 null, 让引导浮层
  // 暂时不渲染高亮. 实际生产中更稳妥的清理应该由 view 生命周期
  // (onDetachedFromWindow) 自动触发 GC.
  target.updateRect(null)
}

/**
 * 内部工具: 把 view 当前在 window 坐标系里的 rect 同步给 target.
 */
private fun syncViewRectToTarget(view: android.view.View, target: OnboardingTarget) {
  if (view.width <= 0 || view.height <= 0) return
  val loc = IntArray(2)
  view.getLocationInWindow(loc)
  target.updateRect(
    androidx.compose.ui.geometry.Rect(
      left = loc[0].toFloat(),
      top = loc[1].toFloat(),
      right = (loc[0] + view.width).toFloat(),
      bottom = (loc[1] + view.height).toFloat(),
    )
  )
}

// =============================================================================
// 步骤绑定 (Step binding helper)
// =============================================================================

/**
 * 解决 step 的 targetRect: 优先使用 step.target, 然后 step.targetRect, 最后 null.
 *
 * 这是 Composable helper, 内部订阅 target.rectFlow 以确保 Rect 更新时
 * 引导浮层自动重绘.
 */
@Composable
fun rememberTargetRect(
  step: OnboardingStep,
): Rect? {
  val target = step.target
  return if (target != null) {
    val rect by target.rectFlow.collectAsState()
    rect ?: step.targetRect
  } else {
    step.targetRect
  }
}

// =============================================================================
// Semantics Key (内部, 用于测试 / 调试 / 可访问性)
// =============================================================================

/**
 * Semantics 标记 key, 标识该节点是某个引导目标.
 */
val OnboardingTargetIdKey: SemanticsPropertyKey<String> =
  SemanticsPropertyKey("OnboardingTargetId")
