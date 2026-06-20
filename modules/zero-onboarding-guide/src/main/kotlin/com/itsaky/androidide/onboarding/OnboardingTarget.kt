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
    OnboardingTargetIdKey setValue target.id
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
