package com.zerostudio.idea.editor.core.buffer.piecetree

import com.zerostudio.idea.editor.core.buffer.model.Range
import com.zerostudio.idea.editor.core.cursor.CursorSelection
import java.util.ArrayDeque

data class UndoRedoItem(
    val forwardOperations: List<SliceEditOperation>,
    val backwardOperations: List<SliceEditOperation>,
    val cursorsBefore: List<CursorSelection>,
    val cursorsAfter: List<CursorSelection>,
    val timestamp: Long = System.currentTimeMillis()
)

class PieceTreeUndoManager(
    val pieceTree: PieceTreeCore,
    var maxStackDepth: Int = 300
) {
    private val undoStack = ArrayDeque<UndoRedoItem>()
    private val redoStack = ArrayDeque<UndoRedoItem>()
    private var isUndoingOrRedoing = false

    fun isWorking(): Boolean = isUndoingOrRedoing
    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * 压入一项原子操作
     */
    fun pushEdit(
        forward: List<SliceEditOperation>,
        reverse: List<SliceEditOperation>,
        cursorsBefore: List<CursorSelection>,
        cursorsAfter: List<CursorSelection>
    ) {
        if (isUndoingOrRedoing) return

        if (undoStack.size >= maxStackDepth) {
            undoStack.removeFirst()
        }
        undoStack.addLast(
            UndoRedoItem(
                forwardOperations = forward,
                backwardOperations = reverse,
                cursorsBefore = cursorsBefore,
                cursorsAfter = cursorsAfter
            )
        )
        redoStack.clear()
    }

    /**
     * 执行原子级撤销
     */
    fun undo(): List<CursorSelection>? {
        if (!canUndo()) return null
        isUndoingOrRedoing = true
        try {
            val item = undoStack.removeLast()
            // 逆序应用反向切片编辑
            for (op in item.backwardOperations.reversed()) {
                val startOffset = pieceTree.getOffsetAt(op.range.startLineNumber.toLong(), op.range.startColumn.toLong())
                val endOffset = pieceTree.getOffsetAt(op.range.endLineNumber.toLong(), op.range.endColumn.toLong())
                val replaceLen = endOffset - startOffset

                if (replaceLen > 0L) {
                    pieceTree.delete(startOffset, replaceLen)
                }
                op.slice?.let { slice ->
                    val textToInsert = slice.materializeText(pieceTree)
                    pieceTree.insert(startOffset, textToInsert)
                }
            }
            redoStack.addLast(item)
            return item.cursorsBefore
        } finally {
            isUndoingOrRedoing = false
        }
    }

    /**
     * 执行原子级重做
     */
    fun redo(): List<CursorSelection>? {
        if (!canRedo()) return null
        isUndoingOrRedoing = true
        try {
            val item = redoStack.removeLast()
            for (op in item.forwardOperations) {
                val startOffset = pieceTree.getOffsetAt(op.range.startLineNumber.toLong(), op.range.startColumn.toLong())
                val endOffset = pieceTree.getOffsetAt(op.range.endLineNumber.toLong(), op.range.endColumn.toLong())
                val replaceLen = endOffset - startOffset

                if (replaceLen > 0L) {
                    pieceTree.delete(startOffset, replaceLen)
                }
                op.slice?.let { slice ->
                    val textToInsert = slice.materializeText(pieceTree)
                    pieceTree.insert(startOffset, textToInsert)
                }
            }
            undoStack.addLast(item)
            return item.cursorsAfter
        } finally {
            isUndoingOrRedoing = false
        }
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}