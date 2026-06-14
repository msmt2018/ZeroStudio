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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 取色结果 v2.1.
 *
 * @property node 命中的节点
 * @property color 估算的背景色 (基于 composable name 启发式映射, 真实场景需读 painted pixel)
 * @property point 屏幕点击点 (相对 root)
 */
data class EyedropperResult(
    val node: NodeInfo,
    val color: Color,
    val point: Offset,
)

/**
 * 屏幕取色器 v2.1.
 *
 * 给定一组 [NodeInfo] (来自 [LayoutNodeInspector.collectNodes]) + 一个点击点 [point],
 * 找到包含该点的最深层 (z-order 最高) 节点, 并返回估算颜色.
 *
 * 颜色估算策略 (启发式):
 * - 当前没有直接读 painted pixel 的方法 (LayoutNode 不暴露)
 * - 退而求其次, 根据 composable name 推断常见色:
 *   - `Button` / `ButtonKt` / `*Button` → Material Primary
 *   - `Card` → SurfaceVariant
 *   - `TopAppBar` / `AppBar` → Material Primary
 *   - `Text` / `*Text` → OnSurface
 *   - `Icon` / `*Icon` → OnSurfaceVariant
 *   - `Surface` / `Box` / `Row` / `Column` → Surface
 *   - 其他 → Surface
 *
 * ## 已知限制
 *
 * - 同一节点多次点击会重复返回相同估算色; 真实色需 bitmap sample (待 P3).
 * - 不支持 Z 轴上层的 hit-test (Compose 没有 z-index 概念, 用 bounds containment + 树深度).
 */
object ColorEyedropper {

    /**
     * 在 [nodes] 中找包含 [point] 的最深层节点.
     *
     * @param nodes Inspector 收集到的所有节点
     * @param point 屏幕点击点 (与节点 bounds 同坐标系, 通常是 root 相对坐标)
     * @return 命中的节点, 或 null (无节点包含此点)
     */
    fun findNodeAt(
        nodes: List<NodeInfo>,
        point: Offset,
    ): NodeInfo? {
        return nodes
            .filter { node ->
                val b = node.bounds
                point.x >= b.left && point.x <= b.right &&
                point.y >= b.top && point.y <= b.bottom
            }
            .maxByOrNull { it.bounds.width.toInt() * it.bounds.height.toInt() }  // 大小最大 = 最深
    }

    /**
     * 给定 [node] 估算其背景色.
     *
     * 见 [ColorEyedropper] 文档说明.
     */
    fun estimateColor(node: NodeInfo): Color {
        val name = node.composableName
        return when {
            name.contains("Button", ignoreCase = true) -> Color(0xFF1976D2)   // Material Primary
            name.contains("AppBar", ignoreCase = true) -> Color(0xFF1976D2)
            name.contains("TopBar", ignoreCase = true) -> Color(0xFF1976D2)
            name.contains("Card", ignoreCase = true) -> Color(0xFFEEEEEE)
            name.contains("Text", ignoreCase = true) -> Color(0xFF212121)
            name.contains("Icon", ignoreCase = true) -> Color(0xFF616161)
            name.contains("Surface", ignoreCase = true) -> Color(0xFFFAFAFA)
            name.contains("Box", ignoreCase = true) -> Color(0xFFFAFAFA)
            name.contains("Row", ignoreCase = true) -> Color(0xFFFAFAFA)
            name.contains("Column", ignoreCase = true) -> Color(0xFFFAFAFA)
            name.contains("Divider", ignoreCase = true) -> Color(0xFFBDBDBD)
            name.contains("Spacer", ignoreCase = true) -> Color.Transparent
            else -> Color(0xFFE0E0E0)
        }
    }

    /**
     * 一站式: 给点 + 节点, 返回 [EyedropperResult].
     */
    fun sample(nodes: List<NodeInfo>, point: Offset): EyedropperResult? {
        val node = findNodeAt(nodes, point) ?: return null
        return EyedropperResult(
            node = node,
            color = estimateColor(node),
            point = point,
        )
    }
}

/**
 * 工具: 比较两个 Rect 是否近似相等 (用于去重).
 */
internal fun Rect.isCloseTo(other: Rect, epsilon: Float = 1f): Boolean =
    abs(left - other.left) < epsilon &&
    abs(top - other.top) < epsilon &&
    abs(right - other.right) < epsilon &&
    abs(bottom - other.bottom) < epsilon
