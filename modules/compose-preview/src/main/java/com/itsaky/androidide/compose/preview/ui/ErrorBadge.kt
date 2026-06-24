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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.compose.preview.compiler.CompileDiagnostic

/**
 * 错误 Badge v3.3.
 *
 * 调试模式开启 + preview 出现 Error 时, 在 Debug Toolbar 末尾显示一个可点击的 badge.
 * 点击触发 [onClick], 弹错误详情 dialog / sheet.
 *
 * 注意: 错误本身已经在主 ErrorPanel 渲染, 这个 badge 只是辅助入口.
 */
@Composable
fun ErrorBadge(
    diagnostics: List<CompileDiagnostic>,
    errorMessage: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (errorMessage == null && diagnostics.isEmpty()) return
    val hasError = errorMessage != null
    val bgColor = if (hasError) Color(0xFFB00020) else Color(0xFFE65100)
    val icon = if (hasError) Icons.Filled.Error else Icons.Filled.Warning
    val label = buildString {
        if (hasError) append("Error")
        else append("${diagnostics.size} 警告")
        if (errorMessage != null) append(" · $errorMessage")
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .align(Alignment.CenterVertically),
            )
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * 错误详情 Sheet v3.3.
 *
 * 弹出全部 diagnostics + 错误 message. 用户可滚动查看.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ErrorDetailSheet(
    errorMessage: String?,
    diagnostics: List<CompileDiagnostic>,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "编译 / 运行错误",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (errorMessage != null) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (diagnostics.isNotEmpty()) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = "诊断 (${diagnostics.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    items(diagnostics) { d: com.itsaky.androidide.compose.preview.compiler.CompileDiagnostic ->
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            val loc = buildString {
                                d.file?.let { append(it.substringAfterLast('/')) }
                                d.line?.let { append(":$it") }
                                d.column?.let { append(":$it") }
                            }
                            if (loc.isNotBlank()) {
                                Text(
                                    text = loc,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                )
                            }
                            Text(
                                text = "[${d.severity}] ${d.message}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
        }
    }
}
