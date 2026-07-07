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

import android.view.View
import android.zero.studio.widget.editor.symbolinput.AdvancedSymbolInputView
import java.lang.ref.WeakReference

/**
 * Bug 5.1: 预览 fragment (图片/Markdown) 底部被符号输入控件遮挡。
 *
 * 进入预览 fragment 时调用 [hideForPreview] 把符号输入控件 (以及 Header 状态栏)
 * 推到屏幕外 (visibility = GONE), 只保留 EdgeSnapBubbleView 在屏幕可见区域内;
 * BottomSheet peekHeight 随之减小, 预览 fragment 底部不再被遮挡。
 *
 * 上滑时恢复符号输入控件到正常位置 ([showFromPreview]),
 * 下滑时保持屏外 ([hideForPreview] 已生效, 无需额外操作)。
 *
 * 使用 [WeakReference] 持有 view, Activity 销毁后不会泄漏。
 *
 * @author android_zero
 */
object SymbolInputVisibilityManager {

    private var symbolInputRef: WeakReference<AdvancedSymbolInputView>? = null
    private var headerContentRef: WeakReference<View>? = null
    private var headerDividerRef: WeakReference<View>? = null

    /** 隐藏前 Header 状态栏 / 分隔线的可见性, 恢复时还原。 */
    private var savedHeaderContentVisibility = View.VISIBLE
    private var savedHeaderDividerVisibility = View.VISIBLE

    /** 当前是否处于预览隐藏态。 */
    @Volatile
    private var hidden = false

    /** 预览隐藏态标记, 供 [EditorBottomSheet] 的拖拽监听器判断是否拦截手势。 */
    val previewHidden: Boolean
        get() = hidden

    /**
     * 注册符号输入控件及相关 Header 视图。
     *
     * 应在 [com.itsaky.androidide.activities.editor.BaseEditorActivity] 初始化
     * BottomSheet 后调用。
     */
    fun register(
        symbolInputView: AdvancedSymbolInputView,
        headerContentWrapper: View?,
        headerDivider: View?,
    ) {
        symbolInputRef = WeakReference(symbolInputView)
        headerContentRef = headerContentWrapper?.let { WeakReference(it) }
        headerDividerRef = headerDivider?.let { WeakReference(it) }
    }

    /** 注销, 恢复可见性后清空引用。 */
    fun unregister() {
        showFromPreview()
        symbolInputRef = null
        headerContentRef = null
        headerDividerRef = null
    }

    /**
     * 隐藏符号输入控件 + Header 状态栏 + 分隔线, 只保留 EdgeSnapBubbleView。
     *
     * 幂等: 重复调用不会重复保存状态。
     */
    fun hideForPreview() {
        if (hidden) return
        hidden = true
        symbolInputRef?.get()?.hideForPreview()
        headerContentRef?.get()?.let {
            savedHeaderContentVisibility = it.visibility
            if (it.visibility != View.GONE) it.visibility = View.GONE
        }
        headerDividerRef?.get()?.let {
            savedHeaderDividerVisibility = it.visibility
            if (it.visibility != View.GONE) it.visibility = View.GONE
        }
    }

    /**
     * 恢复符号输入控件 + Header 状态栏 + 分隔线到隐藏前的可见性。
     *
     * 幂等: 未处于隐藏态时为空操作。
     */
    fun showFromPreview() {
        if (!hidden) return
        hidden = false
        symbolInputRef?.get()?.showFromPreview()
        headerContentRef?.get()?.let {
            if (it.visibility != savedHeaderContentVisibility) {
                it.visibility = savedHeaderContentVisibility
            }
        }
        headerDividerRef?.get()?.let {
            if (it.visibility != savedHeaderDividerVisibility) {
                it.visibility = savedHeaderDividerVisibility
            }
        }
    }
}
