package com.zerostudio.idea.editor.core.buffer.piecetree

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.regex.Pattern

data class SearchMatch(
    val startOffset: Long,
    val endOffset: Long,
    val matchedContent: String
)

data class SearchOptions(
    val query: String,
    val isRegex: Boolean = false,
    val caseSensitive: Boolean = false,
    val matchWholeWord: Boolean = false
)

object PieceTreeSearchEngine {

    private const val CHUNK_SIZE = 64 * 1024 // 64KB 滑动分析块
    private const val OVERLAP_SIZE = 4 * 1024 // 4KB 重叠安全区

    /**
     * 64位流式查找全部匹配项，背压发射至 Flow
     */
    fun search(tree: PieceTreeCore, options: SearchOptions): Flow<SearchMatch> = flow {
        val totalLength = tree.getLength()
        if (totalLength == 0L || options.query.isEmpty()) return@flow

        if (!options.isRegex) {
            // 执行硬件级纯文本 Boyer-Moore-Horspool 检索
            searchBoyerMoore(tree, options, this@flow)
        } else {
            // 执行跨块滑动正则匹配
            searchRegexOverlapping(tree, options, this@flow)
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Boyer-Moore-Horspool 算法：O(N / M) 极速跳跃查找
     */
    private suspend fun searchBoyerMoore(
        tree: PieceTreeCore,
        options: SearchOptions,
        collector: kotlinx.coroutines.flow.FlowCollector<SearchMatch>
    ) {
        val totalLength = tree.getLength()
        val query = if (options.caseSensitive) options.query else options.query.lowercase()
        val m = query.length
        val badCharShift = IntArray(256) { m }

        for (i in 0 until m - 1) {
            val c = query[i].code and 0xFF
            badCharShift[c] = m - 1 - i
        }

        var offset = 0L
        while (offset <= totalLength - m) {
            currentCoroutineContext().ensureActive()

            var j = m - 1
            while (j >= 0) {
                var docChar = tree.charAt(offset + j)
                if (!options.caseSensitive) docChar = docChar.lowercaseChar()

                if (docChar != query[j]) {
                    break
                }
                j--
            }

            if (j < 0) {
                // 命中匹配
                var isWordValid = true
                if (options.matchWholeWord) {
                    val prevChar = if (offset > 0L) tree.charAt(offset - 1L) else ' '
                    val nextChar = if (offset + m < totalLength) tree.charAt(offset + m) else ' '
                    isWordValid = !Character.isJavaIdentifierPart(prevChar) && !Character.isJavaIdentifierPart(nextChar)
                }

                if (isWordValid) {
                    collector.emit(SearchMatch(offset, offset + m, options.query))
                }
                offset += m.toLong()
            } else {
                val lastChar = tree.charAt(offset + m - 1)
                val shift = badCharShift[lastChar.code and 0xFF]
                offset += shift.toLong().coerceAtLeast(1L)
            }
        }
    }

    private suspend fun searchRegexOverlapping(
        tree: PieceTreeCore,
        options: SearchOptions,
        collector: kotlinx.coroutines.flow.FlowCollector<SearchMatch>
    ) {
        val totalLength = tree.getLength()
        val flags = if (options.caseSensitive) 0 else Pattern.CASE_INSENSITIVE
        val regexStr = if (options.matchWholeWord) "\\b${options.query}\\b" else options.query
        val pattern = Pattern.compile(regexStr, flags)

        val window = StringBuilder(CHUNK_SIZE + OVERLAP_SIZE)
        var cursor = 0L

        while (cursor < totalLength) {
            currentCoroutineContext().ensureActive()

            val toRead = minOf(CHUNK_SIZE.toLong(), totalLength - cursor).toInt()
            val chunk = CharArray(toRead)
            for (i in 0 until toRead) {
                chunk[i] = tree.charAt(cursor + i)
            }
            window.append(chunk)

            val matcher = pattern.matcher(window)
            while (matcher.find()) {
                currentCoroutineContext().ensureActive()
                val matchStartInWin = matcher.start()
                val matchEndInWin = matcher.end()

                // 排除重叠区重复发射已判定的结果
                if (matchStartInWin >= (window.length - toRead - OVERLAP_SIZE).coerceAtLeast(0)) {
                    val actualGlobalStart = cursor - (window.length - toRead) + matchStartInWin
                    val actualGlobalEnd = actualGlobalStart + (matchEndInWin - matchStartInWin)
                    val text = window.substring(matchStartInWin, matchEndInWin)

                    collector.emit(SearchMatch(actualGlobalStart, actualGlobalEnd, text))
                }
            }

            if (window.length > OVERLAP_SIZE) {
                window.delete(0, window.length - OVERLAP_SIZE)
            }
            cursor += toRead
        }
    }

    /**
     * 大小写保全替换算法 (Preserve Case Replace)
     * e.g. "FOO" 替换为 "BAR", "foo" 替换为 "bar", "Foo" 替换为 "Bar"
     */
    fun computePreserveCaseReplacement(matched: String, replacement: String): String {
        if (matched.isEmpty() || replacement.isEmpty()) return replacement

        val isAllUpper = matched.all { !it.isLetter() || it.isUpperCase() }
        val isAllLower = matched.all { !it.isLetter() || it.isLowerCase() }
        val isCapitalized = matched.first().isUpperCase() && matched.drop(1).all { !it.isLetter() || it.isLowerCase() }

        return when {
            isAllUpper -> replacement.uppercase()
            isAllLower -> replacement.lowercase()
            isCapitalized -> replacement.replaceFirstChar { it.uppercase() }
            else -> replacement
        }
    }
}