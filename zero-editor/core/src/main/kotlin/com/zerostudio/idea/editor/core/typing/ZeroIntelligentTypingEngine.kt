package com.zerostudio.idea.editor.core.typing

import android.view.KeyEvent
import com.zerostudio.idea.editor.ui.view.VirtualCodeEditorView
import io.github.rosemoe.sora.text.Content

/**
 * 工业级按键辅助与连续排版智能中枢
 */
object ZeroIntelligentTypingEngine {

    private val HTML_OPEN_TAG_REGEX = Regex("<([_a-zA-Z][a-zA-Z0-9:\\-_.]*)(?:\\s+[^<>]*?[^\\s/<>=]+?)*?\\s?(>)$")
    private val MD_LIST_UNORDERED_REGEX = Regex("^(\\s*)([-*+])(\\s+)(\\[[ xX]\\]\\s+)?(.*)$")
    private val MD_LIST_ORDERED_REGEX = Regex("^(\\s*)(\\d+)(\\.)(\\s+)(\\[[ xX]\\]\\s+)?(.*)$")
    private val MD_QUOTE_REGEX = Regex("^(\\s*>\\s*)(.*)$")

    private val SELF_CLOSING_HTML_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    /**
     * 智能 Tag 标签自动闭合检测 (在用户打出 '>' 瞬间触发)
     */
    fun onTagCloseTriggered(editor: VirtualCodeEditorView) {
        val cursor = editor.cursor
        if (cursor.isSelected) return

        val line = cursor.leftLine
        val col = cursor.leftColumn
        val lineText = editor.text.getLine(line)
        val textBeforeCursor = lineText.substring(0, col)

        val match = HTML_OPEN_TAG_REGEX.find(textBeforeCursor) ?: return
        val tagName = match.groupValues[1]

        if (tagName.lowercase() !in SELF_CLOSING_HTML_TAGS) {
            val closeTag = "</$tagName>"
            editor.text.insert(line, col, closeTag)
            // 维持光标停留在开闭标签的正中间
            editor.setSelection(line, col)
        }
    }

    /**
     * 回车换行智能延续 (支持 Markdown 任务列表、编号自增与自动对齐)
     */
    fun onEnterPressed(editor: VirtualCodeEditorView): Boolean {
        val cursor = editor.cursor
        if (cursor.isSelected) return false

        val line = cursor.leftLine
        val col = cursor.leftColumn
        val lineText = editor.text.getLine(line).toString()
        val textBeforeCursor = lineText.substring(0, col)

        // 1. Markdown 引用智能延续 (> )
        val quoteMatch = MD_QUOTE_REGEX.matchEntire(textBeforeCursor)
        if (quoteMatch != null) {
            val prefix = quoteMatch.groupValues[1]
            val content = quoteMatch.groupValues[2]
            if (content.isBlank()) {
                // 如果是连续两个回车，则清空上一行前缀并退出引用
                editor.text.delete(line, 0, line, col)
            } else {
                editor.commitText("\n$prefix")
            }
            return true
        }

        // 2. Markdown 无序列表延续 (- / * / + / - [ ])
        val ulMatch = MD_LIST_UNORDERED_REGEX.matchEntire(textBeforeCursor)
        if (ulMatch != null) {
            val indent = ulMatch.groupValues[1]
            val bullet = ulMatch.groupValues[2]
            val space = ulMatch.groupValues[3]
            val checkbox = ulMatch.groupValues[4]
            val content = ulMatch.groupValues[5]

            if (content.isBlank()) {
                editor.text.delete(line, 0, line, col)
            } else {
                val nextCheckbox = if (checkbox.isNotEmpty()) "[ ] " else ""
                editor.commitText("\n$indent$bullet$space$nextCheckbox")
            }
            return true
        }

        // Markdown 有序列表智能自增编号 (1. 2. 3.)
        val olMatch = MD_LIST_ORDERED_REGEX.matchEntire(textBeforeCursor)
        if (olMatch != null) {
            val indent = olMatch.groupValues[1]
            val number = olMatch.groupValues[2].toIntOrNull() ?: 1
            val dot = olMatch.groupValues[3]
            val space = olMatch.groupValues[4]
            val checkbox = olMatch.groupValues[5]
            val content = olMatch.groupValues[6]

            if (content.isBlank()) {
                editor.text.delete(line, 0, line, col)
            } else {
                val nextCheckbox = if (checkbox.isNotEmpty()) "[ ] " else ""
                editor.commitText("\n$indent${number + 1}$dot$space$nextCheckbox")
            }
            return true
        }

        return false
    }
}