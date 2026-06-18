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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.compose.preview.editor.AttributeEditResult
import com.itsaky.androidide.compose.preview.editor.NamedParameter

/**
 * 属性编辑面板 v3.4.
 *
 * 调试模式 + 编辑模式开启时, 在底部抽屉 Tab 2 显示:
 * - 选中节点的 named parameter 列表 (从 dex 反编译得到)
 * - 每个参数有 "编辑" 按钮 → 弹 AlertDialog 输入新值
 * - 提交后:
 *   1. ViewModel.editAttribute -> ComposeAttributeEditor.editKtFile 写 .kt
 *   2. onBuildTriggered 回调 -> Activity 调 BuildService.executeTasks("assembleDebug")
 *   3. Build 完成后 preview 自动重新渲染
 *
 * v3.4 增: 头部加 [onRefresh] 按钮 + [isRefreshing] 加载状态指示.
 * 点击 Refresh 触发 [onRefresh] 回调 (Activity 调 ViewModel.refreshAttributes),
 * 加载时显示 [CircularProgressIndicator] 替代 Refresh 图标, 让用户感知 dex
 * 重新解析过程 (可能耗时 100ms ~ 数秒, 取决于 dex 大小).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AttributeEditPanel(
    className: String?,
    methodName: String?,
    callLine: Int,
    attributes: List<NamedParameter>,
    lastResult: AttributeEditResult?,
    onEditAttribute: (parameterName: String, newValue: String) -> Unit,
    onClearResult: () -> Unit,
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var editingParam by remember { mutableStateOf<NamedParameter?>(null) }

    Column(modifier = modifier.fillMaxWidth().padding(top = 4.dp)) {
        // 头
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "属性编辑 (v3.4)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (attributes.isNotEmpty()) {
                AssistChip(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    label = { Text("${attributes.size} 个属性") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    ),
                )
            }
            // v3.4: 刷新按钮 — 加载中替换为 CircularProgressIndicator (小尺寸 16dp)
            // 让用户看到 "正在重新解析 dex" 状态. 之前 v3.3.1 这里只有 /* TODO: 刷新 */.
            // CircularProgressIndicator 自带 indeterminate 旋转, 不需要额外的 LaunchedEffect.
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = isRefreshing,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                AnimatedVisibility(
                    visible = !isRefreshing,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(32.dp),
                        enabled = attributes.isNotEmpty() || className != null,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新属性 (重新解析 dex)",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // 上次结果
        when (lastResult) {
            is AttributeEditResult.Success -> {
                ResultBanner(
                    text = "已修改 L${lastResult.line} · ${lastResult.taskName} 已触发\n${lastResult.oldSource.trim()}\n  ↓\n${lastResult.newSource.trim()}",
                    isError = false,
                    onClose = onClearResult,
                )
            }
            is AttributeEditResult.Failure -> {
                ResultBanner(
                    text = "修改失败: ${lastResult.reason}",
                    isError = true,
                    onClose = onClearResult,
                )
            }
            null -> Unit
        }

        // class + method + line 信息
        if (className != null && methodName != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(8.dp),
            ) {
                Column {
                    Text(
                        text = "class: $className",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                    Text(
                        text = "fun: $methodName · L$callLine",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.size(4.dp))
        }

        if (attributes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "未选中节点, 或当前方法无 named parameter.\n" +
                        "在 preview 中点击节点, 即可看到该方法的属性.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        // 属性列表
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(attributes, key = { it.name }) { p ->
                AttributeRow(
                    param = p,
                    onEdit = { editingParam = p },
                )
            }
        }
    }

    // 编辑 dialog
    editingParam?.let { param ->
        EditAttributeDialog(
            param = param,
            onConfirm = { newValue ->
                onEditAttribute(param.name, newValue)
                editingParam = null
            },
            onDismiss = { editingParam = null },
        )
    }
}

@Composable
private fun AttributeRow(
    param: NamedParameter,
    onEdit: () -> Unit,
) {
    val typeColor = when (param.valueType) {
        NamedParameter.ValueType.STRING -> Color(0xFF388E3C)
        NamedParameter.ValueType.NUMBER -> Color(0xFF1976D2)
        NamedParameter.ValueType.COLOR -> Color(0xFFE65100)
        NamedParameter.ValueType.DIMEN -> Color(0xFF7B1FA2)
        NamedParameter.ValueType.BOOLEAN -> Color(0xFF455A64)
        NamedParameter.ValueType.NULL -> Color(0xFF9E9E9E)
        NamedParameter.ValueType.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 参数名
        Text(
            text = param.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(80.dp),
        )
        // 类型 chip
        Text(
            text = param.valueType.name.lowercase(),
            style = MaterialTheme.typography.labelSmall,
            color = typeColor,
            fontSize = 9.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(typeColor.copy(alpha = 0.12f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        // 值
        Text(
            text = param.value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        // 编辑按钮
        OutlinedButton(
            onClick = onEdit,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "编辑",
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text("编辑", fontSize = 10.sp)
        }
    }
}

@Composable
private fun EditAttributeDialog(
    param: NamedParameter,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(param.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑 ${param.name}", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    text = "原值: ${param.value}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("新值") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "提交后:\n" +
                        "1. 写 .kt 源文件\n" +
                        "2. 触发 assembleDebug\n" +
                        "3. 完成后 preview 重新渲染",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
                Text("提交")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ResultBanner(
    text: String,
    isError: Boolean,
    onClose: () -> Unit,
) {
    val bg = if (isError) Color(0xFFB00020).copy(alpha = 0.15f) else Color(0xFF388E3C).copy(alpha = 0.15f)
    val icon = if (isError) Icons.Filled.Error else Icons.Filled.AssignmentTurnedIn
    val color = if (isError) Color(0xFFB00020) else Color(0xFF388E3C)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, shape = RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClose) {
            Text("关闭", fontSize = 10.sp)
        }
    }
    Spacer(modifier = Modifier.size(4.dp))
}
