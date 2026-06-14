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

package com.itsaky.androidide.compose.preview.runtime

import androidx.compose.ui.graphics.Color

/**
 * LiveLiteral 类型安全编码器 v2.2 (P1).
 *
 * 负责在 [Int] (Compose Compiler 生成的 static int 字段) 与业务值
 * ([Color] / Dp / Sp / Float / Boolean / Int) 之间做正确转换.
 *
 * ## 编码规则
 *
 * - [LiveLiteralType.INT]     — 直接 int
 * - [LiveLiteralType.FLOAT]   — `Float.toRawBits`
 * - [LiveLiteralType.BOOLEAN] — 0 / 1
 * - [LiveLiteralType.DP]      — `Dp.value` 乘以 density (px)
 *   - 实际 Compose 编译器不直接生成 Dp, Dp 由 .dp 扩展函数内联成 Float
 * - [LiveLiteralType.SP]      — `Sp.value` 乘以 density (px)
 * - [LiveLiteralType.LONG]    — 跨 2 字段: high 32 + low 32
 * - [LiveLiteralType.COLOR]   — `Color(0xFF6650a4)` 跨 2 字段:
 *   - high 32: `0xFF6650A4.toInt()`
 *   - low 32: 0
 *
 * ## 字段配对
 *
 * 对于跨 2 字段的类型 (LONG / COLOR), Compose Compiler 会生成:
 * - `Int$arg-0$callSite-3` (high 32)
 * - `Int$arg-1$callSite-3` (low 32)
 *
 * **同一 callSite hash** 配对, 由 [LiveLiteralsScanner.scanAll] 检测.
 *
 * ## Density 注入
 *
 * Dp / Sp 编码时需要 density, 由调用方从 Resources.displayMetrics 注入.
 * 默认 density = 1.0 (近似预览场景, 实际界面给具体值).
 *
 * @see LiveLiteralsScanner
 * @see LiveLiteralEditor
 */
class LiveLiteralEncoder(
    private val density: Float = DEFAULT_DENSITY,
    private val fontScale: Float = DEFAULT_FONT_SCALE,
) {
    /**
     * 把业务值编码为 Compose Compiler 期望的 int 值.
     *
     * @return 单 int 值 (单字段) 或 IntPair (跨 2 字段: high + low)
     */
    fun encode(value: LiveLiteralValue): EncodedLiteral {
        return when (value) {
            is LiveLiteralValue.IntValue -> EncodedLiteral.Single(value.value)
            is LiveLiteralValue.FloatValue -> EncodedLiteral.Single(
                java.lang.Float.floatToRawIntBits(value.value),
            )
            is LiveLiteralValue.BooleanValue -> EncodedLiteral.Single(if (value.value) 1 else 0)
            is LiveLiteralValue.DpValue -> {
                // Compose 的 Dp.value 实际就是 Float, 这里用 dp 数值编码
                // 实际 K2 编译时 .dp = (Dp(value.toFloat())) 内联
                EncodedLiteral.Single(java.lang.Float.floatToRawIntBits(value.value))
            }
            is LiveLiteralValue.SpValue -> {
                // Sp 同 Dp, 都是 Float
                EncodedLiteral.Single(java.lang.Float.floatToRawIntBits(value.value))
            }
            is LiveLiteralValue.LongValue -> {
                // 跨 2 字段: high 32 + low 32
                val high = (value.value ushr 32).toInt()
                val low = (value.value and 0xFFFFFFFFL).toInt()
                EncodedLiteral.Pair(high, low)
            }
            is LiveLiteralValue.ColorValue -> {
                // Color(ULong packed) 跨 2 字段
                val packed = value.value.toArgb().toLong() and 0xFFFFFFFFL
                val high = (packed ushr 32).toInt()
                val low = (packed and 0xFFFFFFFFL).toInt()
                EncodedLiteral.Pair(high, low)
            }
        }
    }

    /**
     * 把 Compose Compiler 生成的 int 值解码为业务值.
     *
     * @param type 字面量类型
     * @param intValue 单 int (单字段)
     * @param pairValue 跨 2 字段 (high 32 + low 32), 仅当 type = LONG / COLOR 时使用
     */
    fun decode(
        type: LiveLiteralType,
        intValue: Int,
        pairValue: Int? = null,
    ): LiveLiteralValue {
        return when (type) {
            LiveLiteralType.INT -> LiveLiteralValue.IntValue(intValue)
            LiveLiteralType.FLOAT -> LiveLiteralValue.FloatValue(
                java.lang.Float.intBitsToFloat(intValue),
            )
            LiveLiteralType.BOOLEAN -> LiveLiteralValue.BooleanValue(intValue != 0)
            LiveLiteralType.DP -> LiveLiteralValue.DpValue(
                java.lang.Float.intBitsToFloat(intValue) / density,
            )
            LiveLiteralType.SP -> LiveLiteralValue.SpValue(
                java.lang.Float.intBitsToFloat(intValue) / (density * fontScale),
            )
            LiveLiteralType.LONG -> {
                requireNotNull(pairValue) { "LONG literal needs pair value" }
                val long = ((intValue.toLong() and 0xFFFFFFFFL) shl 32) or
                    (pairValue.toLong() and 0xFFFFFFFFL)
                LiveLiteralValue.LongValue(long)
            }
            LiveLiteralType.COLOR -> {
                requireNotNull(pairValue) { "COLOR literal needs pair value" }
                val long = ((intValue.toLong() and 0xFFFFFFFFL) shl 32) or
                    (pairValue.toLong() and 0xFFFFFFFFL)
                LiveLiteralValue.ColorValue(Color(long.toULong()))
            }
            LiveLiteralType.UNKNOWN -> LiveLiteralValue.IntValue(intValue)
        }
    }

    /**
     * 计算两个 int 字段 (来自同 callSite) 的解码结果.
     *
     * @param type 字面量类型
     * @param fieldName 第一个字段名 (high 32)
     * @param firstValue 第一个 int 值
     * @param secondValue 第二个 int 值 (low 32)
     */
    fun decodePair(
        type: LiveLiteralType,
        firstValue: Int,
        secondValue: Int,
    ): LiveLiteralValue {
        return decode(type, firstValue, pairValue = secondValue)
    }

    companion object {
        const val DEFAULT_DENSITY = 2.75f  // Pixel 5 默认
        const val DEFAULT_FONT_SCALE = 1.0f

        /**
         * 工厂: 从 Resources 注入 density / fontScale.
         */
        fun fromResources(
            density: Float,
            fontScale: Float = DEFAULT_FONT_SCALE,
        ): LiveLiteralEncoder = LiveLiteralEncoder(density, fontScale)
    }
}

/**
 * 编码结果.
 *
 * - [Single]: 单字段, 1 个 int
 * - [Pair]: 跨 2 字段 (high 32 + low 32)
 */
sealed class EncodedLiteral {
    data class Single(val intValue: Int) : EncodedLiteral()
    data class Pair(val high: Int, val low: Int) : EncodedLiteral()
}

/**
 * 业务值类型 (跨 2 字段的 Long/Color 需要 Pair 编码).
 */
sealed class LiveLiteralValue {
    data class IntValue(val value: Int) : LiveLiteralValue()
    data class FloatValue(val value: Float) : LiveLiteralValue()
    data class BooleanValue(val value: Boolean) : LiveLiteralValue()
    data class DpValue(val value: Float) : LiveLiteralValue()
    data class SpValue(val value: Float) : LiveLiteralValue()
    data class LongValue(val value: Long) : LiveLiteralValue()
    data class ColorValue(val value: Color) : LiveLiteralValue()
}
