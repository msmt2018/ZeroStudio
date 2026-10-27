package com.zerostudio.idea.editor.ui.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

data class FileNodeItem(
    val file: File,
    val isDirectory: Boolean,
    val level: Int,
    var isExpanded: Boolean = false
)

/**
 * 自适应项目文件系统树控制器
 */
@Composable
fun ZeroFileTreeDrawer(
    rootDirectory: File,
    onFileClick: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }
    val displayList by remember(rootDirectory, expandedPaths) {
        derivedStateOf {
            val list = mutableListOf<FileNodeItem>()
            fun traverse(dir: File, level: Int) {
                val files = dir.listFiles()?.sortedWith(
                    compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
                ) ?: return

                for (file in files) {
                    val isDir = file.isDirectory
                    val isExp = expandedPaths.contains(file.absolutePath)
                    list.add(FileNodeItem(file, isDir, level, isExp))
                    if (isDir && isExp) {
                        traverse(file, level + 1)
                    }
                }
            }
            traverse(rootDirectory, 0)
            list
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxHeight().widthIn(min = 260.dp, max = 340.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp)) {
            // 项目名顶栏
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = rootDirectory.name.ifBlank { "Project" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            // 树形列表核心
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(displayList, key = { it.file.absolutePath }) { item ->
                    FileTreeRow(
                        node = item,
                        onClick = {
                            if (item.isDirectory) {
                                expandedPaths = if (expandedPaths.contains(item.file.absolutePath)) {
                                    expandedPaths - item.file.absolutePath
                                } else {
                                    expandedPaths + item.file.absolutePath
                                }
                            } else {
                                onFileClick(item.file)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileTreeRow(
    node: FileNodeItem,
    onClick: () -> Unit
) {
    val indentPadding = (node.level * 16).dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .padding(start = indentPadding)
    ) {
        if (node.isDirectory) {
            Icon(
                imageVector = if (node.isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (node.isExpanded) Icons.Default.PlayArrow else Icons.Default.Place,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Spacer(modifier = Modifier.width(20.dp))
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = node.file.name,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}