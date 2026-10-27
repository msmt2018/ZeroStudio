package com.zerostudio.idea.editor.ui.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import java.io.File

data class WorkspaceEditorTab(
    val id: String,
    val file: File,
    val title: String,
    var isDirty: Boolean = false,
    var isReadOnly: Boolean = false
)

@Composable
fun ZeroTabWorkspace(
    tabs: List<WorkspaceEditorTab>,
    activeTabIndex: Int,
    onTabSelect: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth().height(42.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                val isActive = index == activeTabIndex
                val bgColor by animateColorAsState(
                    targetValue = if (isActive) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                    label = "tabBg"
                )

                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(bgColor)
                        .clickable { onTabSelect(index) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 脏数据修改指示点
                    if (tab.isDirty) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { onTabClose(index) },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // 标签分割虚线
                if (index < tabs.size - 1 && !isActive && index + 1 != activeTabIndex) {
                    Box(
                        modifier = Modifier
                            .height(16.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}