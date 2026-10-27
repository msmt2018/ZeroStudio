package com.zerostudio.idea.editor.ui.settings.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zerostudio.idea.editor.core.analytics.TextAnalyticsEngine
import com.zerostudio.idea.editor.core.buffer.LargeTextPieceTree
import com.zerostudio.idea.editor.core.config.TextAnalyticsResult
import java.text.NumberFormat

@Composable
fun TextAnalyticsDialog(
    pieceTree: LargeTextPieceTree,
    onDismiss: () -> Unit
) {
    var statsResult by remember { mutableStateOf<TextAnalyticsResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        statsResult = TextAnalyticsEngine.analyze(pieceTree)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        title = {
            Text(text = "文本全维统计信息", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                statsResult?.let { stats ->
                    val formatter = remember { NumberFormat.getInstance() }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatItemRow("总行数 (Lines)", formatter.format(stats.totalLines))
                        StatItemRow("字符总数 (Characters)", formatter.format(stats.charCount))
                        StatItemRow("总字节数 (Bytes / UTF-8)", formatter.format(stats.byteCount))
                        StatItemRow("单词计数 (Words)", formatter.format(stats.wordCount))
                        StatItemRow("中日韩字符数 (CJK)", formatter.format(stats.cjkCharCount))
                    }
                }
            }
        }
    )
}

@Composable
private fun StatItemRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}