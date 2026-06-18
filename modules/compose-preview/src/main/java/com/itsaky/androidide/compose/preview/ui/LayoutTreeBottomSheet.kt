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
import com.itsaky.androidide.compose.preview.editor.AttributeEditResult
import com.itsaky.androidide.compose.preview.editor.NamedParameter

/**
 * 布局树底部抽屉 v3.3.1.
 *
 * 调试模式 + 点击 debug toolbar "结构" chip 弹出. 显示:
 * - Tab 1: 节点树 (缩进, 可点击选中, 显示类型 + 位置 + 文本)
 * - Tab 2: 选中节点属性详情 (只读 + 复制)
 * - Tab 3: 隐藏节点管理 (编辑模式用, 可还原)
 * - Tab 4: 属性编辑 (v3.3.1 新增) — dex → smali/java → named parameter 提取 → 编辑 → build
 *
 * v3.3.1: Tab 1 接收 [layoutSnapshot] (来自 [com.itsaky.androidide.compose.preview.runtime.LayoutInspector]
 * 反射捕获的真实 LayoutNode 树), 真实嵌套显示. 之前 placeholder 只显示调试模式状态.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LayoutTreeBottomSheet(
    debugMode: DebugModeState,
    layoutSnapshot: LayoutNodeSnapshot?,
    attributeClassName: String?,
    attributeMethodName: String?,
    attributeCallLine: Int,
    attributes: List<NamedParameter>,
    lastEditResult: AttributeEditResult?,
    onSelectNode: (String?) -> Unit,
    onToggleHidden: (String) -> Unit,
    onClearHidden: () -> Unit,
    onEditAttribute: (parameterName: String, newValue: String) -> Unit,
    onClearEditResult: () -> Unit,
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
                // 节点计数 chip
                val nodeCount = layoutSnapshot?.let { countNodes(it) } ?: 0
                if (nodeCount > 0) {
                    AssistChip(
                        onClick = { selectedTab = 0 },
                        label = { Text("$nodeCount 节点", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ),
                    )
                }
                if (debugMode.hiddenNodeIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    AssistChip(
                        onClick = onClearHidden,
                        label = { Text("还原 ${debugMode.hiddenNodeIds.size} 隐藏") },
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
                    text = { Text("编辑") },
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("隐藏") },
                )
            }

            Spacer(modifier = Modifier.padding(top = 8.dp))

            when (selectedTab) {
                0 -> NodeTreeTab(
                    snapshot = layoutSnapshot,
                    debugMode = debugMode,
                    onSelectNode = onSelectNode,
                )
                1 -> PropertiesTab(
                    selectedNodeSnapshot = findNode(layoutSnapshot, debugMode.selectedNodeId),
                    selectedNodeId = debugMode.selectedNodeId,
                )
                2 -> AttributeEditPanel(
                    className = attributeClassName,
                    methodName = attributeMethodName,
                    callLine = attributeCallLine,
                    attributes = attributes,
                    lastResult = lastEditResult,
                    onEditAttribute = onEditAttribute,
                    onClearResult = onClearEditResult,
                )
                3 -> HiddenNodesTab(
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
 * 递归计算 LayoutNodeSnapshot 树的总节点数.
 */
private fun countNodes(node: LayoutNodeSnapshot): Int {
    return 1 + node.children.sumOf { countNodes(it) }
}

/**
 * 递归查找指定 id 的节点.
 */
private fun findNode(root: LayoutNodeSnapshot?, id: String?): LayoutNodeSnapshot? {
    if (root == null || id == null) return null
    if (root.id == id) return root
    for (child in root.children) {
        findNode(child, id)?.let { return it }
    }
    return null
}

/**
 * Tab 1: 节点树 (v3.3.1 真实化).
 */
@Composable
private fun NodeTreeTab(
    snapshot: LayoutNodeSnapshot?,
    debugMode: DebugModeState,
    onSelectNode: (String?) -> Unit,
) {
    if (snapshot == null) {
        // 没有快照 — 显示状态
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
                text = "1. 开启分析模式.\n" +
                    "2. preview 内容会叠加虚线 + 节点类型角标.\n" +
                    "3. 500ms 后自动捕获真实 LayoutNode 树, 节点列表显示在这里.\n" +
                    "4. 点击节点查看属性, 编辑模式下可隐藏 / 还原.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // 真实节点树
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "真实节点树 (${countNodes(snapshot)} 节点)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (debugMode.selectedNodeId != null) {
                AssistChip(
                    onClick = { onSelectNode(null) },
                    label = { Text("清除选中", style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Spacer(modifier = Modifier.size(4.dp))

        val flatList = remember(snapshot.id, debugMode.hiddenNodeIds) {
            flattenTree(snapshot)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items(flatList, key = { it.node.id }) { item ->
                NodeTreeRow(
                    item = item,
                    selectedId = debugMode.selectedNodeId,
                    onSelect = { onSelectNode(item.node.id) },
                )
            }
        }
    }
}

/**
 * 扁平化节点树为列表, 每个 item 包含深度用于缩进.
 */
private data class FlatNode(
    val node: LayoutNodeSnapshot,
    val depth: Int,
    val isLast: Boolean,
)

private fun flattenTree(root: LayoutNodeSnapshot): List<FlatNode> {
    val out = mutableListOf<FlatNode>()
    fun dfs(node: LayoutNodeSnapshot, depth: Int) {
        if (node.children.isEmpty()) {
            out.add(FlatNode(node, depth, true))
        } else {
            out.add(FlatNode(node, depth, false))
            node.children.forEachIndexed { i, c ->
                dfs(c, depth + 1)
            }
        }
    }
    dfs(root, 0)
    return out
}

@Composable
private fun NodeTreeRow(
    item: FlatNode,
    selectedId: String?,
    onSelect: () -> Unit,
) {
    val node = item.node
    val isSelected = node.id == selectedId
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        node.isHidden -> Color(0xFFB71C1C).copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    val indent = (item.depth * 16).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(onClick = onSelect)
            .padding(start = indent, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 树形连线
        Text(
            text = if (item.isLast) "└─ " else "├─ ",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        // 类型
        Text(
            text = node.typeName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            maxLines = 1,
        )
        // 文本 (Text 节点)
        if (node.text.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "\"${node.text.take(20)}${if (node.text.length > 20) "…" else ""}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // 尺寸 chip
        Text(
            text = "${node.width.toInt()}×${node.height.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

/**
 * Tab 2: 选中节点属性 (v3.3.1 真实化 — 用 snapshot 替代 placeholder).
 */
@Composable
private fun PropertiesTab(
    selectedNodeSnapshot: LayoutNodeSnapshot?,
    selectedNodeId: String?,
) {
    if (selectedNodeSnapshot == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (selectedNodeId == null) {
                    "未选中节点 — 在 '节点' tab 点击节点, 或在 preview 中点击虚线框"
                } else {
                    "选中节点 \"$selectedNodeId\" 不在当前快照中 (可能已隐藏或被重新渲染)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val clipboard = LocalClipboardManager.current
    val node = selectedNodeSnapshot
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        PropertyRow("id", node.id, copyable = true, onCopy = { clipboard.setText(AnnotatedString(node.id)) })
        PropertyRow(
            "类型", "${node.typeName} (${node.fullTypeName.substringAfterLast('.')})",
            copyable = true, onCopy = { clipboard.setText(AnnotatedString(node.fullTypeName)) },
        )
        PropertyRow(
            "完整类名", node.fullTypeName,
            copyable = true, onCopy = { clipboard.setText(AnnotatedString(node.fullTypeName)) },
        )
        PropertyRow(
            "位置 (px)", "L=${node.left.toInt()}, T=${node.top.toInt()}",
            copyable = true, onCopy = { clipboard.setText(AnnotatedString("L=${node.left.toInt()}, T=${node.top.toInt()}")) },
        )
        PropertyRow(
            "尺寸 (px)", "${node.width.toInt()} × ${node.height.toInt()}",
            copyable = true, onCopy = { clipboard.setText(AnnotatedString("${node.width.toInt()} x ${node.height.toInt()}")) },
        )
        PropertyRow(
            "深度", node.depth.toString(),
            copyable = false, onCopy = {},
        )
        PropertyRow(
            "子节点", node.children.size.toString(),
            copyable = false, onCopy = {},
        )
        if (node.text.isNotEmpty()) {
            PropertyRow(
                "文本", node.text,
                copyable = true, onCopy = { clipboard.setText(AnnotatedString(node.text)) },
            )
        }
        PropertyRow(
            "isHidden", node.isHidden.toString(),
            copyable = false, onCopy = {},
        )
        PropertyRow(
            "isClickable", node.isClickable.toString(),
            copyable = false, onCopy = {},
        )
        PropertyRow(
            "isFocusable", node.isFocusable.toString(),
            copyable = false, onCopy = {},
        )
        PropertyRow(
            "isEnabled", node.isEnabled.toString(),
            copyable = false, onCopy = {},
        )
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
