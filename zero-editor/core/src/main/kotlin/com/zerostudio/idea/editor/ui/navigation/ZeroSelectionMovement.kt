package com.zerostudio.idea.editor.ui.navigation

import com.zerostudio.idea.editor.ui.view.VirtualCodeEditorView
import io.github.rosemoe.sora.text.CharPosition

object ZeroSelectionMovement {

    /**
     * 增强型 Home 键逻辑：智能在【行首第一个非空格字符】与【绝对物理行首】之间翻转
     */
    fun moveHomeEnhanced(editor: VirtualCodeEditorView, select: Boolean) {
        val text = editor.text
        val cursor = editor.cursor
        val curLine = cursor.leftLine
        val curCol = cursor.leftColumn

        val lineText = text.getLine(curLine)
        var firstNonWs = 0
        while (firstNonWs < lineText.length && lineText[firstNonWs].isWhitespace()) {
            firstNonWs++
        }

        // 如果已经在缩进首字符，则回滚到绝对物理行首 (col=0)；否则优先定位到缩进处
        val targetCol = if (curCol == firstNonWs) 0 else firstNonWs

        if (select) {
            val anchor = cursor.right()
            editor.setSelectionRegion(curLine, targetCol, anchor.line, anchor.column)
        } else {
            editor.setSelection(curLine, targetCol)
        }
        editor.ensurePositionVisible(curLine, targetCol)
    }

    /**
     * 智能驼峰 (camelCase) 与下划线 (snake_case) 边界向前跳步
     */
    fun findPreviousWordBoundary(text: CharSequence, offset: Int): Int {
        if (offset <= 0) return 0
        var i = offset - 1

        // 先跳过连续空白符
        while (i > 0 && text[i].isWhitespace()) {
            i--
        }

        val isAlphaNumeric = text[i].isLetterOrDigit()
        while (i > 0) {
            val prevChar = text[i - 1]
            val currChar = text[i]

            if (!currChar.isLetterOrDigit() && isAlphaNumeric) {
                break
            }
            if (currChar == '_' || prevChar == '_') {
                break
            }
            // 驼峰大写跳步: e.g. parse[A]rgs
            if (prevChar.isLowerCase() && currChar.isUpperCase()) {
                break
            }
            i--
        }
        return i
    }

    /**
     * 智能驼峰与下划线边界向后跳步
     */
    fun findNextWordBoundary(text: CharSequence, offset: Int): Int {
        val len = text.length
        if (offset >= len) return len
        var i = offset

        while (i < len && text[i].isWhitespace()) {
            i++
        }

        while (i < len - 1) {
            val currChar = text[i]
            val nextChar = text[i + 1]

            if (!currChar.isLetterOrDigit()) {
                i++
                break
            }
            if (currChar == '_' || nextChar == '_') {
                i++
                break
            }
            if (currChar.isLowerCase() && nextChar.isUpperCase()) {
                i++
                break
            }
            i++
        }
        return i
    }
}