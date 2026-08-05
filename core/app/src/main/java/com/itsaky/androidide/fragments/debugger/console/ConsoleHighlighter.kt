package com.itsaky.androidide.fragments.debugger.console

import androidx.compose.ui.graphics.Color
import com.itsaky.androidide.ui.theme.deviceconnection.DeviceConnectionColors

/**
 * 控制台单行文本的高亮片段。
 *
 * @param text 片段文本
 * @param color 片段颜色
 */
data class HighlightSegment(
    val text: String,
    val color: Color,
)

/**
 * adb 命令输出高亮器。
 *
 * 按正则匹配 adb 关键词 / 路径 / 错误行，将一行文本切成多段不同颜色的 [HighlightSegment]。
 *
 * 复刻 connection 模块 HighlightQueryText 的思路但全新实现：
 * - 命令回显（`$ ...`）→ 命令绿
 * - adb 子命令（shell / push / pull / install / uninstall / devices / connect / disconnect / forward / shell / start-server / kill-server / reboot / root / remount / wait-for-device / logcat / dumpsys / pm / am / get-state / get-serialno / bugreport）→ 主色蓝
 * - 路径（/开头或含 / 的单词，或 com.xxx.yyy 包名）→ 路径蓝
 * - 错误标识（error / failed / failure / denied / not found / cannot / exception）→ 错误红
 * - exit= 数字 → 状态绿/红
 * - 其余文本 → 控制台文本灰
 */
object ConsoleHighlighter {

    /** adb 顶层子命令关键词。 */
    private val ADB_KEYWORDS = setOf(
        "adb", "shell", "push", "pull", "install", "uninstall", "devices",
        "connect", "disconnect", "forward", "reverse", "start-server", "kill-server",
        "reboot", "root", "unroot", "remount", "wait-for-device", "logcat", "bugreport",
        "get-state", "get-serialno", "enable-root", "disable-root", "tcpip", "usb",
        "sync", "jdwp", "ppp", "restore", "backup", "sideload", "keygen",
        "pm", "am", "dumpsys", "wm", "svc", "settings", "cmd", "content",
        "stat", "ls", "cd", "rm", "mkdir", "rmdir", "cp", "mv", "touch", "chmod", "chown",
        "cat", "echo", "grep", "find", "ps", "top", "free", "df", "du", "id", "whoami",
        "su", "kill", "killall", "mount", "umount", "setprop", "getprop",
    )

    /** 错误关键词。 */
    private val ERROR_KEYWORDS = setOf(
        "error", "failed", "failure", "denied", "not found", "cannot", "exception",
        "fatal", "crash", "killed", "timeout", "refused", "unreachable", "unauthorized",
        "permission denied", "no such file", "illegal", "invalid",
    )

    /**
     * 把一行文本切成带颜色的片段。
     *
     * @param text 原始行
     * @param isError 是否来自 stderr（影响默认色）
     * @param colors 当前主题配色
     */
    fun highlight(
        text: String,
        isError: Boolean,
        colors: DeviceConnectionColors,
    ): List<HighlightSegment> {
        if (text.isEmpty()) return listOf(HighlightSegment("", colors.consoleText))

        // 命令回显行整体用命令绿
        if (text.startsWith("$ ")) {
            return listOf(HighlightSegment(text, colors.consoleCommand))
        }

        // exit=<n>
        if (text.startsWith("exit=") || text.startsWith("^C")) {
            val code = text.removePrefix("exit=").trimOrNull()
            val color = when {
                code == null -> colors.consoleError
                code == "0" -> colors.statusGreen
                else -> colors.statusRed
            }
            return listOf(HighlightSegment(text, color))
        }

        // 按空白分词，逐词判定颜色，保留空白为分隔
        val segments = mutableListOf<HighlightSegment>()
        val tokens = tokenize(text)
        for (token in tokens) {
            if (token.isBlank()) {
                segments.add(HighlightSegment(token, colors.consoleText))
                continue
            }
            val lower = token.lowercase().trimEnd(':', ',', ';', '.')
            val color = when {
                isError -> colors.consoleError
                token in ADB_KEYWORDS || lower in ADB_KEYWORDS -> colors.primary
                isPath(token) || isPackageName(token) -> colors.consolePath
                containsErrorKeyword(token) -> colors.consoleError
                else -> if (isError) colors.consoleError else colors.consoleText
            }
            segments.add(HighlightSegment(token, color))
        }
        // 合并相邻同色片段，减少 AnnotatedString 构建开销
        return mergeAdjacent(segments)
    }

    /** 按空白切分但保留空白字符。 */
    private fun tokenize(text: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var lastWasSpace = false
        for (ch in text) {
            val isSpace = ch.isWhitespace()
            if (isSpace != lastWasSpace && sb.isNotEmpty()) {
                result.add(sb.toString())
                sb.setLength(0)
            }
            sb.append(ch)
            lastWasSpace = isSpace
        }
        if (sb.isNotEmpty()) result.add(sb.toString())
        return result
    }

    /** 判定是否为路径：以 / 开头，或包含 / 且无空格。 */
    private fun isPath(token: String): Boolean {
        if (token.startsWith("/")) return true
        if (token.contains("/") && !token.contains(" ")) return true
        // /storage/emulated/0/xxx
        if (token.matches(Regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._/-]+$"))) return true
        return false
    }

    /** 判定是否为包名：com.xxx.yyy 形式。 */
    private fun isPackageName(token: String): Boolean {
        return token.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")) &&
            token.contains(".")
    }

    /** 该 token 是否包含错误关键词。 */
    private fun containsErrorKeyword(token: String): Boolean {
        val lower = token.lowercase()
        return ERROR_KEYWORDS.any { lower.contains(it) }
    }

    /** 合并相邻同色片段。 */
    private fun mergeAdjacent(segments: List<HighlightSegment>): List<HighlightSegment> {
        if (segments.isEmpty()) return segments
        val result = mutableListOf<HighlightSegment>()
        var current = segments.first()
        for (i in 1 until segments.size) {
            val next = segments[i]
            if (next.color == current.color) {
                current = current.copy(text = current.text + next.text)
            } else {
                result.add(current)
                current = next
            }
        }
        result.add(current)
        return result
    }

    private fun String.trimOrNull(): String? =
        if (this.isBlank()) null else this
}