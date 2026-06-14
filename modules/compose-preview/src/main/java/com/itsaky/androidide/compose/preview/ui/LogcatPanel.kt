/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.compose.preview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 日志条目.
 */
data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val throwable: Throwable? = null,
)

enum class LogLevel(val label: String, val color: Color) {
    VERBOSE("V", Color(0xFF888888)),
    DEBUG("D", Color(0xFF4FC3F7)),
    INFO("I", Color(0xFF81C784)),
    WARN("W", Color(0xFFFFB74D)),
    ERROR("E", Color(0xFFE57373)),
    FATAL("F", Color(0xFFD32F2F));

    companion object {
        fun fromLabel(label: String): LogLevel =
            values().firstOrNull { it.label == label } ?: DEBUG
    }
}

/**
 * 拦截 System.out / System.err / 模拟 android.util.Log 调用的全局 sink.
 *
 * 用法:
 * ```
 * val logcat = PreviewLogcatSink()
 * logcat.install()
 * println("Hello")   // 出现在 logcat
 * PreviewLog.d("Tag", "msg")   // 出现在 logcat
 * logcat.uninstall()
 * ```
 *
 * 线程安全 (CopyOnWriteArrayList). 容量上限 1000 条 (FIFO 淘汰).
 */
class PreviewLogcatSink(
    val maxEntries: Int = 1000,
) {
    private val entries: CopyOnWriteArrayList<LogEntry> = CopyOnWriteArrayList()
    private val listeners: CopyOnWriteArrayList<(List<LogEntry>) -> Unit> = CopyOnWriteArrayList()

    @Volatile
    private var originalOut: PrintStream? = null
    @Volatile
    private var originalErr: PrintStream? = null

    fun snapshot(): List<LogEntry> = entries.toList()

    fun addListener(listener: (List<LogEntry>) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (List<LogEntry>) -> Unit) {
        listeners.remove(listener)
    }

    fun clear() {
        entries.clear()
        notifyListeners()
    }

    @Synchronized
    fun install() {
        if (originalOut != null) return
        originalOut = System.out
        originalErr = System.err
        System.setOut(PreviewPrintStream(this, LogLevel.INFO, "stdout", originalOut!!))
        System.setErr(PreviewPrintStream(this, LogLevel.ERROR, "stderr", originalErr!!))
    }

    @Synchronized
    fun uninstall() {
        originalOut?.let { System.setOut(it) }
        originalErr?.let { System.setErr(it) }
        originalOut = null
        originalErr = null
    }

    @Synchronized
    internal fun append(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(level, tag, message, System.currentTimeMillis(), throwable)
        entries.add(entry)
        if (entries.size > maxEntries) {
            entries.subList(0, entries.size - maxEntries).clear()
        }
        notifyListeners()
    }

    private fun notifyListeners() {
        val copy = entries.toList()
        listeners.forEach { it(copy) }
    }
}

/**
 * 把写入的字符串解析成日志条目, 通过 sink 转发.
 */
internal class PreviewPrintStream(
    private val sink: PreviewLogcatSink,
    private val level: LogLevel,
    private val tag: String,
    private val original: PrintStream,
) : PrintStream(original) {

    private val buffer = StringBuilder()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun print(s: String?) {
        if (s == null) return
        // 把原内容也输出 (避免 logcat 完全吃掉)
        original.print(s)
        buffer.append(s)
    }

    override fun println(s: String?) {
        if (s == null) return
        original.println(s)
        // 单行 (println 时 buffer 累积多行, 这里一并 flush)
        val combined = if (buffer.isNotEmpty()) buffer.toString() + s else s
        buffer.clear()
        // 多行拆开
        combined.lineSequence().forEach { line ->
            if (line.isNotEmpty()) {
                sink.append(level, tag, "[${timeFormat.format(Date())}] $line")
            }
        }
    }

    override fun println(x: Any?) {
        println(x?.toString() ?: "null")
    }
}

/**
 * 模拟 android.util.Log API 的轻量替代.
 *
 * 在预览中不能用真正的 android.util.Log (沙箱下 Log 类可能被移除),
 * 这里走 [PreviewLogcatSink].
 */
object PreviewLog {
    fun v(tag: String, msg: String) = sink.append(LogLevel.VERBOSE, tag, msg)
    fun d(tag: String, msg: String) = sink.append(LogLevel.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = sink.append(LogLevel.INFO, tag, msg)
    fun w(tag: String, msg: String) = sink.append(LogLevel.WARN, tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) = sink.append(LogLevel.ERROR, tag, msg, t)
    fun wtf(tag: String, msg: String, t: Throwable? = null) = sink.append(LogLevel.FATAL, tag, msg, t)

    @Volatile
    var sink: PreviewLogcatSink = PreviewLogcatSink()
}

/**
 * Logcat 面板 v2.1.
 *
 * 显示 [PreviewLogcatSink] 收集的日志, 支持:
 * - 级别过滤 (V/D/I/W/E/F toggle)
 * - 文本搜索
 * - 自动滚动到最新
 * - 清空
 *
 * @param sink 日志源
 * @param modifier modifier
 */
@Composable
fun LogcatPanel(
    sink: PreviewLogcatSink,
    modifier: Modifier = Modifier,
) {
    val entries = remember { mutableStateListOf<LogEntry>() }
    val listState = rememberLazyListState()
    var filterText by remember { mutableStateOf("") }
    var minLevel by remember { mutableStateOf(LogLevel.VERBOSE) }
    var autoScroll by remember { mutableStateOf(true) }

    LaunchedEffect(sink) {
        sink.addListener { list ->
            entries.clear()
            entries.addAll(list)
            if (autoScroll && entries.isNotEmpty()) {
                // 滚到最后
                kotlinx.coroutines.delay(50)
                try {
                    listState.scrollToItem(entries.size - 1)
                } catch (_: Throwable) {}
            }
        }
        entries.clear()
        entries.addAll(sink.snapshot())
    }

    val filtered = entries.filter { entry ->
        entry.level.ordinal >= minLevel.ordinal &&
            (filterText.isBlank() ||
                entry.message.contains(filterText, ignoreCase = true) ||
                entry.tag.contains(filterText, ignoreCase = true))
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                placeholder = { Text("过滤", fontSize = 12.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(14.dp)) },
                modifier = Modifier.weight(1f).height(40.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = { sink.clear() }) {
                Icon(Icons.Filled.ClearAll, contentDescription = "Clear", modifier = Modifier.size(18.dp))
            }
        }
        // 级别过滤
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LogLevel.values().forEach { level ->
                FilterChip(
                    selected = minLevel.ordinal <= level.ordinal,
                    onClick = { minLevel = level },
                    label = { Text(level.label, fontSize = 10.sp) },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            FilterChip(
                selected = autoScroll,
                onClick = { autoScroll = !autoScroll },
                label = { Text("Auto", fontSize = 10.sp) },
            )
        }

        // 日志列表
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101015)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无日志 (调用 println 或 PreviewLog.d/i/w/e 查看)",
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101015)),
            ) {
                items(filtered, key = { it.timestamp.toString() + it.hashCode() }) { entry ->
                    LogLine(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 级别
        Text(
            text = entry.level.label,
            color = entry.level.color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        // 标签
        Text(
            text = entry.tag,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(60.dp),
        )
        // 消息
        Text(
            text = entry.message,
            color = Color(0xFFE0E0E0),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}
