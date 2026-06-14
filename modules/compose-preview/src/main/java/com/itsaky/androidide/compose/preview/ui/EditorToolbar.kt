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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 可视化编辑工具栏 v2.1.
 *
 * 4 个工具按钮 (Select / Pan / Drag / Eyedropper) + 关闭按钮.
 * 当前选中工具高亮 (蓝色背景 + 圆角).
 *
 * 接入方式 (在 [ComposePreviewScreen] 中):
 * ```kotlin
 * if (editorEnabled) {
 *     EditorToolbar(
 *         tool = editorTool,
 *         onToolChange = { viewModel.setEditorTool(it) },
 *         onClose = { viewModel.toggleEditor() },
 *     )
 * }
 * ```
 *
 * @param tool 当前工具
 * @param onToolChange 工具切换回调
 * @param onClose 关闭编辑模式回调
 * @param modifier modifier
 */
@Composable
fun EditorToolbar(
    tool: EditorTool,
    onToolChange: (EditorTool) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 工具栏标签
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Brush,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Editor",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.width(8.dp))

        // 4 个工具按钮
        EditorTool.values().forEach { t ->
            EditorToolButton(
                tool = t,
                selected = tool == t,
                onClick = { onToolChange(t) },
            )
            Spacer(Modifier.width(4.dp))
        }

        Spacer(Modifier.weight(1f))

        // 工具描述
        Text(
            text = tool.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.width(8.dp))

        // 关闭按钮
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭编辑器",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 单个工具按钮.
 */
@Composable
private fun EditorToolButton(
    tool: EditorTool,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
             else Color.Transparent
    val iconTint = if (selected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (selected) MaterialTheme.colorScheme.primary
                 else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(bg)
                .padding(2.dp),
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = tool.icon(),
                    contentDescription = tool.label,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = tool.label,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * 工具的图标映射.
 */
private fun EditorTool.icon(): ImageVector = when (this) {
    EditorTool.Select -> Icons.Filled.TouchApp
    EditorTool.Pan -> Icons.Filled.PanTool
    EditorTool.Drag -> Icons.Filled.OpenWith
    EditorTool.Eyedropper -> Icons.Filled.Colorize
}

/**
 * 编辑器状态条 v2.1.
 *
 * 顶部细条: 显示当前选中信息 (节点 id / composable 名 / 翻译偏移).
 * 跟 [EditorToolbar] 配合使用.
 *
 * @param state 当前 editor state
 * @param modifier modifier
 */
@Composable
fun EditorStatusBar(
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val s = state.selection
    val text = when {
        s == null -> "未选中 — 选择 ${state.tool.label} 工具后点击预览元素"
        s.isTranslated -> "选中 #${s.nodeId} ${s.composableName} · 偏移 (%.1f, %.1f) px".format(
            s.translationX, s.translationY,
        )
        else -> "选中 #${s.nodeId} ${s.composableName} · 尺寸 %.0f×%.0f px".format(
            s.bounds.width, s.bounds.height,
        )
    }
    val tint = if (s == null) MaterialTheme.colorScheme.onSurfaceVariant
               else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = Icons.Filled.Adjust,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(10.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = tint,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
