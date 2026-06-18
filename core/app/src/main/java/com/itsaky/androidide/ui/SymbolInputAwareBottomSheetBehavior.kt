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

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * 自定义 BottomSheetBehavior, 让其不再拦截落在
 * [com.itsaky.androidide.widget.editor.symbolinput.AdvancedSymbolInputView]
 * 上的触摸事件, 以避免 IDE 抽屉抢走符号栏的内部展开/折叠手势.
 *
 * # 设计动机
 * 标准的 BottomSheetBehavior 借助 CoordinatorLayout.Behavior.onInterceptTouchEvent
 * 决定是否拦截向下滑动手势. 拦截优先级如下:
 *   CoordinatorLayout.onInterceptTouchEvent
 *     └─ BottomSheetBehavior.onInterceptTouchEvent  ← 提前决定
 *     └─ ... 内部 ViewGroup ...
 *     └─ AdvancedSymbolInputView.onInterceptTouchEvent  ← 永远到不了
 *
 * 即便 AdvancedSymbolInputView 自己通过 requestDisallowInterceptTouchEvent(true)
 * 想要阻止, 该 flag 只在自身 onInterceptTouchEvent 被调用后才能生效,
 * 而此时 BottomSheetBehavior 已经抢先拦截.
 *
 * # 解决方案
 * 重写 BottomSheetBehavior.onInterceptTouchEvent, 在内部检查 touch 是否落在
 * 外部设置的"排除区域" (这里即 AdvancedSymbolInputView 的屏幕坐标范围).
 * 如果在排除区域, 直接 return false, 不拦截, 让 AdvancedSymbolInputView
 * 的 onInterceptTouchEvent 接管, 自己的内部展开/折叠抽屉逻辑才有机会触发.
 *
 * @author android_zero
 */
class SymbolInputAwareBottomSheetBehavior<V : View>(
    context: Context,
    attrs: AttributeSet? = null,
) : BottomSheetBehavior<V>(context, attrs) {

  /**
   * 由 EditorBottomSheet 在 onAttachedToWindow 时设置.
   * 返回 true 表示"这个 MotionEvent 落在我们不想被拦截的视图上".
   *
   * 注意: 这里使用 raw 坐标 (event.rawX/rawY) 与 AdvancedSymbolInputView
   * 的 getLocationOnScreen() 比较, 二者都是相对屏幕坐标, 可以直接比较.
   */
  var isEventOnExcludedArea: ((MotionEvent) -> Boolean)? = null

  override fun onInterceptTouchEvent(
      parent: CoordinatorLayout,
      child: V,
      event: MotionEvent,
  ): Boolean {
    // 检查 touch 是否落在排除区域 (例如 AdvancedSymbolInputView 范围).
    // 如果是, 立即返回 false, 不拦截, 让子 View 的 onInterceptTouchEvent 接管.
    val excluded = isEventOnExcludedArea?.invoke(event) == true
    if (excluded) {
      return false
    }
    return super.onInterceptTouchEvent(parent, child, event)
  }
}
