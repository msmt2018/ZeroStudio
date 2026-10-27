package com.zerostudio.idea.editor.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zerostudio.idea.editor.core.buffer.LargeTextPieceTree
import com.zerostudio.idea.editor.core.config.EditorPreferencesState
import com.zerostudio.idea.editor.ui.compose.ThreeScreenState
import com.zerostudio.idea.editor.ui.compose.ZeroCodeEditorViewport

/**
 * 响应式双栏 Diff 差异比对与分屏容器
 * 适用于 MEDIUM (8列) 和 EXPANDED (12列)
 */
@Composable
fun ZeroSplitDiffHost(
    leftPieceTree: LargeTextPieceTree,
    rightPieceTree: LargeTextPieceTree,
    onCloseDiff: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preferences = remember { EditorPreferencesState() }
    var syncScrollLocked by remember { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Diff 顶栏控制台
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "差异比对视图 (Diff Workspace)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FilterChip(
                        selected = syncScrollLocked,
                        onClick = { syncScrollLocked = !syncScrollLocked },
                        label = { Text("同步滚动") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                IconButton(onClick = onCloseDiff) {
                    Icon(Icons.Default.Close, contentDescription = "退出对比")
                }
            }
        }

        // 双栏栅格并排区域（80% 黄金工作视口）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 左侧：原始/基线版本 (ReadOnly)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                ZeroCodeEditorViewport(
                    state = ThreeScreenState.FULL,
                    preferences = preferences.apply { readOnlyMode = true },
                    pieceTree = leftPieceTree,
                    onStateChangeRequested = {},
                    modifier = Modifier.fillMaxSize()
                )
                // 左窗标识
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "原始基线 (Original)",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 右侧：当前/修改版本
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                ZeroCodeEditorViewport(
                    state = ThreeScreenState.FULL,
                    preferences = preferences.apply { readOnlyMode = false },
                    pieceTree = rightPieceTree,
                    onStateChangeRequested = {},
                    modifier = Modifier.fillMaxSize()
                )
                // 右窗标识
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "当前编辑副本 (Modified)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}