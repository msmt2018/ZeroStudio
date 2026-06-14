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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itsaky.androidide.perf.PhaseStore
import com.itsaky.androidide.perf.proto.PerfEvent
import com.itsaky.androidide.perf.store.BootHistoryStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Perf Console ViewModel (PR #5/5).
 *
 * 后台线程 1Hz 轮询 [PhaseStore.collector] (启动 phase) + [BootHistoryStore]
 * (历史), 解析 [PerfEvent] 流, 聚合成 [PerfUiState] 推给 UI.
 *
 * ## 线程模型
 *
 * - **轮询线程**: `perf-vm-tick` daemon thread, 1Hz 调用 [tick]
 * - **状态发布**: [MutableStateFlow.update] 是线程安全的, 无锁
 * - **Compose 订阅**: `collectAsState` 在 main thread 读 StateFlow
 *
 * ## 聚合逻辑
 *
 * 启动 phase (18 段) → [PerfUiState.bootEvents] (按收到顺序)
 * 启动历史 → [PerfUiState.bootHistory]
 * 实时 fps (PR #4 FrameRateMonitor 上报 `fps_<n>`) → 滚动 60 个样本窗口
 * 实时 PSS (PR #4 MemoryMonitor 上报 `mem_total_pss_kb_<n>`) → 滚动 60 个样本
 * 实时 GC delta (PR #4 GcMonitor 上报 `gc_count_delta_<n>`) → 滚动 60 个样本
 * ANR 事件 (PR #4 AnrMonitor 上报 `anr_<n>`) → 累积列表
 *
 * @author android_zero
 */
class PerfConsoleViewModel(application: Application) : AndroidViewModel(application) {

  private val _state = MutableStateFlow(PerfUiState.EMPTY)
  val state: StateFlow<PerfUiState> = _state.asStateFlow()

  private val historyStore = BootHistoryStore(application)

  private val tickExecutor = Executors.newSingleThreadScheduledExecutor { r ->
    Thread(r, "perf-vm-tick").apply { isDaemon = true }
  }

  init {
    tickExecutor.scheduleWithFixedDelay(
        { runCatching { tick() } },
        0L,
        TICK_INTERVAL_MS,
        TimeUnit.MILLISECONDS,
    )
  }

  override fun onCleared() {
    super.onCleared()
    tickExecutor.shutdown()
  }

  /**
   * 解析 [PerfEvent] 流.
   *
   * 提取规则:
   * - `fps_<n>` instant → fps 滚动数组
   * - `mem_total_pss_kb_<n>` instant → pss 滚动数组
   * - `gc_count_delta_<n>` instant → gc 滚动数组
   * - `anr_<n>` / `anr_warn_<n>` instant → anr 列表
   * - 其他 instant / phase → 启动 events (PR #2 的 18 段)
   *
   * 名称前缀用 `_` 切分, 因为 metric name 自身是 `fps_<n>` 这种
   */
  internal fun tick() {
    val collector = PhaseStore.collector() ?: run {
      _state.value = PerfUiState.EMPTY.copy(isReady = false)
      return
    }

    val snapshot = collector.snapshot()
    val history = historyStore.readAll()

    val fps = ArrayDeque<Int>(SAMPLE_WINDOW)
    val pss = ArrayDeque<Long>(SAMPLE_WINDOW)
    val gc = ArrayDeque<Long>(SAMPLE_WINDOW)
    val anrs = ArrayList<PerfEvent.Instant>()
    val jank = ArrayList<PerfEvent.Instant>() // PR #10: 慢帧
    val jankPct = ArrayDeque<Int>(SAMPLE_WINDOW) // PR #10: 慢帧 % 滚动
    var slowestMs: Int = 0 // PR #10: 最慢单帧

    // 启动 phase 列表: 只取前 18 条 + end_boot (PR #2 设计)
    val bootEvents =
        snapshot.filter { e ->
          when (e) {
            is PerfEvent.Phase -> true
            is PerfEvent.Instant ->
                e.name !in MONITOR_PREFIXES && e.name != END_BOOT_MARKER
            PerfEvent.EndBoot -> true
          }
        }

    snapshot.forEach { ev ->
      if (ev is PerfEvent.Instant) {
        when {
          ev.name.startsWith("fps_") && ev.name.length > 4 -> {
            ev.name.substring(4).toIntOrNull()?.let { fps.addLast(it) }
          }
          ev.name.startsWith("mem_total_pss_kb_") && ev.name.length > 17 -> {
            ev.name.substring(17).toLongOrNull()?.let { pss.addLast(it) }
          }
          ev.name.startsWith("gc_count_delta_") && ev.name.length > 15 -> {
            ev.name.substring(15).toLongOrNull()?.let { gc.addLast(it) }
          }
          ev.name.startsWith("anr_") -> anrs.add(ev)
          ev.name.startsWith("jank_pct_") && ev.name.length > 9 -> {
            // PR #10: jank_pct_<n>
            ev.name.substring(9).toIntOrNull()?.let { jankPct.addLast(it) }
          }
          ev.name.startsWith("jank_max_") && ev.name.endsWith("ms") -> {
            // PR #10: jank_max_<n>ms
            val ms = ev.name.removePrefix("jank_max_").removeSuffix("ms").toIntOrNull() ?: 0
            if (ms > slowestMs) slowestMs = ms
          }
          ev.name.startsWith("jank_") && ev.name.endsWith("ms") -> {
            // PR #10: jank_<n>ms (排除 jank_max_)
            val ms = ev.name.removePrefix("jank_").removeSuffix("ms").toIntOrNull() ?: 0
            if (ms > slowestMs) slowestMs = ms
            jank.add(ev)
          }
        }
      }
    }

    // 滚动窗口: 保留最后 SAMPLE_WINDOW 个
    while (fps.size > SAMPLE_WINDOW) fps.removeFirst()
    while (pss.size > SAMPLE_WINDOW) pss.removeFirst()
    while (gc.size > SAMPLE_WINDOW) gc.removeFirst()
    while (jankPct.size > SAMPLE_WINDOW) jankPct.removeFirst()

    val startMs = collector.startElapsedMs()
    val totalBootMs =
        if (startMs == 0L) 0L
        else android.os.SystemClock.elapsedRealtime() - startMs

    _state.value =
        PerfUiState(
            isReady = true,
            isBootEnded = collector.isBootEnded(),
            bootEvents = bootEvents,
            bootHistory = history,
            recentFps = fps.toList(),
            recentPssKb = pss.toList(),
            recentGcDelta = gc.toList(),
            anrEvents = anrs,
            jankEvents = jank, // PR #10
            recentJankPct = jankPct.toList(), // PR #10
            slowestFrameMs = slowestMs, // PR #10
            totalBootMs = totalBootMs,
        )
  }

  companion object {
    /** 1Hz 轮询间隔. */
    private const val TICK_INTERVAL_MS = 1000L

    /** 折线图样本窗口 (60 个 = 1 分钟历史). */
    private const val SAMPLE_WINDOW = 60

    /**
     * Monitor 上报的 instant name 前缀 (用于过滤掉 metric 事件, 只保留 IDEApplication
     * 上报的启动 phase 标记).
     */
    private val MONITOR_PREFIXES =
        setOf(
            "fps_",
            "mem_",
            "gc_",
            "anr_",
            "jank_", // PR #10: jank_pct_*, jank_max_*ms, jank_*ms
        )

    /** IDEApplication.onCreate 末端的 instant. */
    private const val END_BOOT_MARKER = "ide_on_create_end"
  }
}
