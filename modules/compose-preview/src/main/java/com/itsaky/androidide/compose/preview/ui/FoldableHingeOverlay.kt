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

package com.itsaky.androidide.compose.preview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 折叠屏铰链 (hinge) 视觉叠加层.
 *
 * 在折叠屏内屏中央画一条**垂直阴影 + 折痕线**, 模拟真实折叠屏
 * 物理铰链对屏幕的影响:
 *
 * - 中心 4dp 宽: 一条深色折痕
 * - 两侧各 28dp: 阴影渐变 (从中央深到两侧透明)
 *
 * **注意**: 这是**视觉示意**, 不是 1:1 物理模拟. 真实铰链会
 * 影响屏幕亮度 / 反射, 但因为预览中无法复现物理光, 这里只画
 * 一条折痕 + 阴影给用户视觉提示.
 *
 * @param modifier 外部 modifier, 应该是被叠加在 [DeviceFrame] 屏幕
 *                 内容上方并填满屏幕. 铰链位置由 [horizontal] 决定.
 * @param horizontal 铰链方向. `true` = 横向 (Galaxy Z Fold 内屏,
 *                   横向) ; `false` = 竖向 (Surface Duo).
 * @param shadowWidthDp 两侧阴影宽度 (默认 28dp)
 * @param creaseWidthDp 中间折痕宽度 (默认 4dp)
 * @param shadowColor 阴影 / 折痕颜色
 */
@Composable
fun FoldableHingeOverlay(
    modifier: Modifier = Modifier,
    horizontal: Boolean = true,
    shadowWidthDp: Float = 28f,
    creaseWidthDp: Float = 4f,
    shadowColor: Color = Color(0x60000000),
    creaseColor: Color = Color(0xFF000000),
) {
    Canvas(modifier = modifier) {
        val density = this.density
        val shadowPx = shadowWidthDp * density
        val creasePx = creaseWidthDp * density

        if (horizontal) {
            // 横向铰链 - 中间一条垂直阴影
            val centerX = size.width / 2f

            // 左阴影 (从 center 渐变到透明)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, shadowColor, shadowColor, Color.Transparent),
                    startX = centerX - shadowPx - creasePx,
                    endX = centerX + shadowPx + creasePx,
                ),
                topLeft = Offset(centerX - shadowPx - creasePx, 0f),
                size = androidx.compose.ui.geometry.Size(shadowPx * 2f + creasePx, size.height),
            )
            // 折痕
            drawRect(
                color = creaseColor,
                topLeft = Offset(centerX - creasePx / 2f, 0f),
                size = androidx.compose.ui.geometry.Size(creasePx, size.height),
            )
        } else {
            // 竖向铰链 - 中间一条水平阴影
            val centerY = size.height / 2f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, shadowColor, shadowColor, Color.Transparent),
                    startY = centerY - shadowPx - creasePx,
                    endY = centerY + shadowPx + creasePx,
                ),
                topLeft = Offset(0f, centerY - shadowPx - creasePx),
                size = androidx.compose.ui.geometry.Size(size.width, shadowPx * 2f + creasePx),
            )
            drawRect(
                color = creaseColor,
                topLeft = Offset(0f, centerY - creasePx / 2f),
                size = androidx.compose.ui.geometry.Size(size.width, creasePx),
            )
        }
    }
}
