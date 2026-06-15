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
import androidx.compose.material3.MaterialTheme
import com.itsaky.androidide.perf.ui.PerfConsoleScreen
import com.itsaky.androidide.perf.ui.PerfConsoleViewModel

/**
 * 性能监控调试控制台 (PR #5 完成).
 *
 * 跑在独立进程 `android:process=":perf"`, 不会被主 application
 * (`com.itsaky.androidide`) 的初始化阻塞. 主 application 启动崩溃时,
 * 此 Activity 仍能启动, 监控 UI 仍能显示.
 *
 * ## 5 步路线图完成
 *
 * - **PR #1**: 骨架 — 独立 :perf 进程 + 桌面图标 ✅
 * - **PR #2**: 主进程 PerfTracer + IDEApplication.onCreate 18 段埋点 ✅
 * - **PR #3**: PerfServerSocket + 跨进程协议 + PhaseCollector ✅
 * - **PR #4**: 4 个 Monitor (FrameRate / Memory / Gc / Anr) + BootHistoryStore ✅
 * - **PR #5 (本 PR)**: 完整 Compose UI ✅
 *
 * @author android_zero
 */
class PerfConsoleActivity : ComponentActivity() {

  // 手动创建 ViewModel 避免 viewModels delegate 依赖 androidx-lifecycle-viewmodel-savedstate
  // (PR #5 走轻依赖路径). Activity onDestroy 时调 onCleared 释放 tickExecutor.
  private val viewModel: PerfConsoleViewModel by lazy { PerfConsoleViewModel(application) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { MaterialTheme { PerfConsoleScreen(viewModel) } }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (isFinishing) {
      viewModel.shutdown()
    }
  }
}
