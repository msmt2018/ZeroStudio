package com.zerostudio.idea.editor.ui.lsp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zerostudio.idea.editor.lsp.registry.ExternalLspServer
import com.zerostudio.idea.editor.lsp.registry.LspRegistry
import java.util.UUID

@Composable
fun LspServerConfigDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var serverName by remember { mutableStateOf("") }
    var languageName by remember { mutableStateOf("") }
    var fileExtensions by remember { mutableStateOf("") }
    var connectionType by remember { mutableStateOf("process") } // "process" or "socket"

    var command by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("localhost") }
    var port by remember { mutableStateOf("2087") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "配置外部语言服务器 (LSP)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = languageName,
                    onValueChange = { languageName = it },
                    label = { Text("语言名称 (例如 Go, Rust)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = fileExtensions,
                    onValueChange = { fileExtensions = it },
                    label = { Text("关联文件后缀 (逗号分隔: go, mod)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 通道选择
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = connectionType == "process",
                        onClick = { connectionType = "process" },
                        label = { Text("Stdio 管道进程") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = connectionType == "socket",
                        onClick = { connectionType = "socket" },
                        label = { Text("TCP Socket") }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (connectionType == "process") {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("启动命令 (例如 gopls serve)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("主机") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("端口") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val exts = fileExtensions.split(",").map { it.trim().removePrefix(".") }.filter { it.isNotEmpty() }
                            val server = ExternalLspServer(
                                id = UUID.randomUUID().toString(),
                                languageName = languageName.ifBlank { "Custom" },
                                serverName = serverName.ifBlank { "$languageName Server" },
                                supportedExtensions = exts,
                                connectionType = connectionType,
                                host = host,
                                port = port.toIntOrNull() ?: 2087,
                                command = command
                            )
                            LspRegistry.addExternalServer(context, server)
                            onDismiss()
                        },
                        enabled = languageName.isNotBlank() && fileExtensions.isNotBlank() &&
                                (connectionType == "socket" || command.isNotBlank())
                    ) {
                        Text("添加并保存")
                    }
                }
            }
        }
    }
}