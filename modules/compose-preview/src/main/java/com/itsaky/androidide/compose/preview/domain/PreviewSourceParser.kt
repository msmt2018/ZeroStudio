/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.compose.preview.domain

import com.itsaky.androidide.compose.preview.PreviewConfig
import com.itsaky.androidide.compose.preview.domain.model.ParsedPreviewSource
import org.slf4j.LoggerFactory

/**
 * 预览源码解析器 v2.1.
 *
 * 纯正则实现 (不依赖 K2 PSI), 识别:
 * - `package x.y.z` → 包名
 * - `class A` / `object A` → 类名
 * - `@Preview(...)` 标注的 `fun Foo()`
 * - `@PreviewParameter(provider = ...)` 标注
 * - `@PreviewFontScale = 1.5f` 标注
 * - `@PreviewLightDark` 标注
 *
 * 解析顺序:
 * 1. 包名 / 类名 (必要)
 * 2. 标注了 `@Preview` 的函数 (优先)
 * 3. 任意 `@Composable fun` 作为兜底
 */
class PreviewSourceParser {

    fun parse(source: String): ParsedPreviewSource? {
        val packageName = extractPackageName(source) ?: return null
        val className = extractClassName(source)
        val previewConfigs = detectAllPreviewFunctions(source)
        return ParsedPreviewSource(packageName, className, previewConfigs)
    }

    fun extractPackageName(source: String): String? {
        return PACKAGE_PATTERN.find(source)?.groupValues?.get(1)
    }

    fun extractClassName(source: String): String? {
        CLASS_PATTERN.find(source)?.groupValues?.get(1)?.let { return it }
        OBJECT_PATTERN.find(source)?.groupValues?.get(1)?.let { return it }
        return null
    }

    fun detectAllPreviewFunctions(source: String): List<PreviewConfig> {
        val previews = mutableListOf<PreviewConfig>()
        val seenFunctions = mutableSetOf<String>()

        // @Preview
        PREVIEW_ANNOTATION_PATTERN.findAll(source).forEach { match ->
            val params = match.groupValues[1]
            val functionName = match.groupValues[2]
            if (seenFunctions.add(functionName)) {
                previews.add(buildConfig(functionName, params, source))
            }
        }

        // @Composable @Preview
        COMPOSABLE_PREVIEW_PATTERN.findAll(source).forEach { match ->
            val params = match.groupValues[1]
            val functionName = match.groupValues[2]
            if (seenFunctions.add(functionName)) {
                previews.add(buildConfig(functionName, params, source))
            }
        }

        // 兜底: 任意 @Composable fun
        if (previews.isEmpty()) {
            COMPOSABLE_FUNCTION_PATTERN.findAll(source).forEach { match ->
                val functionName = match.groupValues[1]
                if (seenFunctions.add(functionName)) {
                    previews.add(PreviewConfig(functionName = functionName))
                }
            }
        }

        LOG.debug("Detected {} preview functions: {}", previews.size, previews.map { it.functionName })
        return previews
    }

    /**
     * 从 [params] 解析 @Preview 注解参数 + 从 [source] 全文解析
     * @PreviewParameter / @PreviewFontScale / @PreviewLightDark.
     */
    private fun buildConfig(functionName: String, params: String, source: String): PreviewConfig {
        return PreviewConfig(
            functionName = functionName,
            heightDp = extractIntParam(params, "heightDp"),
            widthDp = extractIntParam(params, "widthDp"),
            fontScale = extractFontScale(source, functionName),
            isLightDark = isLightDark(source, functionName),
            parameterProviderName = extractParameterProvider(source, functionName),
        )
    }

    private fun extractIntParam(params: String, name: String): Int? {
        if (params.isBlank()) return null
        return Regex("""$name\s*=\s*(\d+)""").find(params)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractFloatParam(params: String, name: String): Float? {
        if (params.isBlank()) return null
        return Regex("""$name\s*=\s*([0-9]*\.?[0-9]+)""").find(params)?.groupValues?.get(1)?.toFloatOrNull()
    }

    /**
     * 解析 @PreviewFontScale = 1.5f (v2.1 新).
     *
     * 实际使用: 解析该函数附近的 `@PreviewFontScale` 注解.
     */
    private fun extractFontScale(source: String, functionName: String): Float? {
        val pattern = Regex(
            """@PreviewFontScale\s*=\s*([0-9]*\.?[0-9]+)f?\s*(?:\([^)]*\))?[\s\n]*fun\s+$functionName"""
        )
        return pattern.find(source)?.groupValues?.get(1)?.toFloatOrNull()
    }

    /**
     * 检测 @PreviewLightDark (v2.1 新).
     */
    private fun isLightDark(source: String, functionName: String): Boolean {
        val pattern = Regex(
            """@PreviewLightDark\s*(?:\([^)]*\))?[\s\n]*fun\s+$functionName"""
        )
        return pattern.containsMatchIn(source)
    }

    /**
     * 解析 @PreviewParameter(provider = X::class) (v2.1 新).
     */
    private fun extractParameterProvider(source: String, functionName: String): String? {
        val pattern = Regex(
            """@PreviewParameter\s*\(\s*provider\s*=\s*([\w.]+)\s*::\s*class\s*\)[\s\n]*(?:\([^)]*\))?[\s\n]*fun\s+$functionName"""
        )
        return pattern.find(source)?.groupValues?.get(1)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PreviewSourceParser::class.java)

        // Matches: package com.example.app
        private val PACKAGE_PATTERN = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)

        // Matches: class ClassName
        private val CLASS_PATTERN = Regex("""^\s*class\s+(\w+)""", RegexOption.MULTILINE)

        // Matches: object ObjectName
        private val OBJECT_PATTERN = Regex("""^\s*object\s+(\w+)""", RegexOption.MULTILINE)

        // Matches: @Preview(...) fun FunctionName
        private val PREVIEW_ANNOTATION_PATTERN = Regex(
            """@Preview\s*(?:\(([^)]*)\))?\s*(?:@\w+(?:\s*\([^)]*\))?[\s\n]*)*fun\s+(\w+)""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
        )

        // Matches: @Composable @Preview(...) fun FunctionName
        private val COMPOSABLE_PREVIEW_PATTERN = Regex(
            """@Composable\s*(?:@\w+(?:\s*\([^)]*\))?[\s\n]*)*@Preview\s*(?:\(([^)]*)\))?[\s\n]*(?:@\w+(?:\s*\([^)]*\))?[\s\n]*)*fun\s+(\w+)""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
        )

        // Matches: @Composable fun FunctionName (fallback when no @Preview found)
        private val COMPOSABLE_FUNCTION_PATTERN = Regex("""@Composable\s+fun\s+(\w+)""")
    }
}
