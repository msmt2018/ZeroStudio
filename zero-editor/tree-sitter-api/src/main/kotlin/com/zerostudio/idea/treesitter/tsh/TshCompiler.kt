package com.zerostudio.idea.treesitter.tsh

/**
 * @author android_zero
 *
 * 变更说明：
 * 1. 输出 100% 纯净的原生 SCM，无任何非官方符号残留。
 * 2. 编译 RGBA 全色域 + 样式标志位 (Bold, Italic, Underline, Strikethrough, Action) 为紧凑数组。
 */
class TshCompiler {

    companion object {
        const val FLAG_BOLD          = 1 shl 0
        const val FLAG_ITALIC        = 1 shl 1
        const val FLAG_UNDERLINE     = 1 shl 2
        const val FLAG_STRIKETHROUGH = 1 shl 3
        const val FLAG_ACTION        = 1 shl 4
    }

    fun compile(document: TshDocument): TshCompiledResult {
        val scmQueryBuilder = StringBuilder()
        val captureMetaList = mutableListOf<Int>()
        val actionIdMap = mutableMapOf<Int, String>()
        var globalCaptureIndex = 0

        document.rules.forEach { rule ->
            var patternScm = rule.scmPatternWithPredicates

            rule.captureStyles.forEach { (tag, style) ->
                val internalCapture = "c$globalCaptureIndex"

                // 替换语义标签为内核唯一索引标签
                if (patternScm.contains("@$tag")) {
                    patternScm = patternScm.replace("@$tag", "@$internalCapture")
                } else if (!patternScm.endsWith("@$internalCapture")) {
                    patternScm = "$patternScm @$internalCapture"
                }

                // 编译 RGBA 32 位整型 (默认纯白不透明)
                val rgbaColor = parseRgbaColor(style.colorHex ?: "#FFFFFFFF")

                // 打包样式标志位
                var styleFlags = 0
                if (style.bold) styleFlags = styleFlags or FLAG_BOLD
                if (style.italic) styleFlags = styleFlags or FLAG_ITALIC
                if (style.underline) styleFlags = styleFlags or FLAG_UNDERLINE
                if (style.strikethrough) styleFlags = styleFlags or FLAG_STRIKETHROUGH
                if (style.action != null) styleFlags = styleFlags or FLAG_ACTION

                val actionId = if (style.action != null) globalCaptureIndex + 1 else 0
                if (style.action != null) {
                    actionIdMap[actionId] = style.action
                }

                // 写入 C++ 样式元数据数组：每项 4 个整型 [RGBA, StyleFlags, Priority, ActionId]
                captureMetaList.add(rgbaColor)
                captureMetaList.add(styleFlags)
                captureMetaList.add(style.priority)
                captureMetaList.add(actionId)

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

    /**
     * 支持 #RGB, #RGBA, #RRGGBB, #RRGGBBAA 任意精度色值
     */
    private fun parseRgbaColor(hex: String): Int {
        val clean = hex.removePrefix("#")
        return when (clean.length) {
            3 -> { // RGB -> RRGGBBFF
                val r = clean[0].toString().repeat(2).toInt(16)
                val g = clean[1].toString().repeat(2).toInt(16)
                val b = clean[2].toString().repeat(2).toInt(16)
                (r shl 24) or (g shl 16) or (b shl 8) or 0xFF
            }
            4 -> { // RGBA -> RRGGBBAA
                val r = clean[0].toString().repeat(2).toInt(16)
                val g = clean[1].toString().repeat(2).toInt(16)
                val b = clean[2].toString().repeat(2).toInt(16)
                val a = clean[3].toString().repeat(2).toInt(16)
                (r shl 24) or (g shl 16) or (b shl 8) or a
            }
            6 -> { // RRGGBB -> RRGGBBFF
                val rgb = clean.toInt(16)
                (rgb shl 8) or 0xFF
            }
            8 -> { // RRGGBBAA 直接匹配
                clean.toLong(16).toInt()
            }
            else -> 0xFFFFFFFF.toInt()
        }
    }
}