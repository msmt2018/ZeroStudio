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
 * 调试模式状态 v3.3.
 *
 * 区别于 [com.itsaky.androidide.compose.preview.PreviewState.Ready.debugEnabled] —
 * 那个是更早的占位字段, 现在被 [DebugModeState] 替代, 表达完整的调试模式 UI 状态.
 */
data class DebugModeState(
    val enabled: Boolean = false,
    val analysisMode: Boolean = false,
    val editMode: Boolean = false,
    val showRecompositionHighlight: Boolean = false,
    val showErrorBadge: Boolean = true,
    /** 选中的 layout 节点 id (分析模式下点击节点时设置) */
    val selectedNodeId: String? = null,
    /** 隐藏的节点 id 集合 (编辑模式下用户可隐藏某些节点) */
    val hiddenNodeIds: Set<String> = emptySet(),
) {
    /**
     * 分析模式或编辑模式任一开启, 都进入"检查活跃"态 — 渲染 inspector overlay.
     */
    val isInspectorActive: Boolean
        get() = analysisMode || editMode
}

/**
 * 布局模式 (用户从 Debug Toolbar 切换的两种模式).
 */
enum class DebugSubMode {
    /** 关闭, 只用普通渲染 */
    OFF,
    /** 分析模式: 显示虚线 + 点击节点查看属性 (只读) */
    INSPECT,
    /** 编辑模式: 在分析模式基础上, 提供"隐藏节点"等编辑能力 */
    EDIT,
}
