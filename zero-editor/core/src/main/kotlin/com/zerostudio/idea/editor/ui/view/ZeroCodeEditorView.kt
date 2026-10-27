package com.zerostudio.idea.editor.ui.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import com.zerostudio.idea.editor.core.buffer.LargeTextPieceTree
import com.zerostudio.idea.editor.core.buffer.VirtualPageManager
import com.zerostudio.idea.editor.core.buffer.ZeroPageScrollSynchronizer
import com.zerostudio.idea.editor.core.cursor.ZeroMultiCursorNavigator
import com.zerostudio.idea.editor.ui.magnifier.ZeroEditorMagnifier

open class ZeroCodeEditorViewEnhanced @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ZeroCodeEditorView(context, attrs, defStyleAttr) {

    val multiCursorNavigator by lazy { ZeroMultiCursorNavigator(this) }
    private val magnifierController by lazy { ZeroEditorMagnifier(this) }
    private var pageSynchronizer: ZeroPageScrollSynchronizer? = null

    var isMagnifierFeatureEnabled = true

    override fun bindPieceTreeStorage(pieceTree: LargeTextPieceTree, paginationThreshold: Long) {
        super.bindPieceTreeStorage(pieceTree, paginationThreshold)
        val manager = VirtualPageManager(pieceTree, paginationThreshold)
        pageSynchronizer = ZeroPageScrollSynchronizer(this, manager).apply {
            attach()
        }
    }

    /**
     * 拦截键盘事件优先派发给多光标全向导航
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (multiCursorNavigator.dispatchKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * 拦截触控手势同步放大镜并驱动无感滚动翻页
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isMagnifierFeatureEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (cursor.isSelected) {
                        magnifierController.showOrMove(event.x, event.y)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    magnifierController.dismiss()
                }
            }
        }
        val handled = super.onTouchEvent(event)
        pageSynchronizer?.checkAndTriggerPageSwap()
        return handled
    }

    /**
     * 接管智能粘贴
     */
    override fun pasteFromClipboardToMultiCursor() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        multiCursorNavigator.executeSmartPaste(clip)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        magnifierController.dismiss()
        pageSynchronizer?.detach()
    }
}