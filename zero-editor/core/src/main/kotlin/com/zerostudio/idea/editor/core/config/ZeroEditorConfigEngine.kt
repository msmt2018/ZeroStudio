package com.zerostudio.idea.editor.core.config

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Paths

data class ResolvedEditorConfig(
    val indentStyle: String? = null,        // "tab" or "space"
    val indentSize: Int? = null,             // e.g. 2, 4
    val tabWidth: Int? = null,               // e.g. 4
    val endOfLine: LineSeparatorMode? = null,// LF, CRLF, CR
    val charset: String? = null,             // "utf-8", "latin1"
    val trimTrailingWhitespace: Boolean? = null,
    val insertFinalNewline: Boolean? = null
)

/**
 * 项目级 .editorconfig 自主解析器
 */
object ZeroEditorConfigEngine {

    fun resolveForFile(targetFile: File): ResolvedEditorConfig {
        var currentDir = targetFile.parentFile
        val hierarchyConfigs = mutableListOf<File>()

        // 向上沿父目录寻址直至根目录，捕获层级 .editorconfig
        while (currentDir != null && currentDir.canRead()) {
            val configFile = File(currentDir, ".editorconfig")
            if (configFile.exists() && configFile.isFile) {
                hierarchyConfigs.add(configFile)
                // 检查 root = true
                if (isRootConfig(configFile)) break
            }
            currentDir = currentDir.parentFile
        }

        var resolved = ResolvedEditorConfig()
        // 从最顶层向内层合并覆盖属性
        for (configFile in hierarchyConfigs.reversed()) {
            resolved = mergeConfigFile(configFile, targetFile, resolved)
        }
        return resolved
    }

    private fun isRootConfig(configFile: File): Boolean {
        return configFile.useLines { lines ->
            lines.any { it.trim().equals("root = true", ignoreCase = true) }
        }
    }

    private fun mergeConfigFile(
        configFile: File,
        targetFile: File,
        base: ResolvedEditorConfig
    ): ResolvedEditorConfig {
        var currentSectionApplies = false
        var current = base
        val relativePathStr = targetFile.name

        configFile.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return@forEachLine

            if (line.startsWith("[") && line.endsWith("]")) {
                val pattern = line.substring(1, line.length - 1)
                currentSectionApplies = matchGlob(pattern, relativePathStr)
            } else if (currentSectionApplies && line.contains("=")) {
                val parts = line.split("=", limit = 2).map { it.trim() }
                val key = parts[0].lowercase()
                val value = parts[1].lowercase()

                current = when (key) {
                    "indent_style" -> current.copy(indentStyle = value)
                    "indent_size" -> current.copy(indentSize = value.toIntOrNull())
                    "tab_width" -> current.copy(tabWidth = value.toIntOrNull())
                    "end_of_line" -> current.copy(
                        endOfLine = when (value) {
                            "lf" -> LineSeparatorMode.UNIX_LF
                            "crlf" -> LineSeparatorMode.WINDOWS_CRLF
                            "cr" -> LineSeparatorMode.MAC_CR
                            else -> current.endOfLine
                        }
                    )
                    "charset" -> current.copy(charset = value)
                    "trim_trailing_whitespace" -> current.copy(trimTrailingWhitespace = value.toBoolean())
                    "insert_final_newline" -> current.copy(insertFinalNewline = value.toBoolean())
                    else -> current
                }
            }
        }
        return current
    }

    private fun matchGlob(globPattern: String, fileName: String): Boolean {
        return runCatching {
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$globPattern")
            matcher.matches(Paths.get(fileName))
        }.getOrDefault(false)
    }
}