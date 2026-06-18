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

package com.itsaky.androidide.compose.preview.data.model

/**
 * 布局节点快照 v3.3.
 *
 * 通过反射 [androidx.compose.ui.node.LayoutNode] 私有字段 (e.g. `_children`) 拿到
 * 节点树, 然后序列化为这个不可变结构, 给 UI / 底部抽屉 / 编辑模式用.
 *
 * 字段全部快照 — 修改不会影响真实 Compose 树. 如果要隐藏, 单独存 [DebugModeState.hiddenNodeIds].
 */
data class LayoutNodeSnapshot(
    /** 唯一 id — 路径式 (e.g. "root.0.1.2") */
    val id: String,
    /** 节点在父节点中的索引 (用于排序) */
    val index: Int,
    /** 控件类型 (Compose 控件的 simpleName, e.g. "Box", "Text", "Button") */
    val typeName: String,
    /** 完整 FQCN (e.g. "androidx.compose.foundation.layout.Box") */
    val fullTypeName: String,
    /** 文本内容 (Text / Button 等含文字的控件), 其它为空 */
    val text: String = "",
    /** 深度 — root = 0 */
    val depth: Int,
    /** 绝对位置 + 尺寸 (px) */
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    /** 子节点 */
    val children: List<LayoutNodeSnapshot> = emptyList(),
    /** 文本相关属性 (Text 节点用) */
    val textProperties: TextProperties? = null,
    /** 通用属性 (clickable / focusable / enabled) */
    val isClickable: Boolean = false,
    val isFocusable: Boolean = false,
    val isEnabled: Boolean = true,
    /** 该节点当前是否被隐藏 (编辑模式) */
    val isHidden: Boolean = false,
    /** 该节点是否在最近一次 render 中 recompose 过 (Recomposition 高亮用) */
    val recomposed: Boolean = false,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * Text 节点属性快照.
 */
data class TextProperties(
    val fontSize: Float,
    val color: Int,
    val textAlign: String,
    val fontWeight: Int,
    val letterSpacing: Float,
    val lineHeight: Float,
    val maxLines: Int,
    val overflow: String,
)
