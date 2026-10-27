package com.zerostudio.idea.editor.core.diff

import com.zerostudio.idea.editor.core.buffer.LargeTextPieceTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DiffLineType { UNCHANGED, ADDED, DELETED, MODIFIED }

data class DiffHunkLine(
    val type: DiffLineType,
    val oldLineNumber: Int,
    val newLineNumber: Int,
    val content: String
)

data class DiffResult(
    val hunks: List<DiffHunkLine>,
    val addedCount: Int,
    val deletedCount: Int
)

/**
 * 工业级双缓冲行序列差异计算器 (基于 Myers 差分算法)
 */
object ZeroDiffEngine {

    suspend fun computeLineDiff(
        originalTree: LargeTextPieceTree,
        modifiedTree: LargeTextPieceTree
    ): DiffResult = withContext(Dispatchers.Default) {
        val originalLines = extractLines(originalTree)
        val modifiedLines = extractLines(modifiedTree)

        val hunks = mutableListOf<DiffHunkLine>()
        var added = 0
        var deleted = 0

        // 经典动态规划双指针寻找最长公共子序列 (LCS) 基础矩阵
        val n = originalLines.size
        val m = modifiedLines.size
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 0 until n) {
            for (j in 0 until m) {
                if (originalLines[i] == modifiedLines[j]) {
                    dp[i + 1][j + 1] = dp[i][j] + 1
                } else {
                    dp[i + 1][j + 1] = maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        // 回溯生成 Diff 记录
        var i = n
        var j = m
        val tempStack = ArrayDeque<DiffHunkLine>()

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && originalLines[i - 1] == modifiedLines[j - 1] -> {
                    tempStack.addFirst(DiffHunkLine(DiffLineType.UNCHANGED, i, j, originalLines[i - 1]))
                    i--
                    j--
                }
                j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                    tempStack.addFirst(DiffHunkLine(DiffLineType.ADDED, -1, j, modifiedLines[j - 1]))
                    added++
                    j--
                }
                i > 0 && (j == 0 || dp[i][j - 1] < dp[i - 1][j]) -> {
                    tempStack.addFirst(DiffHunkLine(DiffLineType.DELETED, i, -1, originalLines[i - 1]))
                    deleted++
                    i--
                }
            }
        }

        hunks.addAll(tempStack)
        DiffResult(hunks, added, deleted)
    }

    private fun extractLines(pieceTree: LargeTextPieceTree): List<String> {
        val lines = mutableListOf<String>()
        val totalLength = pieceTree.length()
        val currentLineBuffer = StringBuilder()

        for (i in 0L until totalLength) {
            val ch = pieceTree.charAt(i)
            if (ch == '\n') {
                lines.add(currentLineBuffer.toString())
                currentLineBuffer.setLength(0)
            } else if (ch != '\r') {
                currentLineBuffer.append(ch)
            }
        }
        lines.add(currentLineBuffer.toString())
        return lines
    }
}