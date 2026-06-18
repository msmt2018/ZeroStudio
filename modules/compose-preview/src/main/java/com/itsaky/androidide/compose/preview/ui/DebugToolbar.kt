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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 调试模式专用 toolbar v3.3.
 *
 * 位于主 [PreviewToolbar] 下方, 仅在 [DebugModeState.enabled] = true 时显示.
 *
 * 包含按钮 (左到右):
 * - @Composable 函数下拉 (Debug 函数选择)
 * - 布局分析模式 toggle
 * - 布局编辑模式 toggle
 * - Recomposition 高亮 toggle
 * - 显示/隐藏布局树 (LayoutTreeBottomSheet)
 * - 截图导出
 * - 关闭调试模式
 */
@Composable
fun DebugToolbar(
    state: DebugToolbarState,
    actions: DebugToolbarActions,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 调试模式标签 — 视觉上区分于主 toolbar
        Text(
            text = "DEBUG",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp),
        )

        // @Composable 函数下拉
        AssistChip(
            onClick = actions.onOpenFunctionPicker,
            label = {
                Text(
                    text = state.currentFunctionName ?: "选择 @Composable",
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        // 函数计数
        Text(
            text = "${state.functionCount} 个",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 分隔
        Spacer(modifier = Modifier.width(8.dp))

        // 布局分析模式 toggle
        FilterChip(
            selected = state.analysisMode,
            onClick = actions.onToggleAnalysisMode,
            label = { Text("分析", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        // 布局编辑模式 toggle
        FilterChip(
            selected = state.editMode,
            onClick = actions.onToggleEditMode,
            label = { Text("编辑", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        // Recomposition 高亮 toggle
        FilterChip(
            selected = state.showRecompositionHighlight,
            onClick = actions.onToggleRecomposition,
            label = { Text("Recomp", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        // 显示/隐藏布局树
        FilterChip(
            selected = state.showLayoutTree,
            onClick = actions.onToggleLayoutTree,
            label = { Text("结构", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(
                    imageVector = if (state.showLayoutTree) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        // 清空隐藏节点
        if (state.hiddenNodeCount > 0) {
            AssistChip(
                onClick = actions.onClearHiddenNodes,
                label = { Text("清空 ${state.hiddenNodeCount} 隐藏", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.LayersClear,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
        }

        // 截图导出
        IconButton(onClick = actions.onExportScreenshot) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = "导出截图",
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 关闭调试模式
        IconButton(onClick = actions.onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭调试模式",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Debug Toolbar 状态.
 */
data class DebugToolbarState(
    /** 当前选中的 @Composable 函数名 (下拉 chip 显示) */
    val currentFunctionName: String? = null,
    /** 全部 @Composable 函数数量 */
    val functionCount: Int = 0,
    val analysisMode: Boolean = false,
    val editMode: Boolean = false,
    val showRecompositionHighlight: Boolean = false,
    val showLayoutTree: Boolean = false,
    val hiddenNodeCount: Int = 0,
)

/**
 * Debug Toolbar 行为回调.
 */
data class DebugToolbarActions(
    val onOpenFunctionPicker: () -> Unit = {},
    val onToggleAnalysisMode: () -> Unit = {},
    val onToggleEditMode: () -> Unit = {},
    val onToggleRecomposition: () -> Unit = {},
    val onToggleLayoutTree: () -> Unit = {},
    val onClearHiddenNodes: () -> Unit = {},
    val onExportScreenshot: () -> Unit = {},
    val onClose: () -> Unit = {},
)
