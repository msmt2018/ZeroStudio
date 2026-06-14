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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Perf Console 主屏 (PR #5/5).
 *
 * 4 tab:
 * - Boot: 启动总耗时 + phase 时间线 + 历史对比
 * - Frame: FPS 实时 + ANR 列表
 * - Memory: PSS 实时 + GC 频率
 * - Threads: 进程信息 + 线程分组
 *
 * @author android_zero
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfConsoleScreen(viewModel: PerfConsoleViewModel) {
  val state by viewModel.state.collectAsState()
  var selectedTab by remember { mutableStateOf(0) }

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
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        )
      },
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

private val TABS = listOf("Boot", "Frame", "Memory", "Threads")
