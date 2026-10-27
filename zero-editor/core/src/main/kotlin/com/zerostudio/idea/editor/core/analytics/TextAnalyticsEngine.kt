package com.zerostudio.idea.editor.core.analytics

import com.zerostudio.idea.editor.core.buffer.LargeTextPieceTree
import com.zerostudio.idea.editor.core.config.TextAnalyticsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TextAnalyticsEngine {

    /**
     * 流式扫描 Piece Tree，支持 10 亿级字符分析，内存常驻 < 1MB
     */
    suspend fun analyze(pieceTree: LargeTextPieceTree): TextAnalyticsResult = withContext(Dispatchers.Default) {
        val totalLength = pieceTree.length()
        var byteCount = 0L
        var wordCount = 0L
        var cjkCount = 0L
        var lineCount = 1L
        
        var inWord = false
        val bufferChunkSize = 65536 // 64KB 局部虚拟滑动窗口
        val chunk = CharArray(bufferChunkSize)
        
        var readOffset = 0L
        while (readOffset < totalLength) {
            val toRead = minOf(bufferChunkSize.toLong(), totalLength - readOffset).toInt()
            for (i in 0 until toRead) {
                chunk[i] = pieceTree.charAt(readOffset + i)
            }
            
            for (i in 0 until toRead) {
                val ch = chunk[i]
                
                // 统计字节数 (按 UTF-8 规则)
                byteCount += when {
                    ch.code <= 0x7F -> 1
                    ch.code <= 0x7FF -> 2
                    Character.isHighSurrogate(ch) -> 2 // 高低代理对合并按 4 字节计算
                    else -> 3
                }
                
                // 行数累加
                if (ch == '\n') {
                    lineCount++
                }

                // CJK 统一表意符号区间统计 (中文/日韩)
                if (isCJKCharacter(ch)) {
                    cjkCount++
                }

                // 词频边界判定
                if (Character.isLetterOrDigit(ch)) {
                    if (!inWord) {
                        wordCount++
                        inWord = true
                    }
                } else {
                    inWord = false
                }
            }
            readOffset += toRead
        }

        TextAnalyticsResult(
            byteCount = byteCount,
            charCount = totalLength,
            wordCount = wordCount,
            cjkCharCount = cjkCount,
            totalLines = lineCount
        )
    }

    private fun isCJKCharacter(c: Char): Boolean {
        val ub = Character.UnicodeBlock.of(c)
        return ub === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
               ub === Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
               ub === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
               ub === Character.UnicodeBlock.GENERAL_PUNCTUATION ||
               ub === Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
    }
}