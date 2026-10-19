package com.zerostudio.ide.treesitter.tsh

/**
 * @author android_zero
 *
 * 变更说明与设计架构：
 * 将 TSH 语法树编译为 100% Tree-sitter 原生兼容的 SCM 字符串，并把 Capture 样式打平成连续整型数组。
 */
class TshCompiler {

    fun compile(document: TshDocument): TshCompiledResult {
        val scmQueryBuilder = StringBuilder()
        val captureMetaList = mutableListOf<Int>()
        val actionIdMap = mutableMapOf<Int, String>()
        var globalCaptureIndex = 0

        document.rules.forEach { rule ->
            var patternScm = rule.scmPatternWithPredicates

            rule.captureStyles.forEach { (tag, style) ->
                val internalCapture = "cap_${globalCaptureIndex}"
                
                if (patternScm.contains("@$tag")) {
                    patternScm = patternScm.replace("@$tag", "@$internalCapture")
                } else if (!patternScm.endsWith("@$internalCapture")) {
                    patternScm = "$patternScm @$internalCapture"
                }

                val color = parseHex(style.colorHex ?: "#FFFFFF")
                val mask = StyleMask.pack(
                    color = color,
                    bold = style.bold,
                    italic = style.italic,
                    underline = style.underline,
                    hasAction = style.action != null
                )

                // 打包 3 元组元数据：[PackedStyle, Priority, ActionId]
                captureMetaList.add(mask)
                captureMetaList.add(style.priority)
                val actionId = if (style.action != null) globalCaptureIndex + 1 else 0
                captureMetaList.add(actionId)

                if (style.action != null) {
                    actionIdMap[actionId] = style.action
                }

                globalCaptureIndex++
            }

            scmQueryBuilder.append(patternScm).append("\n\n")
        }

        return TshCompiledResult(
            standardScmQuery = scmQueryBuilder.toString(),
            flatStyleTable = captureMetaList.toIntArray(),
            captureCount = globalCaptureIndex,
            actionIdMap = actionIdMap,
            behaviors = document.behaviors,
            metadata = document.metadata
        )
    }

    private fun parseHex(hex: String): Long {
        val clean = hex.removePrefix("#")
        return when (clean.length) {
            6 -> clean.toLong(16)
            8 -> clean.substring(2).toLong(16)
            else -> 0xFFFFFFL
        }
    }
}