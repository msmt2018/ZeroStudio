package com.zerostudio.idea.editor.ui.lsp

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerostudio.idea.editor.lsp.model.LspLogEntry
import com.zerostudio.idea.editor.lsp.model.LspServerInstance
import com.zerostudio.idea.editor.lsp.model.MessageSource
import org.eclipse.lsp4j.MessageType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LspLogStreamViewer(
    instance: LspServerInstance,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logs by instance.logs.collectAsState()
    var selectedSource by remember { mutableStateOf<MessageSource?>(null) }
    val listState = rememberLazyListState()

    // 新日志产生时自动沉底滚动
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Surface(
        color = Color(0xFF1E1E1E),
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 工具栏
            TopAppBar(
                title = { Text("${instance.serverName} 运行日志", color = Color.White, fontSize = 16.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF252526)),
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        FilterChip(
                            selected = selectedSource == null,
                            onClick = { selectedSource = null },
                            label = { Text("ALL", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedSource == MessageSource.RPC,
                            onClick = { selectedSource = MessageSource.RPC },
                            label = { Text("RPC", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedSource == MessageSource.LSP,
                            onClick = { selectedSource = MessageSource.LSP },
                            label = { Text("LSP", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedSource == MessageSource.CLIENT,
                            onClick = { selectedSource = MessageSource.CLIENT },
                            label = { Text("Client", fontSize = 11.sp) }
                        )
                    }
                }
            )

            // 日志流
            val filtered = remember(logs, selectedSource) {
                if (selectedSource == null) logs else logs.filter { it.source == selectedSource }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                items(filtered) { entry ->
                    val color = when (entry.type) {
                        MessageType.Error -> Color(0xFFF44336)
                        MessageType.Warning -> Color(0xFFFFB74D)
                        MessageType.Info -> Color(0xFF81C784)
                        else -> when (entry.source) {
                            MessageSource.RPC -> Color(0xFF64B5F6)
                            MessageSource.CLIENT -> Color(0xFFBA68C8)
                            else -> Color(0xFFD4D4D4)
                        }
                    }

                    Text(
                        text = "[${entry.source}] ${entry.message}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = color,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}