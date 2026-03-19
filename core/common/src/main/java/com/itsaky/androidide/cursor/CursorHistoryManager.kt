// by android_zero
package com.itsaky.androidide.cursor

import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.Unsubscribe
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * 光标历史记录管理器。
 * 用于实现类似撤销/重做的光标位置回退与前进功能。
 * @author android_zero
 */
object CursorHistoryManager {

    class Tracker(val editor: CodeEditor) : EventReceiver<SelectionChangeEvent> {
        val history = mutableListOf<CharPosition>()
        var currentIndex = -1
        var isNavigating = false

        init {
            editor.subscribeEvent(SelectionChangeEvent::class.java, this)
            if (editor.text.length > 0) {
                history.add(editor.cursor.left().fromThis())
                currentIndex = 0
            }
        }

        override fun onReceive(event: SelectionChangeEvent, unsubscribe: Unsubscribe) {
            if (isNavigating) return
            if (event.cause == SelectionChangeEvent.CAUSE_TEXT_MODIFICATION ||
                event.cause == SelectionChangeEvent.CAUSE_UNKNOWN) {
                return
            }

            val pos = event.left.fromThis()

            if (currentIndex >= 0 && currentIndex < history.size) {
                val last = history[currentIndex]
                if (last.line == pos.line && abs(last.column - pos.column) < 10) {
                    history[currentIndex] = pos
                    return
                }
            }

            if (currentIndex < history.size - 1) {
                history.subList(currentIndex + 1, history.size).clear()
            }
            
            history.add(pos)
            
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

    fun getTracker(editor: CodeEditor): Tracker {
        var tracker = trackers[editor]
        if (tracker == null) {
            tracker = Tracker(editor)
            trackers[editor] = tracker
        }
        return tracker
    }
}