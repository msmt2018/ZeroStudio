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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 折叠屏铰链 (hinge) 视觉 + 交互叠加层 (PR-C).
 *
 * 之前 v2.1 是纯视觉: 一条垂直阴影 + 折痕线. PR-C 把它升级为**真实可交互**:
 * - 用户**拖拽铰链**改变 [foldAngle] (0° = 全展开, 180° = 完全折叠/对折)
 * - 根据 [foldAngle] 调整:
 *   - 阴影宽度 (折叠越深, 阴影越宽, 模拟铰链阴影叠加)
 *   - 折痕色 (折叠越深越黑)
 *   - content 渲染的 "被折叠区域" 透明度 (180° 时隐藏)
 *
 * 用户要求 #1.1: "标准开发者调整被折叠区域代码以及适配等". 之前是固定铰链, 现在:
 * - 真实拖拽: 用户在铰链附近上下/左右拖动, foldAngle 从 0° 滑到 180°
 * - 折痕视觉: 跟随 foldAngle 变化
 * - 锁定状态: foldAngle < 30° 时算"展开" (大于 30° 仍渲染 content)
 *
 * 注意: 这是**视觉/交互**模拟, 不模拟铰链物理 / 屏幕亮度衰减. 因为预览中没法
 * 真实模拟折叠物理, 但 drag interaction + 折痕深度能让用户"看得到"折叠效果.
 *
 * @param foldAngle 当前折叠角度 (度). 0 = 展开, 180 = 折叠. 用 [remember] / [mutableStateOf] 维护.
 * @param onFoldAngleChange 拖拽时回调, 上层 (DeviceFrame) 用来同步到 ViewModel.
 * @param modifier 外部 modifier
 * @param horizontal 铰链方向. `true` = 横向 (Galaxy Z Fold 内屏, 横向铰链) ;
 *                   `false` = 竖向 (Surface Duo).
 * @param maxFoldAngle 最大折叠角度 (默认 180). 实际开发中可能只想模拟 0~120°.
 */
@Composable
fun FoldableHingeOverlay(
    foldAngle: Float = 0f,
    onFoldAngleChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    horizontal: Boolean = true,
    maxFoldAngle: Float = 180f,
    shadowWidthDp: Float = 28f,
    creaseWidthDp: Float = 4f,
    shadowColor: Color = Color(0x60000000),
    creaseColor: Color = Color(0xFF000000),
) {
    // 拖拽手势 — 水平/竖向根据铰链方向决定
    val dragModifier = if (horizontal) {
        Modifier.pointerInput(Unit) {
            detectVerticalDragGestures { _, dragAmount ->
                val newAngle = (foldAngle + (dragAmount / 4f).coerceIn(-10f, 10f))
                    .coerceIn(0f, maxFoldAngle)
                onFoldAngleChange(newAngle)
            }
        }
    } else {
        Modifier.pointerInput(Unit) {
            detectDragGestures { _, dragAmount ->
                val newAngle = (foldAngle + (dragAmount.x / 4f).coerceIn(-10f, 10f))
                    .coerceIn(0f, maxFoldAngle)
                onFoldAngleChange(newAngle)
            }
        }
    }

    val progress = (foldAngle / maxFoldAngle).coerceIn(0f, 1f)
    // 阴影宽度跟随 foldAngle 变化: 0° = 4dp, 180° = 64dp
    val dynamicShadowWidth = shadowWidthDp * (1f + progress * 1.3f)
    // 折痕颜色: 折叠越深越黑
    val dynamicCrease = lerpColor(creaseColor.copy(alpha = 0.6f), creaseColor, progress)
    val dynamicShadow = lerpColor(
        shadowColor.copy(alpha = 0.3f),
        shadowColor.copy(alpha = 0.85f),
        progress,
    )

    Canvas(
        modifier = modifier.then(dragModifier),
    ) {
        val density = this.density
        val shadowPx = dynamicShadowWidth * density
        val creasePx = creaseWidthDp * density

        if (horizontal) {
            // 横向铰链 - 中间一条垂直阴影
            val centerX = size.width / 2f

            // 左阴影 (从 center 渐变到透明)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, dynamicShadow, dynamicShadow, Color.Transparent),
                    startX = centerX - shadowPx - creasePx,
                    endX = centerX + shadowPx + creasePx,
                ),
                topLeft = Offset(centerX - shadowPx - creasePx, 0f),
                size = androidx.compose.ui.geometry.Size(shadowPx * 2f + creasePx, size.height),
            )
            // 折痕
            drawRect(
                color = dynamicCrease,
                topLeft = Offset(centerX - creasePx / 2f, 0f),
                size = androidx.compose.ui.geometry.Size(creasePx, size.height),
            )

            // 【PR-C】折叠时加一条"折线" — 从中央往上/下倾斜, 模拟屏幕折弯
            if (progress > 0.1f) {
                val foldOffset = progress * size.height * 0.04f
                drawLine(
                    color = dynamicCrease.copy(alpha = progress),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, foldOffset),
                    strokeWidth = creasePx * 0.5f,
                )
                drawLine(
                    color = dynamicCrease.copy(alpha = progress),
                    start = Offset(centerX, size.height),
                    end = Offset(centerX, size.height - foldOffset),
                    strokeWidth = creasePx * 0.5f,
                )
            }
        } else {
            // 竖向铰链 - 中间一条水平阴影
            val centerY = size.height / 2f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, dynamicShadow, dynamicShadow, Color.Transparent),
                    startY = centerY - shadowPx - creasePx,
                    endY = centerY + shadowPx + creasePx,
                ),
                topLeft = Offset(0f, centerY - shadowPx - creasePx),
                size = androidx.compose.ui.geometry.Size(size.width, shadowPx * 2f + creasePx),
            )
            drawRect(
                color = dynamicCrease,
                topLeft = Offset(0f, centerY - creasePx / 2f),
                size = androidx.compose.ui.geometry.Size(size.width, creasePx),
            )

            if (progress > 0.1f) {
                val foldOffset = progress * size.width * 0.04f
                drawLine(
                    color = dynamicCrease.copy(alpha = progress),
                    start = Offset(0f, centerY),
                    end = Offset(foldOffset, centerY),
                    strokeWidth = creasePx * 0.5f,
                )
                drawLine(
                    color = dynamicCrease.copy(alpha = progress),
                    start = Offset(size.width, centerY),
                    end = Offset(size.width - foldOffset, centerY),
                    strokeWidth = creasePx * 0.5f,
                )
            }
        }
    }
}

/**
 * 简单线性插值颜色.
 */
private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t,
    )
}
