package com.zerostudio.idea.editor.core.cursor

import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.text.CharPosition

class AdvancedMultiCursorEngine(private val editor: ZeroCodeEditorView) {

    private val model: ZeroMultiCursorModel get() = editor.multiCursorModel

    /**
     * 对标 VS Code / IDEA 快捷键: Ctrl + Shift + L
     * 将当前选中的单词，全文档所有出现的地方全部加上活动光标
     */
    fun selectAllOccurrences() {
        val primary = model.primarySelection
        if (primary.isCollapsed) return

        val content = editor.text
        val query = content.subSequence(primary.start.index, primary.end.index).toString()
        val textStr = content.toString()

        val allSelections = mutableListOf<CursorSelection>()
        var searchStart = 0
        while (searchStart < textStr.length) {
            val idx = textStr.indexOf(query, searchStart)
            if (idx == -1) break

            val startPos = content.indexer.getCharPosition(idx)
            val endPos = content.indexer.getCharPosition(idx + query.length)
            allSelections.add(CursorSelection(startPos, endPos))

            searchStart = idx + query.length
        }

        if (allSelections.isNotEmpty()) {
            replaceSelectionsDirectly(allSelections)
            editor.invalidate()
        }
    }

    /**
     * 智能多行剪贴板一对一匹配粘贴
     * 如果剪贴板由 4 行构成，且当前正好有 4 个活动光标，则光标 i 粘贴第 i 行，否则广播整段粘贴
     */
    fun smartMultiLinePaste(clipboardContent: String) {
        val normalized = clipboardContent.replace("\r\n", "\n")
        val lines = normalized.split("\n")
        val selections = model.selections

        if (lines.size == selections.size && selections.size > 1) {
            // 逆序执行，防止前序插入修改后续光标位置
            val indexedSelections = selections.mapIndexed { idx, sel -> Pair(idx, sel) }
                .sortedByDescending { it.second.start.index }

            val content = editor.text
            val updatedPositions = arrayOfNulls<CursorSelection>(selections.size)

            for ((originalIdx, sel) in indexedSelections) {
                val lineToPaste = lines[originalIdx]
                if (!sel.isCollapsed) {
                    content.delete(sel.start.line, sel.start.column, sel.end.line, sel.end.column)
                }
                content.insert(sel.start.line, sel.start.column, lineToPaste)
                val newPos = content.indexer.getCharPosition(sel.start.index + lineToPaste.length)
                updatedPositions[originalIdx] = CursorSelection(newPos, newPos)
            }

            replaceSelectionsDirectly(updatedPositions.filterNotNull())
        } else {
            model.executeBatchInsert(clipboardContent)
        }
        editor.invalidate()
    }

    private fun replaceSelectionsDirectly(newSelections: List<CursorSelection>) {
        val field = ZeroMultiCursorModel::class.java.getDeclaredField("_selections")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = field.get(model) as MutableList<CursorSelection>
        list.clear()
        list.addAll(newSelections)
        model.normalizeAndMergeSelections()
    }
}