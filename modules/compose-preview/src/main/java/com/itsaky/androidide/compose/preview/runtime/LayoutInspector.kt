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
import com.itsaky.androidide.compose.preview.data.model.LayoutNodeSnapshot
import com.itsaky.androidide.compose.preview.data.model.TextProperties
import org.slf4j.LoggerFactory

/**
 * 布局检查器 v3.5.1.
 *
 * v3.5.1 简化: 之前的 v3.3.1 实现通过反射 `androidx.compose.ui.node.LayoutNode`
 * 私有字段 (`_children` / `_modifier` 等) 拿真实 compose UI 树. 但 `LayoutNode`
 * 在 Compose 1.5+ 是 **internal class**, Kotlin 编译器拒绝外部 module 反射
 * 访问, 即使绕过 module 限制 (用 `setAccessible(true)`) 在新版本 Compose 也
 * 会抛 `InaccessibleObjectException`.
 *
 * 现在的方案改用**纯公开 API**:
 * 1. `SemanticsNode` 树 (通过 `ComposeView.findViewTreeCompositionContext()` +
 *    `androidx.compose.ui.platform.ViewCompositionStrategy` + `Owner.getSemanticsTree()`)
 *    是 public 的, 但需要在 IDE 主 APK 的 [androidx.compose.ui.platform.AndroidComposeView]
 *    之上访问, 模块边界不一定能拿到 `Owner` 引用.
 * 2. **简化策略**: 当前实现返回 null, 让 UI 走 "no snapshot" 路径, 显示
 *    "暂未实现" 占位. v3.6 之后用 `Modifier.testTag` + 自定义 recorder 包一层
 *    `setContent` 拿真实节点树, 不依赖反射.
 *
 * ## 为什么不能用 SemanticsNode
 *
 * `androidx.compose.ui.semantics.SemanticsNode` 是公开类, 但 `SemanticsNode.parent`
 * / `SemanticsNode.children` / `SemanticsNode.layoutInfo` 在 Compose 1.5+ 是
 * `internal`, 同样不可外部访问. 因此**全 compose 节点遍历**必须用反射或等
 * 官方公开的稳定 API 出来 (待 Compose 1.8+).
 *
 * ## 替代方案 (v3.6 路线)
 *
 * 1. 在 [com.itsaky.androidide.compose.preview.runtime.PreviewRenderEngine.setContent]
 *    之前包一层 `CompositionLocalProvider` 注入自定义 `CompositionLocal<LayoutRecorder>`,
 *    由用户在 Composable 里**显式**调 `LayoutRecorder.record(name)` 注册节点.
 *    这种"白名单"方案不依赖反射, 100% 稳定.
 * 2. 用 `Modifier.testTag` + Compose 公开的 `findChildTag` API (在新版 Compose
 *    1.8+ 提供 `LocalInspectionTables` + `currentCompositeKeyHash` 拿 hierarchy).
 *
 * 当前 stub: captureSnapshot() 永远返回 null.
 *
 * @see androidx.compose.ui.semantics.SemanticsNode
 * @see androidx.compose.ui.platform.ComposeView
 */
class LayoutInspector {

    private val LOG = LoggerFactory.getLogger(LayoutInspector::class.java)

    /**
     * 拿 [composeView] 的 layout snapshot.
     *
     * v3.5.1 简化实现: 永远返回 null. v3.6 之前 UI 显示 "未启用 layout inspection"
     * 占位. 历史 log 仍然保留 (debug 级别), 方便后续接入.
     *
     * @param composeView 已被 [com.itsaky.androidide.compose.preview.runtime.PreviewRenderEngine]
     *                    注入内容的 [androidx.compose.ui.platform.ComposeView]
     * @param hiddenIds 编辑模式下的隐藏节点 id 集合 (v3.5.1 暂未使用, 保留 API 兼容)
     * @return v3.5.1 永远 null; 后续版本接 SemanticsNode 后返回真实 root snapshot
     */
    fun captureSnapshot(
        composeView: View,
        hiddenIds: Set<String> = emptySet(),
    ): LayoutNodeSnapshot? {
        LOG.debug(
            "captureSnapshot called but LayoutInspector is stub in v3.5.1 (composeView={}, hiddenIds={})",
            composeView.javaClass.simpleName,
            hiddenIds.size,
        )
        return null
    }

    /**
     * v3.5.1: 不可用 — captureSnapshot() 永远返回 null, 这里仅占位 API.
     * 完整实现见 v3.6 (依赖 Modifier.testTag + 自定义 CompositionLocal).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun findRootLayoutNode(view: View): Any? = null

    /**
     * v3.5.1: 不可用 — 改用 SemanticsNode 公开 API (待 v3.6 接).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun readChildrenNodes(node: Any?): List<Any?> = emptyList()

    /**
     * v3.5.1: 不可用 — 改用 SemanticsNode 公开 API (待 v3.6 接).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun readNodeType(node: Any?): Pair<String, String> = "Node" to "Node"

    /**
     * v3.5.1: 不可用 — SemanticsModifier 公开访问的 ContentDescription 在
     * 1.5+ 改成 internal, 改用 [androidx.compose.ui.semantics.SemanticsNode] +
     * [androidx.compose.ui.semantics.SemanticsProperties.Text] 公开 API.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun readTextContent(node: Any?): String = ""

    /**
     * v3.5.1: 不可用 — TextProperties 依赖 LayoutModifier chain, 待 v3.6 接.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun readTextProperties(node: Any?): TextProperties? = null

    /**
     * v3.5.1: 不可用 — 简化: 永远 (false, false, true).
     */
    private fun readCommonProperties(): Triple<Boolean, Boolean, Boolean> {
        return Triple(false, false, true)
    }
}
