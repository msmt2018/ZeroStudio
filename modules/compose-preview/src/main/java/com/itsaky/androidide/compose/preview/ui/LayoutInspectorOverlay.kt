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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.compose.preview.data.model.LayoutNodeSnapshot

/**
 * 布局检查器覆盖层 v3.3.
 *
 * 调试模式 + 分析模式开启时, 在 preview 内容上画:
 * - 每个布局节点的虚线矩形
 * - 节点类型名 + 深度角标
 * - 选中节点用实线 + 高亮色
 * - 隐藏节点用删除线 / 灰色
 * - Recomposition 高亮 (如果开启)
 *
 * 点击节点触发 [onNodeClick].
 */
@Composable
fun LayoutInspectorOverlay(
    snapshot: LayoutNodeSnapshot?,
    selectedNodeId: String?,
    isInspectorActive: Boolean,
    showRecomposition: Boolean,
    onNodeClick: (LayoutNodeSnapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isInspectorActive || snapshot == null) return

    val density = LocalDensity.current
    val outlineColor = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val hiddenColor = Color(0xFFB71C1C)
    val recomposeColor = Color(0xFFFFA000)

    val strokeWidthPx = with(density) { 1.dp.toPx() }
    val selectedStrokeWidthPx = with(density) { 2.5.dp.toPx() }
    val dashOnPx = with(density) { 4.dp.toPx() }
    val dashOffPx = with(density) { 3.dp.toPx() }
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(dashOnPx, dashOffPx), 0f)
    val labelBgRadius = with(density) { 3.dp.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
        // 画虚线 + 标签
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSnapshot(
                root = snapshot,
                selectedId = selectedNodeId,
                showRecomposition = showRecomposition,
                outlineColor = outlineColor,
                selectedColor = selectedColor,
                hiddenColor = hiddenColor,
                recomposeColor = recomposeColor,
                strokeWidthPx = strokeWidthPx,
                selectedStrokeWidthPx = selectedStrokeWidthPx,
                dashEffect = dashEffect,
            )
        }

        // 节点标签 — 用 Text 组件叠在 Canvas 上, 选中节点 / 隐藏节点
        DrawNodeLabels(
            root = snapshot,
            selectedId = selectedNodeId,
            density = density,
        )

        // 点击手势 — 计算哪个节点被点了
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(snapshot.id, selectedNodeId) {
                    detectTapGestures { offset ->
                        val node = findNodeAt(snapshot, offset.x.toInt(), offset.y.toInt())
                        if (node != null) {
                            onNodeClick(node)
                        }
                    }
                }
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnapshot(
    root: LayoutNodeSnapshot,
    selectedId: String?,
    showRecomposition: Boolean,
    outlineColor: Color,
    selectedColor: Color,
    hiddenColor: Color,
    recomposeColor: Color,
    strokeWidthPx: Float,
    selectedStrokeWidthPx: Float,
    dashEffect: PathEffect,
) {
    fun drawNode(node: LayoutNodeSnapshot) {
        if (node.width <= 0 || node.height <= 0) {
            // 跳过零尺寸节点
            node.children.forEach { drawNode(it) }
            return
        }
        val rect = Rect(Offset(node.left, node.top), Size(node.width, node.height))
        val isSelected = node.id == selectedId
        val isHidden = node.isHidden

        val color = when {
            isSelected -> selectedColor
            isHidden -> hiddenColor
            showRecomposition && node.recomposed -> recomposeColor
            else -> outlineColor
        }
        val strokeWidth = if (isSelected) selectedStrokeWidthPx else strokeWidthPx
        val effect = if (isSelected) null else dashEffect

        drawRect(
            color = color,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            style = Stroke(width = strokeWidth, pathEffect = effect),
        )

        // 隐藏节点: 画斜线
        if (isHidden) {
            drawLine(
                color = hiddenColor,
                start = Offset(rect.left, rect.top),
                end = Offset(rect.right, rect.bottom),
                strokeWidth = strokeWidthPx,
            )
            drawLine(
                color = hiddenColor,
                start = Offset(rect.right, rect.top),
                end = Offset(rect.left, rect.bottom),
                strokeWidth = strokeWidthPx,
            )
        }

        // 递归子节点
        node.children.forEach { drawNode(it) }
    }
    drawNode(root)
}

@Composable
private fun DrawNodeLabels(
    root: LayoutNodeSnapshot,
    selectedId: String?,
    density: androidx.compose.ui.unit.Density,
) {
    // 用 Box 叠层显示每个节点的 type 标签 (top-left corner)
    val cornerOffset = with(density) { 4.dp.toPx() }
    val nodes = remember(root.id, selectedId) { flatten(root) }
    Box(modifier = Modifier.fillMaxSize()) {
        nodes.forEach { node ->
            // 只画 selected 节点 + 顶层节点
            if (node.id == selectedId || (node.depth <= 1 && node.width > 0)) {
                val isSelected = node.id == selectedId
                val bgColor = if (isSelected) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = with(density) { (node.left / density.density).dp + 2.dp },
                            y = with(density) { (node.top / density.density).dp + 2.dp },
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = bgColor,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "${node.typeName}${if (isSelected) " ✓" else ""}",
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun findNodeAt(root: LayoutNodeSnapshot, x: Int, y: Int): LayoutNodeSnapshot? {
    // DFS 自底向上找 — 子节点优先
    fun dfs(node: LayoutNodeSnapshot): LayoutNodeSnapshot? {
        for (child in node.children.reversed()) {
            dfs(child)?.let { return it }
        }
        if (x.toFloat() in node.left..node.right && y.toFloat() in node.top..node.bottom) {
            return node
        }
        return null
    }
    return dfs(root)
}

private fun flatten(root: LayoutNodeSnapshot): List<LayoutNodeSnapshot> {
    val out = mutableListOf<LayoutNodeSnapshot>()
    fun dfs(node: LayoutNodeSnapshot) {
        out.add(node)
        node.children.forEach { dfs(it) }
    }
    dfs(root)
    return out
}
