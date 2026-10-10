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

package com.itsaky.androidide.editor.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.activities.editor.BaseEditorActivity
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range

/**
 * 环形日志缓存管理器，确保高吞吐量下内存稳定不溢出。
 */
class ComposeLogBuffer(
    val maxLines: Int = 4000,
) {
  val lines = mutableStateListOf<String>()
  var isSourcePaused by mutableStateOf(false)
  var isAutoScroll by mutableStateOf(true)
  var isSoftWrap by mutableStateOf(false)

  fun appendLine(line: String) {
    if (isSourcePaused) return
    if (lines.size >= maxLines) {
      lines.removeRange(0, 100) // 批量弹出头部防卡死
    }
    lines.add(line)
  }

  fun appendBatch(batch: List<String>) {
    if (isSourcePaused || batch.isEmpty()) return
    val totalAfter = lines.size + batch.size
    if (totalAfter > maxLines) {
      val removeCount = (totalAfter - maxLines).coerceAtLeast(150)
      val actualRemove = removeCount.coerceAtMost(lines.size)
      lines.removeRange(0, actualRemove)
    }
    lines.addAll(batch)
  }

  fun clear() {
    lines.clear()
  }

  fun dump(): String = lines.joinToString("\n")
}

/**
 * Android Studio 风格的日志容器控件
 */
@Composable
fun LogConsoleScreen(
    buffer: ComposeLogBuffer,
    title: String,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val listState = rememberLazyListState()
  val horizontalScroll = rememberScrollState()

  // 自动滚动到最新日志逻辑
  LaunchedEffect(buffer.lines.size, buffer.isAutoScroll) {
    if (buffer.isAutoScroll && buffer.lines.isNotEmpty()) {
      listState.scrollToItem(buffer.lines.lastIndex)
    }
  }

  Column(
      modifier = modifier
          .fillMaxSize()
          .background(Color(0xFF14171A)),
  ) {
    // -------------------------------------------------------------
    // 1. Android Studio 风格的控制工具栏 (Bar 容器)
    // -------------------------------------------------------------
    Surface(
        color = Color(0xFF1E2227),
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
    ) {
      Row(
          modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        // 核心：源头暂停 / 启动
        IconButton(
            onClick = { buffer.isSourcePaused = !buffer.isSourcePaused },
            modifier = Modifier.size(32.dp),
        ) {
          Icon(
              imageVector = if (buffer.isSourcePaused) Icons.Default.PlayArrow else Icons.Default.Pause,
              contentDescription = if (buffer.isSourcePaused) "Resume" else "Pause",
              tint = if (buffer.isSourcePaused) Color(0xFF4CAF50) else Color(0xFFFFB74D),
              modifier = Modifier.size(18.dp),
          )
        }

        // 清空
        IconButton(
            onClick = { buffer.clear() },
            modifier = Modifier.size(32.dp),
        ) {
          Icon(
              imageVector = Icons.Default.Clear,
              contentDescription = "Clear",
              tint = Color(0xFFB0BEC5),
              modifier = Modifier.size(18.dp),
          )
        }

        // 自动滚动锁定
        IconButton(
            onClick = { buffer.isAutoScroll = !buffer.isAutoScroll },
            modifier = Modifier.size(32.dp),
        ) {
          Icon(
              imageVector = Icons.Default.ArrowDownward,
              contentDescription = "Auto Scroll",
              tint = if (buffer.isAutoScroll) Color(0xFF29B6F6) else Color(0xFF78909C),
              modifier = Modifier.size(18.dp),
          )
        }

        // 软换行开关
        IconButton(
            onClick = { buffer.isSoftWrap = !buffer.isSoftWrap },
            modifier = Modifier.size(32.dp),
        ) {
          Icon(
              imageVector = Icons.Default.WrapText,
              contentDescription = "Soft Wrap",
              tint = if (buffer.isSoftWrap) Color(0xFF29B6F6) else Color(0xFF78909C),
              modifier = Modifier.size(18.dp),
          )
        }

        // 复制全部
        IconButton(
            onClick = {
              val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              cm.setPrimaryClip(ClipData.newPlainText("Log Output", buffer.dump()))
              Toast.makeText(context, "日志已复制到剪切板", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(32.dp),
        ) {
          Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Copy All",
              tint = Color(0xFFB0BEC5),
              modifier = Modifier.size(18.dp),
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 状态信息胶囊标签
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (buffer.isSourcePaused) Color(0x33FFB74D) else Color(0x334CAF50),
        ) {
          Text(
              text = if (buffer.isSourcePaused) "源头已暂停" else "LIVE (${buffer.lines.size})",
              color = if (buffer.isSourcePaused) Color(0xFFFFB74D) else Color(0xFF81C784),
              fontSize = 10.sp,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
          )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF90A4AE),
            modifier = Modifier.padding(end = 8.dp),
        )
      }
    }

    // -------------------------------------------------------------
    // 2. 高性能日志展示区 (支持双向滑动、文本选区与超链接点击跳转)
    // -------------------------------------------------------------
    SelectionContainer(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
      Box(
          modifier = Modifier
              .fillMaxSize()
              .then(if (!buffer.isSoftWrap) Modifier.horizontalScroll(horizontalScroll) else Modifier),
      ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
          items(buffer.lines) { line ->
            val styledText = remember(line) {
              LogFileLinkParser.parseAndStyleLine(
                  rawLine = line,
                  linkColor = Color(0xFF40C4FF),
              )
            }

            ClickableText(
                text = styledText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFCFD8DC),
                    lineHeight = 16.sp,
                ),
                onClick = { offset ->
                  styledText.getStringAnnotations(
                      tag = "LOG_FILE_LINK",
                      start = offset,
                      end = offset,
                  ).firstOrNull()?.let { annotation ->
                    LogFileLinkParser.extractTarget(annotation.item)?.let { target ->
                      val activity = context as? BaseEditorActivity
                      if (activity != null) {
                        val zeroLine = (target.line - 1).coerceAtLeast(0)
                        val zeroCol = (target.column - 1).coerceAtLeast(0)
                        val range = Range(Position(zeroLine, zeroCol), Position(zeroLine, zeroCol))
                        activity.openFileAndSelect(target.file, range)
                      }
                    }
                  }
                },
            )
          }
        }
      }
    }
  }
}