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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Composable 节点信息 v2.1.
 *
 * 通过反射 [androidx.compose.ui.node.LayoutNode] 提取:
 * - bounds (相对屏幕)
 * - 宽高 (px / dp)
 * - 父节点 (用于层级展示)
 * - modifier 链 (简化, 只取类型名)
 *
 * @property id 节点 id (系统分配)
 * @property composableName 推测的 Composable 名 (取自 stack)
 * @property bounds 边界 (相对父节点)
 * @property widthPx / heightPx
 * @property childrenIds 子节点 id
 */
data class NodeInfo(
    val id: Int,
    val composableName: String,
    val bounds: Rect,
    val widthPx: Int,
    val heightPx: Int,
    val childrenIds: List<Int>,
) {
    val widthDp: Float get() = widthPx / 3f
    val heightDp: Float get() = heightPx / 3f
}

/**
 * LayoutNode 反射读取器 v2.1.
 *
 * 由于 LayoutNode 的 fields / methods 是 internal, 用反射访问.
 * Compose 1.5+ 大致字段:
 * - id: Long
 * - coordinates: LayoutCoordinates
 * - measuredWidth: Int
 * - measuredHeight: Int
 * - children: List<LayoutNode>
 *
 * 如果任何字段读不到就降级, 不抛异常.
 */
object LayoutNodeInspector {

    private val LOG = LoggerFactory.getLogger(LayoutNodeInspector::class.java)

    private val idField: Field? = findFieldRecursive("androidx.compose.ui.node.LayoutNode", "id")
    private val coordinatesField: Field? = findFieldRecursive("androidx.compose.ui.node.LayoutNode", "coordinates")
    private val widthField: Field? = findFieldRecursive("androidx.compose.ui.node.LayoutNode", "measuredWidth")
    private val heightField: Field? = findFieldRecursive("androidx.compose.ui.node.LayoutNode", "measuredHeight")
    private val childrenField: Field? = findFieldRecursive("androidx.compose.ui.node.LayoutNode", "children")
    private val coordBoundsField: Field? = findFieldRecursive("androidx.compose.ui.layout.LayoutCoordinates", "bounds")
    private val coordSizeField: Field? = findFieldRecursive("androidx.compose.ui.layout.LayoutCoordinates", "size")
    private val coordPositionField: Field? = findFieldRecursive("androidx.compose.ui.layout.LayoutCoordinates", "position")

    /**
     * 从 [rootNode] (AndroidX LayoutNode) 递归收集所有节点信息.
     */
    @Suppress("UNCHECKED_CAST")
    fun collectNodes(rootNode: Any): List<NodeInfo> {
        val result = mutableListOf<NodeInfo>()
        try {
            visitNode(rootNode, result, depth = 0)
        } catch (e: Throwable) {
            LOG.warn("collectNodes failed: {}", e.message)
        }
        return result
    }

    private fun visitNode(node: Any, out: MutableList<NodeInfo>, depth: Int) {
        if (depth > 30) return // 防御性

        val id = readIntField(node, idField) ?: return
        val bounds = readBounds(node) ?: Rect.Zero
        val w = readIntField(node, widthField) ?: 0
        val h = readIntField(node, heightField) ?: 0
        val children = readChildrenIds(node)

        val name = guessComposableName(node)

        out.add(NodeInfo(
            id = id,
            composableName = name,
            bounds = bounds,
            widthPx = w,
            heightPx = h,
            childrenIds = children,
        ))

        val childrenList = readChildrenList(node) ?: return
        for (c in childrenList) {
            try {
                visitNode(c, out, depth + 1)
            } catch (e: Throwable) {
                LOG.debug("visitNode child failed: {}", e.message)
            }
        }
    }

    private fun readBounds(node: Any): Rect? {
        val coords = readFieldSafely(node, coordinatesField) ?: return null
        val rectAny = readFieldSafely(coords, coordBoundsField) ?: return null
        if (rectAny is Rect) return rectAny
        // Rect 在不同 Compose 版本中可能是 Rect (公开) 或内部类
        return try {
            val leftF = readFloatFieldSafely(rectAny, "left") ?: 0f
            val topF = readFloatFieldSafely(rectAny, "top") ?: 0f
            val rightF = readFloatFieldSafely(rectAny, "right") ?: 0f
            val bottomF = readFloatFieldSafely(rectAny, "bottom") ?: 0f
            Rect(leftF, topF, rightF, bottomF)
        } catch (_: Throwable) {
            null
        }
    }

    private fun readChildrenIds(node: Any): List<Int> {
        val list = readChildrenList(node) ?: return emptyList()
        return list.mapNotNull { readIntField(it, idField) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readChildrenList(node: Any): List<Any>? {
        val raw = readFieldSafely(node, childrenField) ?: return null
        return when (raw) {
            is List<*> -> raw.filterNotNull()
            is Iterable<*> -> raw.filterNotNull()
            else -> null
        }
    }

    private fun readIntField(obj: Any, field: Field?): Int? {
        if (field == null) return null
        return try {
            (field.get(obj) as? Number)?.toInt()
        } catch (_: Throwable) {
            null
        }
    }

    private fun readFieldSafely(obj: Any, field: Field?): Any? {
        if (field == null) return null
        return try {
            field.isAccessible = true
            field.get(obj)
        } catch (_: Throwable) {
            null
        }
    }

    private fun readFloatFieldSafely(obj: Any, fieldName: String): Float? {
        return try {
            val f = obj.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
            (f.get(obj) as? Number)?.toFloat()
        } catch (_: Throwable) {
            null
        }
    }

    private fun guessComposableName(node: Any): String {
        // 取类名
        val cls = node.javaClass.name
        val simple = cls.substringAfterLast('.').substringAfterLast('$')
        return simple.ifBlank { "Unknown" }
    }

    private fun findFieldRecursive(className: String, fieldName: String): Field? {
        return try {
            val cls = Class.forName(className)
            findFieldInHierarchy(cls, fieldName)
        } catch (_: ClassNotFoundException) {
            LOG.debug("Class not found: {} (可能不在 classpath)", className)
            null
        } catch (e: Throwable) {
            LOG.debug("findFieldRecursive error: {}", e.message)
            null
        }
    }

    private fun findFieldInHierarchy(cls: Class<*>, name: String): Field? {
        var c: Class<*>? = cls
        while (c != null) {
            try {
                return c.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return null
    }
}

/**
 * Component Inspector 面板 v2.1.
 *
 * 显示节点树 + 选中节点属性.
 */
@Composable
fun ComponentInspectorPanel(
    nodes: List<NodeInfo>,
    selectedId: Int? = null,
    onSelect: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${nodes.size} 个节点" + (selectedId?.let { " · 选中 #$it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        if (nodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101015)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无节点 — 调用 LayoutNodeInspector.collectNodes(root) 获取",
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101015))
            ) {
                items(nodes, key = { it.id }) { node ->
                    val isSelected = selectedId == node.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) Color(0x30FFFFFF) else Color.Transparent
                            )
                            .clickable { onSelect(node.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "#${node.id}",
                            color = Color(0xFF888888),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(50.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = node.composableName,
                            color = Color(0xFFE0E0E0),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${node.widthPx}×${node.heightPx}",
                            color = Color(0xFF888888),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    HorizontalDivider(color = Color(0x10FFFFFF))
                }
            }
        }
    }
}

/**
 * 在 content 周围画边界框 (调试用).
 */
@Composable
fun Modifier.layoutBoundsOverlay(
    visible: Boolean,
    color: Color = Color(0xFFE57373),
    widthDp: Float = 1f,
): Modifier {
    if (!visible) return this
    return this.drawWithContent {
        drawContent()
        drawRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            style = Stroke(width = widthDp * density),
        )
    }
}
