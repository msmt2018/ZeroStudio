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

package com.itsaky.androidide.actions.editor

import android.content.Context
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.BaseEditorAction
import com.itsaky.androidide.actions.ActionStyle
import io.github.rosemoe.sora.event.ClickEvent
import io.github.rosemoe.sora.event.DoubleClickEvent
import io.github.rosemoe.sora.event.LongPressEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import java.util.WeakHashMap

/** 
 * 全选文本 Action (自定义实现)
 *
 * @author android_zero 
 */
class SelectAllAction(context: Context, override val order: Int) : BaseEditorAction() {

    init {
        label = context.getString(android.R.string.selectAll)
        icon = null
        style = ActionStyle(
            textSizeSp = 10f,
            paddingHorizontalDp = 2
        )
    }

    override val id: String = "ide.editor.code.text.selectAll"

    override fun prepare(data: ActionData) {
        super.prepare(data)
        getEditor(data)?.let { optimizeEditorSelection(it) }
    }

    override suspend fun execAction(data: ActionData): Boolean {
        val editor = getEditor(data) ?: return false
        
        optimizeEditorSelection(editor)

        customSelectAll(editor)
        
        return true
    }

    /**
     * 自定义的 Select All 实现
     */
    private fun customSelectAll(editor: CodeEditor) {
        val text = editor.text
        val lineCount = text.lineCount
        if (lineCount == 0) return

        val lastLine = lineCount - 1
        val lastColumn = text.getColumnCount(lastLine)
        val cursor = editor.cursor

        if (cursor.isSelected &&
            cursor.left().line == 0 && cursor.left().column == 0 &&
            cursor.right().line == lastLine && cursor.right().column == lastColumn
        ) {
            return
        }

        // 调用底层 setSelectionRegion 实现精确全选
        editor.setSelectionRegion(
            0, 0,
            lastLine, lastColumn,
            false, // makeRightVisible = false，全选时不需要强制滚动到底部
            SelectionChangeEvent.CAUSE_UNKNOWN
        )
    }

    override fun dismissOnAction() = false

    companion object {
        // 使用 WeakHashMap 记录已注入优化的 Editor 实例，防止重复挂载事件导致 OOM 或逻辑重复
        private val optimizedEditors = WeakHashMap<CodeEditor, Boolean>()
        private fun optimizeEditorSelection(editor: CodeEditor) {
            if (optimizedEditors[editor] == true) return
            optimizedEditors[editor] = true
            
            // 关闭按字符吸附
            editor.isStickyTextSelection = false
            
            val props = editor.props
            // 允许长按后直接拖动进行选择扩展
            props.dragSelectAfterLongPress = true
            // 长按时重新精准选取，避免手柄乱飘
            props.reselectOnLongPress = true
            // 允许在边缘进行滑动Fling加速
            props.scrollFling = true
            
            // 拦截单击事件
            editor.subscribeEvent(ClickEvent::class.java) { event, _ ->
                if (editor.cursor.isSelected) {
                    val clickIdx = event.charPosition.index
                    val leftIdx = editor.cursor.left().index
                    val rightIdx = editor.cursor.right().index
                    // 点击在选中区域内 -> 拦截事件
                    if (clickIdx in leftIdx..rightIdx) {
                        event.intercept()
                    }
                    // 点击在区域外 -> 不拦截，交由 sora-editor 原生逻辑处理并取消选中
                }
            }

            // 拦截双击事件
            editor.subscribeEvent(DoubleClickEvent::class.java) { event, _ ->
                if (editor.cursor.isSelected) {
                    val clickIdx = event.charPosition.index
                    val leftIdx = editor.cursor.left().index
                    val rightIdx = editor.cursor.right().index
                    if (clickIdx in leftIdx..rightIdx) {
                        event.intercept()
                    }
                }
            }

            // 拦截长按事件
            editor.subscribeEvent(LongPressEvent::class.java) { event, _ ->
                if (editor.cursor.isSelected) {
                    val clickIdx = event.charPosition.index
                    val leftIdx = editor.cursor.left().index
                    val rightIdx = editor.cursor.right().index
                    if (clickIdx in leftIdx..rightIdx) {
                        event.intercept()
                    }
                }
            }
        }
    }
}