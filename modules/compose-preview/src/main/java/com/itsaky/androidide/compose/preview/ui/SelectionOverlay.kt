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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 选中元素的可视化覆盖层 v2.1.
 *
 * 渲染:
 * - 选中框 (蓝色虚线, 主选中)
 * - 8 个 resize 手柄 (8 个方向)
 * - 元素名标签 (左上角)
 * - 尺寸 / 颜色 工具提示 (顶部)
 *
 * 行为:
 * - 当 [tool] == [EditorTool.Drag] 时, 手势拖动整个框 → 调整 [Selection.translationX/Y]
 *
 * @param selection 当前选中
 * @param tool 当前工具
 * @param onSelectionChange 拖动结果回调
 * @param modifier modifier
 */
@Composable
fun SelectionOverlay(
    selection: Selection?,
    tool: EditorTool,
    onSelectionChange: (Selection) -> Unit,
    modifier: Modifier = Modifier,
    handleSizeDp: Float = 10f,
) {
    if (selection == null) return

    val density = LocalDensity.current
    val handleSizePx = with(density) { handleSizeDp.dp.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
        // 选中框 + 手柄
        SelectionBox(
            selection = selection,
            handleSizePx = handleSizePx,
            tool = tool,
            onTranslate = { dx, dy ->
                onSelectionChange(
                    selection.copy(
                        translationX = selection.translationX + dx,
                        translationY = selection.translationY + dy,
                    )
                )
            },
        )

        // 元素名标签
        SelectionLabel(
            name = selection.composableName,
            selection = selection,
            modifier = Modifier.align(Alignment.TopStart),
        )

        // 尺寸 / 颜色 工具提示
        if (tool == EditorTool.Eyedropper && selection.sampledColor != null) {
            ColorTooltip(
                color = selection.sampledColor,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        } else {
            SizeTooltip(
                selection = selection,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/**
 * 选中框本体 (8 个手柄 + 拖动响应).
 */
@Composable
private fun SelectionBox(
    selection: Selection,
    handleSizePx: Float,
    tool: EditorTool,
    onTranslate: (Float, Float) -> Unit,
) {
    val strokeColor = MaterialTheme.colorScheme.primary
    val handleColor = Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                val b = Rect(
                    left = selection.bounds.left + selection.translationX,
                    top = selection.bounds.top + selection.translationY,
                    right = selection.bounds.right + selection.translationX,
                    bottom = selection.bounds.bottom + selection.translationY,
                )
                // 阴影
                drawRect(
                    color = Color(0x30000000),
                    topLeft = Offset(b.left - 2f, b.top - 2f),
                    size = Size(b.width + 4f, b.height + 4f),
                )
                // 主框 (虚线)
                drawDashedRect(
                    color = strokeColor,
                    topLeft = Offset(b.left, b.top),
                    size = Size(b.width, b.height),
                    strokeWidth = 2f,
                    dashLen = 8f,
                    gapLen = 4f,
                )
                // 8 个手柄
                val handles = buildHandles(b, handleSizePx)
                handles.forEach { h ->
                    drawRect(
                        color = handleColor,
                        topLeft = Offset(h.center.x - h.size / 2f, h.center.y - h.size / 2f),
                        size = Size(h.size, h.size),
                    )
                    drawRect(
                        color = strokeColor,
                        topLeft = Offset(h.center.x - h.size / 2f, h.center.y - h.size / 2f),
                        size = Size(h.size, h.size),
                        style = Stroke(width = 1.5f),
                    )
                }
            }
            .pointerInput(selection.nodeId, tool) {
                if (tool == EditorTool.Drag) {
                    detectDragGestures(
                        onDragStart = { },
                        onDragEnd = { },
                        onDragCancel = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onTranslate(dragAmount.x, dragAmount.y)
                        },
                    )
                }
            }
    )
}

/**
 * 在 DrawScope 上画一个虚线矩形.
 */
private fun DrawScope.drawDashedRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    strokeWidth: Float,
    dashLen: Float,
    gapLen: Float,
) {
    val right = topLeft.x + size.width
    val bottom = topLeft.y + size.height
    fun dashH(y: Float, xStart: Float, xEnd: Float) {
        var x = xStart
        while (x < xEnd) {
            val endX = (x + dashLen).coerceAtMost(xEnd)
            drawLine(color, Offset(x, y), Offset(endX, y), strokeWidth = strokeWidth)
            x += dashLen + gapLen
        }
    }
    fun dashV(x: Float, yStart: Float, yEnd: Float) {
        var y = yStart
        while (y < yEnd) {
            val endY = (y + dashLen).coerceAtMost(yEnd)
            drawLine(color, Offset(x, y), Offset(x, endY), strokeWidth = strokeWidth)
            y += dashLen + gapLen
        }
    }
    dashH(topLeft.y, topLeft.x, right)
    dashH(bottom, topLeft.x, right)
    dashV(topLeft.x, topLeft.y, bottom)
    dashV(right, topLeft.y, bottom)
}

/**
 * 元素名标签 (左上角).
 */
@Composable
private fun SelectionLabel(
    name: String,
    selection: Selection,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val xDp = with(density) { (selection.bounds.left + selection.translationX).toDp() }
    val yDp = with(density) { (selection.bounds.top + selection.translationY - 24f).toDp() }
    Box(
        modifier = modifier
            .offset(x = xDp, y = yDp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * 尺寸工具提示.
 */
@Composable
private fun SizeTooltip(
    selection: Selection,
    modifier: Modifier = Modifier,
) {
    val widthDp = selection.width / 3f
    val heightDp = selection.height / 3f
    Box(
        modifier = modifier
            .padding(top = 4.dp)
            .background(Color(0xCC000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "%.0f × %.0f dp · (%d,%d)".format(
                widthDp, heightDp,
                (selection.bounds.left + selection.translationX).toInt(),
                (selection.bounds.top + selection.translationY).toInt(),
            ),
            color = Color.White,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * 颜色工具提示 (Eyedropper 模式下显示).
 */
@Composable
private fun ColorTooltip(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(top = 4.dp)
            .background(Color(0xCC000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, RoundedCornerShape(2.dp))
                .border(1.dp, Color.White, RoundedCornerShape(2.dp)),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "#%02X%02X%02X".format(
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt(),
            ),
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
