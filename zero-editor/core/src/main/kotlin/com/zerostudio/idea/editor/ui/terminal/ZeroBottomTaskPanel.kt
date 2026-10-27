package com.zerostudio.idea.editor.ui.terminal

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerostudio.idea.editor.ui.compose.ThreeScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class TerminalLine(val text: AnnotatedString)

/**
 * 工业级底部任务控制台 (支持 ANSI SGR 颜色转换与三屏平滑折叠)
 */
@Composable
fun ZeroBottomTaskPanel(
    panelState: ThreeScreenState,
    onStateChange: (ThreeScreenState) -> Unit,
    modifier: Modifier = Modifier
) {
    var outputLines by remember { mutableStateOf(listOf<TerminalLine>()) }
    var isRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val targetHeight = when (panelState) {
        ThreeScreenState.HIDDEN -> 0.dp
        ThreeScreenState.PEEK -> 120.dp
        ThreeScreenState.HALF -> 280.dp
        ThreeScreenState.FULL -> 450.dp
    }

    val animatedHeight by animateDpAsState(targetValue = targetHeight, label = "TerminalHeight")

    fun appendRawText(line: String) {
        val annotated = parseAnsiSequence(line)
        outputLines = outputLines + TerminalLine(annotated)
    }

    if (panelState != ThreeScreenState.HIDDEN) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = Color(0xFF1E1E1E), // 纯黑底色模拟终端
            tonalElevation = 8.dp,
            modifier = modifier.fillMaxWidth().height(animatedHeight)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 控制台顶栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2D2D2D))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Terminal Output",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = FontFamily.Monospace
                        )
                        if (isRunning) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Row {
                        IconButton(
                            onClick = {
                                if (!isRunning) {
                                    isRunning = true
                                    outputLines = emptyList()
                                    scope.launch(Dispatchers.IO) {
                                        // 示例拉起系统 Shell 进程测试
                                        val process = ProcessBuilder("sh", "-c", "echo -e '\\033[32m[ZeroStudio Task]\\033[0m Building Project...'; uname -a; sleep 1; echo -e '\\033[36m[SUCCESS]\\033[0m Build Finished.'").start()
                                        BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                                            lines.forEach { line ->
                                                withContext(Dispatchers.Main) {
                                                    appendRawText(line)
                                                    listState.scrollToItem(outputLines.size)
                                                }
                                            }
                                        }
                                        isRunning = false
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "运行", tint = Color.Green)
                        }
                        IconButton(
                            onClick = { onStateChange(ThreeScreenState.HIDDEN) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "收起", tint = Color.LightGray)
                        }
                    }
                }

                // 日志输出滚动流
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    items(outputLines) { line ->
                        Text(
                            text = line.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

/**
 * 高性能 ANSI 色彩转义解析器 (处理 \u001B[31m 红 / \u001B[32m 绿 等前景色)
 */
private fun parseAnsiSequence(raw: String): AnnotatedString {
    return buildAnnotatedString {
        val pattern = java.util.regex.Pattern.compile("\u001B\\[([0-9;]+)m")
        val matcher = pattern.matcher(raw)
        var lastEnd = 0
        var currentColor = Color(0xFFE0E0E0)

        while (matcher.find()) {
            append(raw.substring(lastEnd, matcher.start()))
            val code = matcher.group(1) ?: "0"
            currentColor = when (code) {
                "31" -> Color(0xFFEF5350) // Red
                "32" -> Color(0xFF66BB6A) // Green
                "33" -> Color(0xFFFFCA28) // Yellow
                "34" -> Color(0xFF42A5F5) // Blue
                "35" -> Color(0xFFAB47BC) // Magenta
                "36" -> Color(0xFF26C6DA) // Cyan
                "0" -> Color(0xFFE0E0E0)  // Reset
                else -> currentColor
            }
            lastEnd = matcher.end()
        }
        if (lastEnd < raw.length) {
            pushStyle(SpanStyle(color = currentColor))
            append(raw.substring(lastEnd))
            pop()
        }
    }
}