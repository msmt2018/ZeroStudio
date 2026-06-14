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
package com.itsaky.androidide.perf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 性能监控调试控制台 (PR #1 骨架).
 *
 * 跑在独立进程 `android:process=":perf"`, 不会被主 application
 * (`com.itsaky.androidide`) 的初始化阻塞. 主 application 启动崩溃时,
 * 此 Activity 仍能启动, 监控 UI 仍能显示.
 *
 * ## PR 路线图
 *
 * - **PR #1 (本 PR)**: 骨架 — 独立 :perf 进程 + 桌面图标 + 占位 UI
 * - **PR #2**: 主进程 [com.itsaky.androidide.perf.tracer.PerfTracer] +
 *   IDEApplication.onCreate 18 段埋点
 * - **PR #3**: [com.itsaky.androidide.perf.server.PerfServerSocket] +
 *   跨进程协议 + PhaseCollector
 * - **PR #4**: 4 个 Monitor (FrameRate / Memory / Gc / Anr) + BootHistoryStore
 * - **PR #5**: 完整 Compose UI (Dashboard + 4 tab + 6 card)
 *
 * 当前 Activity 仅显示静态占位文字, 让用户能验证 :perf 进程独立启动.
 *
 * @author android_zero
 */
class PerfConsoleActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { MaterialTheme { PerfConsoleSkeletonScreen() } }
  }
}

/**
 * PR #1 骨架占位屏.
 *
 * 后续 PR 会替换为:
 * - 顶部 toolbar: 进程名 / PID / 启动耗时
 * - 4 tab: Boot / Frame / Memory / Threads
 * - Dashboard: 6 card 显示当前监控数据
 */
@Composable
private fun PerfConsoleSkeletonScreen() {
  Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
  ) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
      Column(
          verticalArrangement = Arrangement.spacedBy(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
            text = "Perf Console",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "独立 :perf 进程 · 桌面快捷入口",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
          Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
                text = "PR #1 骨架完成 ✅",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    """
                    • 独立进程 android:process=":perf" 已生效
                    • 桌面启动图标已注册
                    • 等待 PR #2 接入主进程埋点
                    • 等待 PR #3 启动 LocalServerSocket
                    • 等待 PR #4 启动 4 个 Monitor
                    • 等待 PR #5 接入完整 UI
                    """.trimIndent(),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Text(
            text = "详细 spec: docs/superpowers/specs/2026-06-14-perf-console-design.md",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.outline,
        )
      }
    }
  }
}
