package com.itsaky.androidide.cursor

import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.Unsubscribe
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Cursor history manager.
 * Used to implement undo/redo-like cursor position revert and forward functions.
 * 
 * Workflow:
 * Listen for [SelectionChangeEvent], automatically filter cursor changes due to editing or minor shifts,
 * Record meaningful cursor jumps in the history stack for consumption by [CursorPreviousLocationAction] and [CursorNextLocationAction].
 *
 * @author android_zero
 */
object CursorHistoryManager {

    class Tracker(val editor: CodeEditor) : EventReceiver<SelectionChangeEvent> {
        val history = mutableListOf<CharPosition>()
        var currentIndex = -1
        var isNavigating = false

        init {
            editor.subscribeEvent(SelectionChangeEvent::class.java, this)
            // 初始化记录当前位置
            if (editor.text.length > 0) {
                history.add(editor.cursor.left().fromThis())
                currentIndex = 0
            }
        }

        override fun onReceive(event: SelectionChangeEvent, unsubscribe: Unsubscribe) {
            if (isNavigating) return

            // 忽略由于文本修改和未知原因造成的光标变动，保持历史记录纯净
            if (event.cause == SelectionChangeEvent.CAUSE_TEXT_MODIFICATION ||
                event.cause == SelectionChangeEvent.CAUSE_UNKNOWN) {
                return
            }

            val pos = event.left.fromThis()

            if (currentIndex >= 0 && currentIndex < history.size) {
                val last = history[currentIndex]
                // 防抖处理：如果处于同一行且移动距离小于 10 个字符，则直接覆盖上一个记录（认为是普通的方向键微调）
                if (last.line == pos.line && abs(last.column - pos.column) < 10) {
                    history[currentIndex] = pos
                    return
                }
            }

            // 如果当前在历史记录中途又发生了新跳跃，则清空前方的记录
            if (currentIndex < history.size - 1) {
                history.subList(currentIndex + 1, history.size).clear()
            }
            
            history.add(pos)
            
            // 限制最大历史记录数量，防止内存泄漏
            if (history.size > 100) {
                history.removeAt(0)
            } else {
                currentIndex++
            }
        }

        fun goBack() {
            if (currentIndex > 0) {
                currentIndex--
                navigate()
            }
        }

        fun goForward() {
            if (currentIndex < history.size - 1) {
                currentIndex++
                navigate()
            }
        }

        private fun navigate() {
            isNavigating = true
            val pos = history[currentIndex]
            if (editor.text.isValidPosition(pos)) {
                editor.setSelection(pos.line, pos.column)
                editor.ensurePositionVisible(pos.line, pos.column)
            }
            isNavigating = false
        }
        
        fun canGoBack() = currentIndex > 0
        fun canGoForward() = currentIndex < history.size - 1
    }

    private val trackers = WeakHashMap<CodeEditor, Tracker>()

    /**
     * 获取或为对应的 CodeEditor 初始化一个光标历史追踪器
     */
    fun getTracker(editor: CodeEditor): Tracker {
        var tracker = trackers[editor]
        if (tracker == null) {
            tracker = Tracker(editor)
            trackers[editor] = tracker
        }
        return tracker
    }
}