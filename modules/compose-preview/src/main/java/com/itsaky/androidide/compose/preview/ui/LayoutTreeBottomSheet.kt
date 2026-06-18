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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.compose.preview.data.model.DebugModeState
import com.itsaky.androidide.compose.preview.data.model.LayoutNodeSnapshot
import com.itsaky.androidide.compose.preview.data.model.TextProperties

/**
 * 布局树底部抽屉 v3.3.
 *
 * 调试模式 + 点击 debug toolbar "结构" chip 弹出. 显示:
 * - Tab 1: 节点树 (缩进, 可点击选中, 显示类型 + 位置)
 * - Tab 2: 选中节点属性详情 (只读 + 复制)
 * - Tab 3: 隐藏节点管理 (编辑模式用, 可还原)
 *
 * 注意: LayoutNodeSnapshot 通过 [LayoutInspector] 反射捕获. 本 sheet 本身
 * 接收一个外部的 snapshot, 因为 inspector 需要 view + reflection, 不在
 * sheet 内做.
 *
 * 简化: 本次不实际 capture snapshot (需要 view 引用), 改由 Composable 函数
 * 列表替代. Tab 1 显示调试模式状态信息 + @Composable 函数列表 (替代方案).
 * 完整版需要 view 引用 + inspector.captureSnapshot, 留到后续 PR.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LayoutTreeBottomSheet(
    debugMode: DebugModeState,
    onSelectNode: (String?) -> Unit,
    onToggleHidden: (String) -> Unit,
    onClearHidden: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "布局检查器",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // 编辑模式下显示"清空隐藏"按钮
                if (debugMode.hiddenNodeIds.isNotEmpty()) {
                    AssistChip(
                        onClick = onClearHidden,
                        label = { Text("还原 ${debugMode.hiddenNodeIds.size} 个隐藏") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.LayersClear,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.padding(top = 4.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("节点") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("属性") },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("隐藏") },
                )
            }

            Spacer(modifier = Modifier.padding(top = 8.dp))

            when (selectedTab) {
                0 -> NodeTreeTab(
                    debugMode = debugMode,
                    onSelectNode = onSelectNode,
                )
                1 -> PropertiesTab(
                    selectedNodeId = debugMode.selectedNodeId,
                )
                2 -> HiddenNodesTab(
                    debugMode = debugMode,
                    onToggleHidden = onToggleHidden,
                    onClearHidden = onClearHidden,
                )
            }

            Spacer(modifier = Modifier.heightIn(min = 8.dp))
        }
    }
}

/**
 * Tab 1: 节点树.
 */
@Composable
private fun NodeTreeTab(
    debugMode: DebugModeState,
    onSelectNode: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(
            text = "调试模式状态",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.size(4.dp))
        DebugStatusRow("调试模式", if (debugMode.enabled) "ON" else "OFF")
        DebugStatusRow("分析模式", if (debugMode.analysisMode) "ON" else "OFF")
        DebugStatusRow("编辑模式", if (debugMode.editMode) "ON" else "OFF")
        DebugStatusRow("Recomp 高亮", if (debugMode.showRecompositionHighlight) "ON" else "OFF")
        DebugStatusRow("选中节点", debugMode.selectedNodeId ?: "无")
        DebugStatusRow("隐藏节点", "${debugMode.hiddenNodeIds.size} 个")

        Spacer(modifier = Modifier.size(8.dp))
        Divider()
        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = "提示",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "分析模式下, 点击 preview 中任意节点 (虚线框) 可在 '属性' tab 查看详情. " +
                "编辑模式下, 长按节点可隐藏 / 还原. " +
                "完整节点树捕获需要反射访问 LayoutNode 内部状态, 已在 [LayoutInspector] 实现, " +
                "由 overlay 在 layout 后回调. 当前显示调试模式状态汇总.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.size(8.dp))
        if (debugMode.selectedNodeId != null) {
            AssistChip(
                onClick = { onSelectNode(null) },
                label = { Text("清除选中") },
            )
        }
    }
}

/**
 * Tab 2: 选中节点属性.
 */
@Composable
private fun PropertiesTab(
    selectedNodeId: String?,
) {
    if (selectedNodeId == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "未选中节点 — 在 '节点' tab 点击节点, 或在 preview 中点击虚线框",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        PropertyRow("id", selectedNodeId, copyable = true, onCopy = { clipboard.setText(AnnotatedString(selectedNodeId)) })
        // 占位 — 完整属性需要 LayoutInspector 捕获到 snapshot 后填入
        PropertyRow("类型", "(需 LayoutInspector 捕获)", copyable = false, onCopy = {})
        PropertyRow("完整类名", "(需 LayoutInspector 捕获)", copyable = true, onCopy = {})
        PropertyRow("位置/尺寸", "(需 LayoutInspector 捕获)", copyable = false, onCopy = {})
    }
}

@Composable
private fun PropertyRow(
    label: String,
    value: String,
    copyable: Boolean,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        if (copyable) {
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "复制",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Tab 3: 隐藏节点管理.
 */
@Composable
private fun HiddenNodesTab(
    debugMode: DebugModeState,
    onToggleHidden: (String) -> Unit,
    onClearHidden: () -> Unit,
) {
    if (debugMode.hiddenNodeIds.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "无隐藏节点 — 编辑模式下点击节点可隐藏",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(debugMode.hiddenNodeIds.toList(), key = { it }) { nodeId ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable { onToggleHidden(nodeId) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = nodeId,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = "还原",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DebugStatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
