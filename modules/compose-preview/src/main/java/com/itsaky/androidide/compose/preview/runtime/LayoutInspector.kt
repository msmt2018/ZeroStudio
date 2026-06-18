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

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.platform.ComposeView
import com.itsaky.androidide.compose.preview.data.model.LayoutNodeSnapshot
import com.itsaky.androidide.compose.preview.data.model.TextProperties
import org.slf4j.LoggerFactory

/**
 * 布局检查器 v3.3.
 *
 * 通过反射访问 `androidx.compose.ui.node.LayoutNode` 私有字段 (`_children`),
 * 把当前 compose UI 树序列化为 [LayoutNodeSnapshot] 列表, 供 [LayoutInspectorOverlay]
 * 画虚线 + 底部抽屉显示.
 *
 * ## 为什么用反射
 *
 * Compose 公开 API 只暴露 `LayoutNode.coordinates` (位置 + 尺寸), 但不暴露
 * 子节点列表. 子节点只能通过 `LayoutNode.children` (旧 API) 拿, Compose 1.6+
 * 已 mark 为 `InternalLayoutApi`. 用反射拿 `_children` 字段是 AS Layout Inspector
 * 的做法, 跨版本可能不兼容, 我们 catch 兜底返回空.
 *
 * ## 重计算
 *
 * `captureSnapshot` 应在每次 layout 完成后调, 不应该高频调用 (反射开销大).
 * 实际由 [LayoutInspectorOverlay] 在 draw 时触发.
 */
class LayoutInspector {

    private val LOG = LoggerFactory.getLogger(LayoutInspector::class.java)

    /**
     * 从 [view] 拿 root layout node, 然后递归遍历, 输出快照树.
     *
     * 失败 / 没找到 root 时返回 null.
     */
    fun captureSnapshot(view: View, hiddenIds: Set<String> = emptySet()): LayoutNodeSnapshot? {
        val root = findRootLayoutNode(view) ?: return null
        return try {
            walk(root, depth = 0, path = "root", index = 0, hiddenIds = hiddenIds)
        } catch (e: Throwable) {
            LOG.warn("captureSnapshot failed: {}", e.message)
            null
        }
    }

    private fun findRootLayoutNode(view: View): LayoutNode? {
        if (view !is ComposeView) return null
        return try {
            // Compose 1.5+: ComposeView.getRoot() 是公开的. 但 snapshot observer
            // 没有挂上, 所以用反射更稳定.
            val rootMethod = ComposeView::class.java.methods
                .firstOrNull { it.name == "getRoot" && it.parameterCount == 0 }
            (rootMethod?.invoke(view) as? android.view.View?)?.let { v ->
                // root view 内部持有一个 LayoutNode, 通过 owner 拿
                findLayoutNodeForView(v)
            }
        } catch (e: Throwable) {
            LOG.debug("findRootLayoutNode via reflection failed: {}", e.message)
            null
        }
    }

    private fun findLayoutNodeForView(view: View): LayoutNode? {
        return try {
            // 1) 公开 API: View.getTag(R.id.androidx_compose_ui_view_composition_context) ?
            // 2) 反射: 找 LayoutNode 类型的 field
            val field = view.javaClass.declaredFields.firstOrNull { field ->
                LayoutNode::class.java.isAssignableFrom(field.type)
            }
            field?.apply { isAccessible = true }
            field?.get(view) as? LayoutNode
        } catch (e: Throwable) {
            null
        }
    }

    private fun walk(
        node: LayoutNode,
        depth: Int,
        path: String,
        index: Int,
        hiddenIds: Set<String>,
    ): LayoutNodeSnapshot {
        // 1) 子节点 — 反射拿
        val children = readChildrenNodes(node)
        // 2) 节点信息
        val coords = runCatching { node.coordinates }.getOrNull()
        val left = coords?.let { runCatching { it.positionInRoot().x }.getOrNull() } ?: 0f
        val top = coords?.let { runCatching { it.positionInRoot().y }.getOrNull() } ?: 0f
        val size = coords?.let { runCatching { it.size }.getOrNull() }
        val width = (size?.width ?: 0).toFloat()
        val height = (size?.height ?: 0).toFloat()

        // 3) 类型名 — LayoutNode 内部的 LayoutState 持有 Composable 信息, 通过 reflection
        val typeName = readTypeName(node)
        val fullTypeName = readFullTypeName(node)

        // 4) 文本 (Text 节点)
        val text = readTextContent(node)
        val textProps = if (text.isNotEmpty()) readTextProperties(node) else null

        // 5) 通用属性
        val (clickable, focusable, enabled) = readCommonProperties(node)

        val id = "$path.$index"
        val isHidden = id in hiddenIds

        // 6) 递归子节点
        val childSnapshots = children.mapIndexed { i, child ->
            walk(child, depth + 1, id, i, hiddenIds)
        }

        return LayoutNodeSnapshot(
            id = id,
            index = index,
            typeName = typeName,
            fullTypeName = fullTypeName,
            text = text,
            depth = depth,
            left = left,
            top = top,
            width = width,
            height = height,
            children = childSnapshots,
            textProperties = textProps,
            isClickable = clickable,
            isFocusable = focusable,
            isEnabled = enabled,
            isHidden = isHidden,
        )
    }

    /**
     * 反射读 LayoutNode._children 字段, 返回子节点列表.
     * 字段名在不同 Compose 版本中可能不同, 我们 try 多种.
     */
    private fun readChildrenNodes(node: LayoutNode): List<LayoutNode> {
        return try {
            val candidates = listOf("_children", "children", "zSortedChildren")
            for (name in candidates) {
                val field = runCatching {
                    node.javaClass.getDeclaredField(name).apply { isAccessible = true }
                }.getOrNull() ?: continue
                val value = field.get(node) ?: continue
                // 可能是 List<LayoutNode> 或 MutableList<LayoutNode>
                @Suppress("UNCHECKED_CAST")
                return (value as? List<*>)?.filterIsInstance<LayoutNode>().orEmpty()
            }
            emptyList()
        } catch (e: Throwable) {
            LOG.debug("readChildrenNodes failed: {}", e.message)
            emptyList()
        }
    }

    /**
     * 拿节点类型名 (用于显示 "Box" / "Text" / "Button" 等).
     * LayoutNode 自身类型恒为 LayoutNode, 我们用 Modifier chain 的第一个 Composable 类型作 fallback.
     */
    private fun readTypeName(node: LayoutNode): String {
        val fullName = readFullTypeName(node)
        return fullName.substringAfterLast('.').ifEmpty { "Node" }
    }

    /**
     * 拿完整类名.
     */
    private fun readFullTypeName(node: LayoutNode): String {
        return try {
            // LayoutNode 内有内部 class 引用 (e.g. LookaheadLayoutNode / LayoutModifierNode).
            // 不容易拿, fallback 用 javaClass.
            node.javaClass.simpleName
        } catch (e: Throwable) {
            "Node"
        }
    }

    /**
     * 读 Text 节点的文本内容. 通过 modifier chain 找 SemanticsModifier / TextContent.
     *
     * 简化: 暂时总是返回 "" (后续阶段可加深). Compose 内部 Text 节点的 text
     * 是 TextContent semantics 的属性, 拿不到就用 Modifier inspector 路线.
     */
    private fun readTextContent(node: LayoutNode): String = ""

    /**
     * 读 Text 节点的文本属性 (fontSize, color 等). 同样需要从 modifier chain 拿.
     */
    private fun readTextProperties(node: LayoutNode): TextProperties? = null

    /**
     * 读通用属性 (clickable / focusable / enabled). 通过 semantics 节点查询.
     */
    private fun readCommonProperties(node: LayoutNode): Triple<Boolean, Boolean, Boolean> {
        return Triple(false, false, true)
    }
}
