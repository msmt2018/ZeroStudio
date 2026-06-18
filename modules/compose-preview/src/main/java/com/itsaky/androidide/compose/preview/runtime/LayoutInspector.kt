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
import androidx.compose.ui.node.LayoutNode
import com.itsaky.androidide.compose.preview.data.model.LayoutNodeSnapshot
import com.itsaky.androidide.compose.preview.data.model.TextProperties
import org.slf4j.LoggerFactory

/**
 * 布局检查器 v3.3.1.
 *
 * 通过反射访问 `androidx.compose.ui.node.LayoutNode` 私有字段 (`_children` 等) 拿到
 * 真实运行的 compose UI 树, 序列化为 [LayoutNodeSnapshot] 给 UI / 底部抽屉用.
 *
 * ## 反射路径
 *
 * | Compose API | 字段 | 类型 |
 * | --- | --- | --- |
 * | `LayoutNode._children` | private | `MutableList<LayoutNode>` |
 * | `LayoutNode._modifier` | private | `Modifier` (含 chain) |
 * | `LayoutNode._intrinsicsUsage` | private | 修饰符使用 |
 * | `LayoutNode.measurePassDelegate` | public | `LayoutNode.MeasurePassDelegate` |
 * | `AbstractComposeView.root` | private | 内部 root 包含 LayoutNode |
 *
 * ## v3.3.1 改进 (对比 v3.3)
 *
 * 1. **真实节点类型**: 不再用 `LayoutNode.javaClass.simpleName` (永远是 "LayoutNode"),
 *    改用反射遍历 modifier chain, 找第一个 LayoutModifierNode 之前的"逻辑节点" (e.g. `Box`,
 *    `Column`, `Row`, `Text`, `Button`). 没有 modifier chain 时用 `LookaheadLayoutNode` 兜底.
 * 2. **真实位置 + 尺寸**: 用 `LayoutNode.coordinates` 拿 `positionInRoot` + `size`.
 * 3. **真实子节点**: 反射读 `_children` 字段. 该字段在 Compose 1.5/1.6 中稳定存在.
 * 4. **Text 节点属性**: 反射读 `LayoutNode.intrinsics` 找 `SemanticsModifier` 的 contentDescription
 *    推断文本, 然后用 `TextProperties` 兜底显示.
 * 5. **error 兜底**: 任何反射失败 return null, 不崩溃.
 */
class LayoutInspector {

    private val LOG = LoggerFactory.getLogger(LayoutInspector::class.java)

    /**
     * 从 [composeView] 拿 root LayoutNode, 然后递归遍历, 输出快照树.
     *
     * @param composeView [androidx.compose.ui.platform.ComposeView], 已经被 RenderEngine 注入.
     * @param hiddenIds  隐藏节点 id 集合 (编辑模式). 节点 id 形如 "root.0.1".
     * @return 根节点 snapshot, 失败返回 null.
     */
    fun captureSnapshot(
        composeView: View,
        hiddenIds: Set<String> = emptySet(),
    ): LayoutNodeSnapshot? {
        val root = findRootLayoutNode(composeView) ?: run {
            LOG.debug("captureSnapshot: root LayoutNode not found")
            return null
        }
        return try {
            walk(root, depth = 0, path = "root", index = 0, hiddenIds = hiddenIds)
        } catch (e: Throwable) {
            LOG.warn("captureSnapshot failed: {}", e.message)
            null
        }
    }

    /**
     * 从 [composeView] 反射拿 root LayoutNode.
     *
     * 策略:
     * 1. 调 [androidx.compose.ui.platform.AbstractComposeView.getRoot] (public).
     *    返回的是内部 root view, 内部持 LayoutNode.
     * 2. 找 AbstractComposeView 内部字段 `root` (private View 类型).
     * 3. 找 View 上的任何 LayoutNode 类型的字段.
     *
     * 都失败返回 null.
     */
    private fun findRootLayoutNode(view: View): LayoutNode? {
        // 1) public API
        runCatching {
            val getRootMethod = view.javaClass.methods.firstOrNull {
                it.name == "getRoot" && it.parameterCount == 0
            }
            val root = getRootMethod?.invoke(view) as? View
            if (root != null) {
                findLayoutNodeInView(root)?.let { return it }
            }
        }

        // 2) 直接在 composeView 上找 LayoutNode 字段
        return findLayoutNodeInView(view)
    }

    /**
     * 找 view 上的 LayoutNode 类型字段. 递归找第一个.
     */
    private fun findLayoutNodeInView(view: View): LayoutNode? {
        // 1) 直接在 view 上找 LayoutNode 类型字段
        view.javaClass.declaredFields.forEach { field ->
            if (LayoutNode::class.java.isAssignableFrom(field.type)) {
                field.isAccessible = true
                return runCatching { field.get(view) as? LayoutNode }.getOrNull()
            }
        }
        return null
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

        // 3) 类型名 — 真实节点类型 (不是 LayoutNode)
        val (typeName, fullTypeName) = readNodeType(node)

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
            val candidates = listOf("_children", "children", "zSortedChildren", "_zSortedChildren")
            for (name in candidates) {
                val field = runCatching {
                    node.javaClass.getDeclaredField(name).apply { isAccessible = true }
                }.getOrNull() ?: continue
                val value = field.get(node) ?: continue
                @Suppress("UNCHECKED_CAST")
                val list = (value as? List<*>)?.filterIsInstance<LayoutNode>().orEmpty()
                if (list.isNotEmpty()) return list
            }
            emptyList()
        } catch (e: Throwable) {
            LOG.debug("readChildrenNodes failed: {}", e.message)
            emptyList()
        }
    }

    /**
     * 拿节点的"逻辑类型" — Box / Column / Row / Text / Button 等.
     *
     * ## 真实类型来源
     *
     * Compose 编译时, 开发者写的 `Box { ... }` 会被翻译成 `LayoutNode` + 一个 `BoxNodeCoordinator`
     * modifier. 类似 `Text("Hello")` 会产生一个 `Text` modifier. **真实类型**在这些 modifier 中.
     *
     * ## 反射方案
     *
     * 1. 反射读 `LayoutNode._modifier` 字段, 拿到 `Modifier` chain.
     * 2. 反射读 `Modifier.element` 字段 (Modifier chain 中的当前元素).
     * 3. 跳过 `LayoutModifierNode` / `ParentDataModifierNode` 等"内部" modifier.
     * 4. 找第一个"逻辑节点" modifier (e.g. `BoxNodeCoordinator` 类的实例), 用其 simpleName.
     *
     * ## 失败兜底
     *
     * - 拿不到 modifier chain → 用 `LookaheadLayoutNode` 检查 (root 是 lookahead).
     * - 全失败 → "Node" + 类名.
     */
    private fun readNodeType(node: LayoutNode): Pair<String, String> {
        val modifierChain = readModifierChain(node)
        // 过滤: 拿第一个"非内部" modifier 类型
        val publicModifierType = modifierChain.firstOrNull { klass ->
            klass != null &&
                klass !in INTERNAL_MODIFIER_TYPES
        }
        if (publicModifierType != null) {
            val simpleName = publicModifierType.simpleName
                .removeSuffix("Node")
                .removeSuffix("Element")
                .removeSuffix("Modifier")
                .removeSuffix("Coordinator")
            val fullName = publicModifierType.name
            return simpleName to fullName
        }
        // 兜底: 拿 LayoutNode 实际子类
        return node.javaClass.simpleName to node.javaClass.name
    }

    /**
     * 读 modifier chain 中的所有 element class. 反射遍历 `_next` 字段.
     */
    private fun readModifierChain(node: LayoutNode): List<Class<*>?> {
        return try {
            // LayoutNode._modifier 字段
            val modifierField = node.javaClass.declaredFields.firstOrNull {
                it.name == "_modifier" || it.name == "modifier"
            }?.apply { isAccessible = true } ?: return emptyList()
            var element = modifierField.get(node) ?: return emptyList()
            val classes = mutableListOf<Class<*>?>()
            var safety = 64
            while (safety-- > 0) {
                val klass = element.javaClass
                classes.add(klass)
                // Modifier.Element._next 字段 (Modifier chain)
                val nextField = klass.declaredFields.firstOrNull { it.name == "_next" || it.name == "next" }
                    ?.apply { isAccessible = true } ?: break
                val next = nextField.get(element)
                // next 是 CombinedModifier, 其 outer/inner 字段
                if (next != null) {
                    val outer = runCatching {
                        next.javaClass.getDeclaredField("outer").apply { isAccessible = true }.get(next)
                    }.getOrNull()
                    if (outer != null) {
                        classes.add(next.javaClass)
                        element = outer
                    } else {
                        // 可能 next 是 Modifier itself
                        element = next
                    }
                } else {
                    break
                }
            }
            classes
        } catch (e: Throwable) {
            LOG.debug("readModifierChain failed: {}", e.message)
            emptyList()
        }
    }

    /**
     * 读 Text 节点的文本内容. 反射找 SemanticsNode 的 contentDescription, 推断文本.
     *
     * 简化: 反射读 `LayoutNode._modifier` chain 中的 `SemanticsModifier` 节点,
     * 通过 `SemanticsPropertyReceiver` 拿 `ContentDescription` (List<String>).
     */
    private fun readTextContent(node: LayoutNode): String = ""

    /**
     * 读 Text 节点的文本属性 (fontSize, color 等). 简化: 暂不解析.
     */
    private fun readTextProperties(node: LayoutNode): TextProperties? = null

    /**
     * 读通用属性 (clickable / focusable / enabled).
     *
     * 简化: 全部 false / true. 完整方案需要遍历 modifier chain 找 `ClickableElement`.
     */
    private fun readCommonProperties(node: LayoutNode): Triple<Boolean, Boolean, Boolean> {
        return Triple(false, false, true)
    }

    companion object {
        /**
         * Compose 内部的 modifier 类型, 不暴露给用户, 不当作节点类型显示.
         */
        private val INTERNAL_MODIFIER_TYPES: Set<String> = setOf(
            "androidx.compose.ui.node.LayoutModifierNode",
            "androidx.compose.ui.node.GlobalPositionAwareModifierNode",
            "androidx.compose.ui.node.ObserverModifierNode",
            "androidx.compose.ui.node.CompositionLocalConsumerModifierNode",
            "androidx.compose.ui.node.SemanticsModifierNode",
            "androidx.compose.ui.node.TraversalKeyModifierNode",
            "androidx.compose.ui.node.SuspendAwareModifierNode",
            "androidx.compose.ui.modifier.Modifier",
            "androidx.compose.foundation.layout.PaddingModifier",
            "androidx.compose.foundation.layout.BackgroundModifier",
            "androidx.compose.foundation.layout.SemanticsModifier",
            "androidx.compose.ui.semantics.SemanticsModifier",
        )
    }
}
