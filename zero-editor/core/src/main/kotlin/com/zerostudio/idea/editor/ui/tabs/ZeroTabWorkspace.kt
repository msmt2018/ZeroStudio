package com.zerostudio.idea.editor.ui.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerostudio.idea.editor.tab.ZeroEditorTab

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZeroTabStrip(
    tabs: List<ZeroEditorTab>,
    activeTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onCloseAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var contextMenuTabIndex by remember { mutableStateOf<Int?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                val isActive = index == activeTabIndex
                val backgroundColor by animateColorAsState(
                    targetValue = if (isActive) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        Color.Transparent
                    },
                    label = "TabBgAnimation"
                )

                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(backgroundColor)
                        .combinedClickable(
                            onClick = { onTabSelected(index) },
                            onLongClick = { contextMenuTabIndex = index }
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 脏标记红点
                    if (tab.state.isDirty) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = tab.state.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { onTabClosed(index) },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 标签分割线
                if (index < tabs.size - 1 && !isActive && index + 1 != activeTabIndex) {
                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // 标签上下文快捷菜单
        contextMenuTabIndex?.let { index ->
            DropdownMenu(
                expanded = true,
                onDismissRequest = { contextMenuTabIndex = null }
            ) {
                DropdownMenuItem(
                    text = { Text("关闭当前标签") },
                    onClick = {
                        onTabClosed(index)
                        contextMenuTabIndex = null
                    }
                )
                DropdownMenuItem(
                    text = { Text("关闭其他标签") },
                    onClick = {
                        onCloseOthers(index)
                        contextMenuTabIndex = null
                    }
                )
                DropdownMenuItem(
                    text = { Text("关闭所有标签") },
                    onClick = {
                        onCloseAll()
                        contextMenuTabIndex = null
                    }
                )
            }
        }
    }
}