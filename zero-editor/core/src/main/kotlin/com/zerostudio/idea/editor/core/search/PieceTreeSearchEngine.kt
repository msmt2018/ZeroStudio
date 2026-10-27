package com.zerostudio.idea.editor.core.search

import com.zerostudio.idea.editor.core.buffer.LargeTextPieceTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.regex.Pattern

data class SearchMatch(
    val globalStartOffset: Long,
    val globalEndOffset: Long,
    val line: Long,
    val previewText: String
)

data class SearchOptions(
    val query: String,
    val isRegex: Boolean = false,
    val caseSensitive: Boolean = false,
    val matchWholeWord: Boolean = false
)

object PieceTreeSearchEngine {

    private const val BUFFER_SIZE = 64 * 1024 // 64KB
    private const val OVERLAP_SIZE = 4 * 1024  // 4KB 重叠区，跨越块边界匹配

    /**
     * 针对数十亿字符的 Piece Tree 执行低内存开销的流式并发匹配
     */
    fun search(
        pieceTree: LargeTextPieceTree,
        options: SearchOptions
    ): Flow<SearchMatch> = flow {
        val totalLength = pieceTree.length()
        if (totalLength == 0L || options.query.isEmpty()) return@flow

        val pattern = if (options.isRegex) {
            val flags = if (options.caseSensitive) 0 else Pattern.CASE_INSENSITIVE
            val regexStr = if (options.matchWholeWord) "\\b${options.query}\\b" else options.query
            Pattern.compile(regexStr, flags)
        } else {
            val flags = if (options.caseSensitive) 0 else Pattern.CASE_INSENSITIVE
            val quoted = Pattern.quote(options.query)
            val regexStr = if (options.matchWholeWord) "\\b$quoted\\b" else quoted
            Pattern.compile(regexStr, flags)
        }

        var readOffset = 0L
        var currentLine = 0L
        val window = StringBuilder(BUFFER_SIZE + OVERLAP_SIZE)

        while (readOffset < totalLength) {
            currentCoroutineContext().ensureActive()

            val toRead = minOf(BUFFER_SIZE.toLong(), totalLength - readOffset).toInt()
            val chunk = CharArray(toRead)
            for (i in 0 until toRead) {
                chunk[i] = pieceTree.charAt(readOffset + i)
            }

            window.append(chunk)

            val matcher = pattern.matcher(window)
            var lastMatchEndInWindow = 0

            while (matcher.find()) {
                currentCoroutineContext().ensureActive()

                val matchStartInWindow = matcher.start()
                val matchEndInWindow = matcher.end()

                // 避免在重叠区重复发射已经被扫描过的结果
                if (matchStartInWindow >= (window.length - toRead - OVERLAP_SIZE).coerceAtLeast(0)) {
                    val actualGlobalStart = readOffset - (window.length - toRead) + matchStartInWindow
                    val actualGlobalEnd = actualGlobalStart + (matchEndInWindow - matchStartInWindow)

                    // 计算当前匹配点所在的上下文与行号
                    val preview = window.substring(
                        (matchStartInWindow - 20).coerceAtLeast(0),
                        (matchEndInWindow + 20).coerceAtMost(window.length)
                    ).replace("\n", " ")

                    emit(SearchMatch(actualGlobalStart, actualGlobalEnd, currentLine, preview))
                }
                lastMatchEndInWindow = matchEndInWindow
            }

            // 更新已处理块的行数
            for (i in 0 until toRead) {
                if (chunk[i] == '\n') currentLine++
            }

            // 保留末尾重叠部分以防长词在下一次迭代被截断
            if (window.length > OVERLAP_SIZE) {
                window.delete(0, window.length - OVERLAP_SIZE)
            }
            readOffset += toRead
        }
    }.flowOn(Dispatchers.Default)

    /**
     * 大小写保全替换算法（Preserve Case）
     * 保持替换结果与原匹配文本的大小写形式一致（全大写、全小写、首字母大写）
     */
    fun computePreserveCaseReplacement(matchedText: String, replacementTemplate: String): String {
        if (matchedText.isEmpty() || replacementTemplate.isEmpty()) return replacementTemplate

        val isAllUpper = matchedText.all { !it.isLetter() || it.isUpperCase() }
        val isAllLower = matchedText.all { !it.isLetter() || it.isLowerCase() }
        val isCapitalized = matchedText.first().isUpperCase() && matchedText.drop(1).all { !it.isLetter() || it.isLowerCase() }

        return when {
            isAllUpper -> replacementTemplate.uppercase()
            isAllLower -> replacementTemplate.lowercase()
            isCapitalized -> replacementTemplate.replaceFirstChar { it.uppercase() }
            else -> replacementTemplate
        }
    }
}