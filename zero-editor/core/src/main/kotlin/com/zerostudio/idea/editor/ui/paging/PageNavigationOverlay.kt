package com.zerostudio.idea.editor.ui.paging

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerostudio.idea.editor.core.buffer.paging.VirtualPageManager
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import kotlinx.coroutines.launch

@Composable
fun PageNavigationOverlay(
    editorView: ZeroCodeEditorView?,
    pageManager: VirtualPageManager?,
    modifier: Modifier = Modifier
) {
    if (pageManager == null || pageManager.getTotalPages() <= 1) return

    val totalPages = pageManager.getTotalPages()
    val currentPage = pageManager.currentPageIndex
    val scope = rememberCoroutineScope()
    var showJumpDialog by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier.padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 上一页
            IconButton(
                enabled = currentPage > 0,
                onClick = {
                    editorView?.let { editor ->
                        scope.launch {
                            val newContent = pageManager.switchPage(currentPage - 1, editor.text)
                            if (newContent != null) {
                                editor.setText(newContent)
                                editor.scrollTo(0, 0)
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一页", modifier = Modifier.size(18.dp))
            }

            // 翻页信息展示与点击跳转
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showJumpDialog = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${currentPage + 1} / $totalPages 页",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 下一页
            IconButton(
                enabled = currentPage < totalPages - 1,
                onClick = {
                    editorView?.let { editor ->
                        scope.launch {
                            val newContent = pageManager.switchPage(currentPage + 1, editor.text)
                            if (newContent != null) {
                                editor.setText(newContent)
                                editor.scrollTo(0, 0)
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一页", modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showJumpDialog) {
        var inputPage by remember { mutableStateOf("${currentPage + 1}") }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("跳转到虚拟分页") },
            text = {
                OutlinedTextField(
                    value = inputPage,
                    onValueChange = { inputPage = it },
                    label = { Text("目标页码 (1 ~ $totalPages)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = inputPage.toIntOrNull()
                        if (p != null && p in 1..totalPages) {
                            editorView?.let { editor ->
                                scope.launch {
                                    val newContent = pageManager.switchPage(p - 1, editor.text)
                                    if (newContent != null) {
                                        editor.setText(newContent)
                                        editor.scrollTo(0, 0)
                                    }
                                }
                            }
                        }
                        showJumpDialog = false
                    }
                ) {
                    Text("跳转")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}