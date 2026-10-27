package com.zerostudio.idea.editor.core.snippet

import com.zerostudio.idea.editor.ui.view.VirtualCodeEditorView
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.TextRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TabStopGroup(
    val id: Int,
    val ranges: MutableList<TextRange>,
    val defaultValue: String = "",
    val choices: List<String> = emptyList()
)

/**
 * 工业级 Snippet 状态机与光标跳转控制器
 */
class ZeroSnippetController(private val editor: VirtualCodeEditorView) {

    private val tabStopGroups = mutableMapOf<Int, TabStopGroup>()
    private val tabOrder = mutableListOf<Int>()
    private var currentOrderIndex = -1

    val isInSnippet: Boolean
        get() = currentOrderIndex != -1 && tabOrder.isNotEmpty()

    /**
     * 变量动态展开器 (例如 $TM_FILENAME, $CURRENT_YEAR)
     */
    fun resolveVariable(name: String, fileName: String = "Untitled"): String {
        return when (name) {
            "TM_FILENAME" -> fileName
            "TM_FILENAME_BASE" -> fileName.substringBeforeLast(".")
            "CURRENT_YEAR" -> SimpleDateFormat("yyyy", Locale.US).format(Date())
            "CURRENT_MONTH" -> SimpleDateFormat("MM", Locale.US).format(Date())
            "CURRENT_DATE" -> SimpleDateFormat("dd", Locale.US).format(Date())
            "CURRENT_HOUR" -> SimpleDateFormat("HH", Locale.US).format(Date())
            "CURRENT_MINUTE" -> SimpleDateFormat("mm", Locale.US).format(Date())
            else -> ""
        }
    }

    /**
     * 解析并插入带有占位符的 Snippet 模板，如:
     * for (int ${1:i} = 0; $1 < ${2:count}; $1++) {\n\t$0\n}
     */
    fun startSnippet(rawTemplate: String, insertOffset: Int, fileName: String = "Untitled") {
        tabStopGroups.clear()
        tabOrder.clear()
        currentOrderIndex = -1

        val parsedBuffer = StringBuilder()
        var i = 0
        val len = rawTemplate.length

        // 正则解析状态机展开变量与 TabStop
        while (i < len) {
            val ch = rawTemplate[i]
            if (ch == '$' && i + 1 < len) {
                val nextCh = rawTemplate[i + 1]
                if (nextCh.isDigit()) {
                    // 形如 $1, $2, $0
                    val stopId = nextCh.digitToInt()
                    val startPos = insertOffset + parsedBuffer.length
                    val group = tabStopGroups.getOrPut(stopId) { TabStopGroup(stopId, mutableListOf()) }
                    group.ranges.add(TextRange(CharPosition(0, startPos), CharPosition(0, startPos)))
                    i += 2
                    continue
                } else if (nextCh == '{') {
                    // 形如 ${1:default} 或 ${TM_FILENAME}
                    val closeIndex = rawTemplate.indexOf('}', i + 2)
                    if (closeIndex != -1) {
                        val body = rawTemplate.substring(i + 2, closeIndex)
                        if (body.contains(':')) {
                            val parts = body.split(':', limit = 2)
                            val stopId = parts[0].toIntOrNull() ?: 1
                            val defaultText = parts[1]
                            val startPos = insertOffset + parsedBuffer.length
                            parsedBuffer.append(defaultText)
                            val endPos = insertOffset + parsedBuffer.length

                            val group = tabStopGroups.getOrPut(stopId) { TabStopGroup(stopId, mutableListOf(), defaultText) }
                            group.ranges.add(TextRange(CharPosition(0, startPos), CharPosition(0, endPos)))
                        } else {
                            // 系统变量展开
                            val varValue = resolveVariable(body, fileName)
                            parsedBuffer.append(varValue)
                        }
                        i = closeIndex + 1
                        continue
                    }
                }
            }
            parsedBuffer.append(ch)
            i++
        }

        // 插入文本至当前物理编辑器
        val insertPos = editor.text.indexer.getCharPosition(insertOffset)
        editor.text.insert(insertPos.line, insertPos.column, parsedBuffer.toString())

        // 构建排序链表：1, 2, 3 ... 最后是 0
        tabOrder.addAll(tabStopGroups.keys.filter { it != 0 }.sorted())
        if (tabStopGroups.containsKey(0)) {
            tabOrder.add(0)
        }

        if (tabOrder.isNotEmpty()) {
            shiftToNextTabStop()
        }
    }

    /**
     * 跳转至下一个占位符位置并选中默认值
     */
    fun shiftToNextTabStop() {
        if (tabOrder.isEmpty()) return
        currentOrderIndex = (currentOrderIndex + 1).coerceAtMost(tabOrder.size - 1)
        val activeStopId = tabOrder[currentOrderIndex]
        highlightActiveTabStop(activeStopId)

        // 遇到 $0 时终止 Snippet 模式
        if (activeStopId == 0 || currentOrderIndex == tabOrder.size - 1) {
            endSnippet()
        }
    }

    fun shiftToPreviousTabStop() {
        if (currentOrderIndex > 0) {
            currentOrderIndex--
            highlightActiveTabStop(tabOrder[currentOrderIndex])
        }
    }

    private fun highlightActiveTabStop(stopId: Int) {
        val group = tabStopGroups[stopId] ?: return
        val primaryRange = group.ranges.firstOrNull() ?: return
        val indexer = editor.text.indexer

        val start = indexer.getCharPosition(primaryRange.startIndex)
        val end = indexer.getCharPosition(primaryRange.endIndex)

        editor.setSelectionRegion(start.line, start.column, end.line, end.column)
        editor.ensurePositionVisible(start.line, start.column)
    }

    fun endSnippet() {
        tabStopGroups.clear()
        tabOrder.clear()
        currentOrderIndex = -1
        editor.invalidate()
    }
}