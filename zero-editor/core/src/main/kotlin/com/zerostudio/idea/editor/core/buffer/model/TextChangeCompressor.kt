package com.zerostudio.idea.editor.core.buffer.model

data class SingleTextChange(
    val oldOffset: Long,
    val oldText: String,
    val newOffset: Long,
    val newText: String
) {
    val oldEnd: Long get() = oldOffset + oldText.length
    val newEnd: Long get() = newOffset + newText.length
}

object TextChangeCompressor {

    /**
     * 合并前后两次连续变动 (Prev 和 Curr)，如果两者逻辑连续（如连续敲击字符），则熔断重组
     */
    fun compress(prev: SingleTextChange, curr: SingleTextChange): SingleTextChange? {
        // 场景 1：连续追加键入 (e.g. "a" -> "ab")
        if (prev.newEnd == curr.newOffset && curr.oldText.isEmpty()) {
            return SingleTextChange(
                oldOffset = prev.oldOffset,
                oldText = prev.oldText,
                newOffset = prev.newOffset,
                newText = prev.newText + curr.newText
            )
        }

        // 场景 2：连续退格删除 (Backspace)
        if (curr.newEnd == prev.newOffset && curr.newText.isEmpty() && prev.oldText.isEmpty()) {
            return SingleTextChange(
                oldOffset = curr.oldOffset,
                oldText = curr.oldText + prev.newText,
                newOffset = curr.newOffset,
                newText = ""
            )
        }

        return null // 不连续，无法压缩为单事务
    }
}