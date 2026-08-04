package com.itsaky.androidide.fragments.debugger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.fragments.sheets.BaseBottomSheetFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

/**
 * JDWP-only device connection sheet.
 *
 * Root, Shizuku, ADB, wireless debugging, OTG, SOCKS, LAN/VM and Fastboot entry
 * points are intentionally absent. The sheet only helps users attach to an
 * already-forwarded JDWP endpoint and provides a local command scratchpad for
 * non-ADB diagnostic commands.
 */
class DeviceConnectionBottomSheet : BaseBottomSheetFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme {
                JdwpConnectionScreen(onDismiss = { dismiss() })
            }
        }
    }
}

@Composable
private fun JdwpConnectionScreen(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var host by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf("8700") }
    var status by remember { mutableStateOf("等待检测 JDWP 端点") }
    var isChecking by remember { mutableStateOf(false) }
    var command by remember { mutableStateOf("pwd") }
    val consoleLines = remember { mutableStateListOf("$ 只允许本地非 ADB 命令；调试连接仅保留 JDWP 协议。") }

    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(onDismiss)
            ProtocolCard(status = status)
            EndpointCard(
                host = host,
                port = port,
                isChecking = isChecking,
                onHostChange = { host = it },
                onPortChange = { port = it.filter(Char::isDigit).take(5) },
                onCheck = {
                    isChecking = true
                    status = "正在检测 $host:$port ..."
                    scope.launch {
                        status = probeJdwp(host, port.toIntOrNull() ?: 0)
                        isChecking = false
                    }
                },
                onCopy = { copyAttachHint(clipboard, host, port) },
            )
            CommandConsole(
                command = command,
                lines = consoleLines,
                onCommandChange = { command = it },
                onRun = {
                    val trimmed = command.trim()
                    if (trimmed.isEmpty()) return@CommandConsole
                    consoleLines += "$ $trimmed"
                    if (isForbiddenCommand(trimmed)) {
                        consoleLines += "已阻止：DeviceConnectionBottomSheet 不再提供 ADB / Fastboot / Shizuku / Root / OTG / SOCKS 连接命令。"
                        return@CommandConsole
                    }
                    scope.launch {
                        consoleLines += runLocalCommand(trimmed)
                    }
                },
            )
        }
    }
}

@Composable
private fun Header(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("JDWP 连接", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("仅保留 Java Debug Wire Protocol", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "关闭")
        }
    }
}

@Composable
private fun ProtocolCard(status: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("当前协议：JDWP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(status, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun EndpointCard(
    host: String,
    port: String,
    isChecking: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onCheck: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("JDWP 端点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = host, onValueChange = onHostChange, label = { Text("Host") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = port, onValueChange = onPortChange, label = { Text("Port") }, modifier = Modifier.width(112.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onCheck, enabled = !isChecking && port.toIntOrNull() in 1..65535) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isChecking) "检测中" else "检测端点")
                }
                OutlinedButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制连接提示")
                }
            }
            InfoLine("请在外部工具完成端口转发后，在这里连接本地 JDWP 端点；本页面不再创建任何 ADB、OTG、无线调试或 Fastboot 通道。")
        }
    }
}

@Composable
private fun CommandConsole(command: String, lines: List<String>, onCommandChange: (String) -> Unit, onRun: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, null, tint = Color(0xFFA7F3D0))
                Spacer(Modifier.width(8.dp))
                Text("本地命令", color = Color.White, fontWeight = FontWeight.Bold)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
                    .border(1.dp, Color(0xFF374151), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(lines.takeLast(80)) { line ->
                    Text(line, color = Color(0xFFD1D5DB), fontFamily = FontFamily.Monospace)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = command, onValueChange = onCommandChange, label = { Text("命令") }, modifier = Modifier.weight(1f), singleLine = true)
                Button(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Text("运行")
                }
            }
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun copyAttachHint(clipboard: ClipboardManager, host: String, port: String) {
    clipboard.setText(AnnotatedString("JDWP attach endpoint: $host:$port"))
}

private suspend fun probeJdwp(host: String, port: Int): String = withContext(Dispatchers.IO) {
    if (port !in 1..65535) return@withContext "端口无效"
    runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 1_500)
            socket.getOutputStream().write("JDWP-Handshake".toByteArray(Charsets.US_ASCII))
            socket.soTimeout = 1_500
            val reply = ByteArray(14)
            val read = socket.getInputStream().read(reply)
            if (read == 14 && String(reply, Charsets.US_ASCII) == "JDWP-Handshake") {
                "JDWP 握手成功：$host:$port"
            } else {
                "端点可连接，但未返回 JDWP 握手"
            }
        }
    }.getOrElse { "无法连接 JDWP 端点：${it.message ?: it.javaClass.simpleName}" }
}

private fun isForbiddenCommand(command: String): Boolean {
    val first = command.trim().substringBefore(' ').lowercase()
    return first in setOf("adb", "fastboot", "su", "shizuku") || command.contains("/dev/bus/usb")
}

private suspend fun runLocalCommand(command: String): String = withContext(Dispatchers.IO) {
    runCatching {
        val process = ProcessBuilder("/system/bin/sh", "-c", command).redirectErrorStream(true).start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }.trim()
        val exit = process.waitFor()
        if (output.isBlank()) "exit=$exit" else "$output\nexit=$exit"
    }.getOrElse { "执行失败：${it.message ?: it.javaClass.simpleName}" }
}
