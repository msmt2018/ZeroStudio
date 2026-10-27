package com.zerostudio.idea.editor.ui.settings.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.nio.charset.Charset

data class EncodingEntry(val charsetName: String, val displayName: String, val region: String)

val GLOBAL_SUPPORTED_ENCODINGS = listOf(
    EncodingEntry("UTF-8", "UTF-8 (推荐通用标准)", "Unicode"),
    EncodingEntry("GB18030", "GB18030 (国家标准强制规范)", "中文简体"),
    EncodingEntry("GBK", "GBK (经典 Windows 简中)", "中文简体"),
    EncodingEntry("GB2312", "GB2312 (基本简中规范)", "中文简体"),
    EncodingEntry("Big5", "Big5 (大五码 繁体中文)", "中文繁体"),
    EncodingEntry("UTF-16LE", "UTF-16 Little Endian", "Unicode"),
    EncodingEntry("UTF-16BE", "UTF-16 Big Endian", "Unicode"),
    EncodingEntry("Shift_JIS", "Shift-JIS (日文标准)", "日本语"),
    EncodingEntry("EUC-JP", "EUC-JP (Unix 日文)", "日本语"),
    EncodingEntry("EUC-KR", "EUC-KR (韩文标准)", "韩语"),
    EncodingEntry("ISO-8859-1", "ISO-8859-1 (Latin-1 西欧标准)", "西欧语言"),
    EncodingEntry("windows-1252", "Windows-1252 (ANSI 西欧)", "西欧语言"),
    EncodingEntry("US-ASCII", "US-ASCII (7位纯文本)", "基础ASCII")
)

@Composable
fun EncodingSelectionDialog(
    currentCharset: String,
    onCharsetSelected: (Charset) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredEncodings = remember(searchQuery) {
        GLOBAL_SUPPORTED_ENCODINGS.filter {
            it.charsetName.contains(searchQuery, ignoreCase = true) ||
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.region.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "重新按指定编码加载",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "更改解释文本流的解码格式，不会损坏文件原始二进制字节",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索编码规范 (如 gbk, utf, 日文)...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredEncodings, key = { it.charsetName }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val charset = runCatching { Charset.forName(entry.charsetName) }
                                        .getOrDefault(Charsets.UTF_8)
                                    onCharsetSelected(charset)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = entry.charsetName.equals(currentCharset, ignoreCase = true),
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = entry.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${entry.region} • ${entry.charsetName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            }
        }
    }
}