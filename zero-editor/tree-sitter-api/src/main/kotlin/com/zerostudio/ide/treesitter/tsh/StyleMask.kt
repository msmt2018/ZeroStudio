package com.zerostudio.ide.treesitter.tsh

import androidx.compose.ui.graphics.Color

/**
 * @author android_zero
 *
 * 变更说明：
 * 紧凑型 32 位整型样式位掩码（Packed Style）：
 * 0x00FFFFFF -> RGB 颜色 (24位)
 * 0x01000000 -> 粗体 (Bold)
 * 0x02000000 -> 斜体 (Italic)
 * 0x04000000 -> 下划线 (Underline)
 * 0x08000000 -> 交互动作标记 (Action)
 */
object StyleMask {
    const val MASK_COLOR = 0x00FFFFFF
    const val FLAG_BOLD = 1 shl 24
    const val FLAG_ITALIC = 1 shl 25
    const val FLAG_UNDERLINE = 1 shl 26
    const val FLAG_ACTION = 1 shl 27

    fun pack(
        color: Long,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false,
        hasAction: Boolean = false
    ): Int {
        var result = (color and 0x00FFFFFFL).toInt()
        if (bold) result = result or FLAG_BOLD
        if (italic) result = result or FLAG_ITALIC
        if (underline) result = result or FLAG_UNDERLINE
        if (hasAction) result = result or FLAG_ACTION
        return result
    }

    fun extractColor(packed: Int): Color = Color(packed and MASK_COLOR or (0xFF shl 24))
    fun isBold(packed: Int): Boolean = (packed and FLAG_BOLD) != 0
    fun isItalic(packed: Int): Boolean = (packed and FLAG_ITALIC) != 0
    fun isUnderline(packed: Int): Boolean = (packed and FLAG_UNDERLINE) != 0
    fun hasAction(packed: Int): Boolean = (packed and FLAG_ACTION) != 0
}