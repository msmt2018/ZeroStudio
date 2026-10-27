package com.zerostudio.idea.editor.ui.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerostudio.idea.editor.tab.ZeroEditorTab
import com.zerostudio.idea.editor.ui.compose.ThreeScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroEditorToolbar(
    activeTab: ZeroEditorTab?,
    screenState: ThreeScreenState,
    onSaveRequested: () -> Unit,
    onSearchRequested: () -> Unit,
    onJumpLineRequested: () -> Unit,
    onFormatRequested: () -> Unit,
    onEncodingRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabState = activeTab?.state

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：文件状态、脏标记与分页控制器
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = "${tabState?.title ?: "无活动文件"}${if (tabState?.isDirty == true) " •" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (tabState?.isDirty == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                // 虚拟分页标签（当处于分页状态时展示）
                if (tabState != null && tabState.totalPages > 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "第 ${tabState.activePageIndex + 1}/${tabState.totalPages} 页",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        modifier = Modifier.height(26.dp)
                    )
                }
            }

            // 右侧工具箱操作：依据三分屏规则收敛剪裁
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                // 1. 基础操作组 (PEEK 态始终保留)
                IconButton(onClick = onSaveRequested, enabled = tabState?.isDirty == true) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = "保存",
                        tint = if (tabState?.isDirty == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 2. 进阶编辑组 (HALF / FULL 态展开)
                AnimatedVisibility(visible = screenState != ThreeScreenState.PEEK) {
                    Row {
                        IconButton(onClick = { activeTab?.boundEditorView?.undo() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "撤销")
                        }
                        IconButton(onClick = { activeTab?.boundEditorView?.redo() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "重做")
                        }
                        IconButton(onClick = onSearchRequested) {
                            Icon(Icons.Default.Search, contentDescription = "搜索与替换")
                        }
                        IconButton(onClick = onJumpLineRequested) {
                            Icon(Icons.Default.Place, contentDescription = "行跳转")
                        }
                        IconButton(onClick = onFormatRequested) {
                            Icon(Icons.Default.Build, contentDescription = "代码格式化")
                        }
                        IconButton(onClick = onEncodingRequested) {
                            Text(
                                text = tabState?.encoding?.name() ?: "UTF-8",
                                fontSize = 11.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { tabState?.isReadOnly = !(tabState?.isReadOnly ?: false) }) {
                            Icon(
                                imageVector = if (tabState?.isReadOnly == true) Icons.Default.Lock else Icons.Default.Edit,
                                contentDescription = "只读锁定"
                            )
                        }
                    }
                }
            }
        }
    }
}