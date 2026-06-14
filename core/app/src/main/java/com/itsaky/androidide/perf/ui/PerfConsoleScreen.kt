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
package com.itsaky.androidide.perf.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.perf.export.PerfExporter
import com.itsaky.androidide.perf.export.PhaseThresholds
import com.itsaky.androidide.perf.export.ThreadDumper
import com.itsaky.androidide.perf.PhaseStore
import com.itsaky.androidide.perf.store.BootHistoryStore
import androidx.core.content.FileProvider
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Perf Console 主屏 (PR #7/7).
 *
 * 4 tab:
 * - Boot: 启动总耗时 + phase 时间线 (PR #7 加告警染色) + 历史对比
 * - Frame: FPS 实时 + ANR 列表
 * - Memory: PSS 实时 + GC 频率
 * - Threads: 进程信息 + 线程分组
 *
 * ## TopAppBar actions (PR #7 新增)
 *
 * - **Export**: 导出当前 session + 历史为 JSON, 弹 share intent
 * - **Dump threads**: 把 JVM 所有线程 stack dump 到 cacheDir
 * - **Thresholds**: 弹 dialog 显示当前 [PhaseThresholds] 配置
 *
 * @author android_zero
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfConsoleScreen(viewModel: PerfConsoleViewModel) {
  val state by viewModel.state.collectAsState()
  var selectedTab by remember { mutableStateOf(0) }
  var menuExpanded by remember { mutableStateOf(false) }
  var thresholdsDialogVisible by remember { mutableStateOf(false) }

  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Column {
                Text(
                    text = "Perf Console",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Text(
                    text = "状态: ${if (state.isReady) "已连接" else "等待 :perf server"}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            },
            actions = {
              IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
              }
              DropdownMenu(
                  expanded = menuExpanded,
                  onDismissRequest = { menuExpanded = false },
              ) {
                DropdownMenuItem(
                    text = { Text("Export JSON") },
                    onClick = {
                      menuExpanded = false
                      scope.launch {
                        val file =
                            withContext(Dispatchers.IO) {
                              PerfExporter.exportToCache(
                                  cacheDir = context.cacheDir,
                                  collector = PhaseStore.collector(),
                                  historyStore = BootHistoryStore(context),
                              )
                            }
                        if (file != null) {
                          shareFile(context = context, file = file, mimeType = "application/json")
                          snackbarHostState.showSnackbar("导出: ${file.name}")
                        } else {
                          snackbarHostState.showSnackbar("导出失败")
                        }
                      }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Dump threads") },
                    onClick = {
                      menuExpanded = false
                      scope.launch {
                        val file =
                            withContext(Dispatchers.IO) {
                              ThreadDumper.dumpToCache(context.cacheDir, reason = "manual")
                            }
                        if (file != null) {
                          shareFile(context = context, file = file, mimeType = "text/plain")
                          snackbarHostState.showSnackbar("Dump: ${file.name}")
                        } else {
                          snackbarHostState.showSnackbar("Dump 失败")
                        }
                      }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Thresholds") },
                    onClick = {
                      menuExpanded = false
                      thresholdsDialogVisible = true
                    },
                )
              }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        )
      },
      snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      TabRow(selectedTabIndex = selectedTab) {
        TABS.forEachIndexed { i, label ->
          Tab(
              selected = selectedTab == i,
              onClick = { selectedTab = i },
              text = { Text(label, fontSize = 13.sp) },
          )
        }
      }

      if (!state.isReady) {
        LoadingState()
        return@Column
      }

      when (selectedTab) {
        0 -> BootTab(state = state)
        1 -> FrameTab(state = state)
        2 -> MemoryTab(state = state)
        3 -> ThreadsTab(state = state)
      }
    }
  }

  if (thresholdsDialogVisible) {
    ThresholdsDialog(onDismiss = { thresholdsDialogVisible = false })
  }
}

@Composable
private fun LoadingState() {
  Box(
      modifier = Modifier.fillMaxSize().padding(32.dp),
      contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      CircularProgressIndicator()
      Spacer(modifier = Modifier.height(16.dp))
      Text(
          text = "等待 IDEApplication.onCreate 上报 phase…",
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
          text = "(从桌面启动主 IDE application 即可看到数据)",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.outline,
      )
    }
  }
}

@Composable
private fun ThresholdsDialog(onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Phase 告警阈值") },
      text = {
        Column {
          val t = PhaseThresholds.thresholds
          Text("OK      < ${t[0]} ms", color = MaterialTheme.colorScheme.primary)
          Text("WARN    < ${t[1]} ms", color = MaterialTheme.colorScheme.tertiary)
          Text("SLOW    < ${t[2]} ms", color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
          Text("CRITICAL ≥ ${t[2]} ms", color = MaterialTheme.colorScheme.error)
          Spacer(modifier = Modifier.height(8.dp))
          Text(
              "比例告警: 5% / 15% / 30%",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
  )
}

/**
 * 用 FileProvider 分享文件 (避免 FileUriExposedException).
 *
 * 失败 (e.g. 没有 FileProvider manifest 配置) 静默 no-op, 不影响主流程.
 */
private fun shareFile(context: android.content.Context, file: java.io.File, mimeType: String) {
  try {
    val authority = "${context.packageName}.perf.fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, file)
    val intent =
        Intent(Intent.ACTION_SEND).apply {
          type = mimeType
          putExtra(Intent.EXTRA_STREAM, uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, "分享 ${file.name}"))
  } catch (e: Throwable) {
    org.slf4j.LoggerFactory.getLogger("PerfConsoleScreen")
        .warn("shareFile failed: {}", e.message)
  }
}

private val TABS = listOf("Boot", "Frame", "Memory", "Threads")
