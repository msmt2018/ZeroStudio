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
package com.itsaky.androidide.perf.server

import com.itsaky.androidide.perf.proto.PerfEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 内存累积 phase 数据 (PR #3/5).
 *
 * 跑在 :perf 进程, 接收主进程通过 socket 发来的 [PerfEvent], 按收到顺序
 * 累积到 [events] 列表. UI (PR #5) 通过 [snapshot] 周期性拉取 (1Hz).
 *
 * ## 线程安全
 *
 * - 写: accept loop 线程 ([com.itsaky.androidide.perf.server.PerfServerSocket] 的
 *   每个 client handler) 并发调用 [collect]. 用 [CopyOnWriteArrayList] 保证
 *   写不阻塞读, 读快照原子.
 * - 读: UI 线程 (Compose recomposition 触发) 调用 [snapshot] / [phases] /
 *   [instants]. CopyOnWriteArrayList 的 iterator 是弱一致但不会抛
 *   ConcurrentModificationException.
 *
 * ## 容量
 *
 * 启动期 ~18 phase + 几十个 instant, 稳态运行时主要由 PR #4 的 Monitor 驱动
 * (帧率/内存/GC 各 1Hz 采样), 单 session 跑 1 小时约 10000 条, 远低于
 * CopyOnWriteArrayList 的实际容量. 不需要在 PR #3 引入分页.
 *
 * @author android_zero
 */
class PhaseCollector {

  private val events = CopyOnWriteArrayList<PerfEvent>()

  @Volatile private var startElapsedMs: Long = 0L

  @Volatile private var bootEnded: Boolean = false

  /** EndBoot 监听器列表. [BootHistoryStore] 用此 hook 在启动结束时持久化. */
  private val endBootListeners = java.util.concurrent.CopyOnWriteArrayList<(List<PerfEvent>, Long) -> Unit>()

  /** ANR 监听器列表. PR #8 加, [ThreadDumper] 用此 hook 自动 dump 线程. */
  private val anrListeners =
      java.util.concurrent.CopyOnWriteArrayList<(name: String, latencyMs: Long) -> Unit>()

  /** 上次已通知监听器的 ANR 计数 (避免重复触发). */
  @Volatile private var lastNotifiedAnrCount: Int = 0

  /**
   * 收集一个事件.
   *
   * - 首次调用时记录 [startElapsedMs] = [SystemClock.elapsedRealtime]
   * - 启动阶段 ([bootEnded] = false) 累积所有 phase / instant
   * - 收到 [PerfEvent.EndBoot] 后切换稳态模式
   */
  fun collect(event: PerfEvent) {
    if (startElapsedMs == 0L) {
      startElapsedMs = android.os.SystemClock.elapsedRealtime()
    }
    if (event is PerfEvent.EndBoot) {
      bootEnded = true
      // 通知所有 listener (BootHistoryStore 等)
      val snapshot = events.toList()
      endBootListeners.forEach { listener ->
        runCatching { listener(snapshot, startElapsedMs) }
      }
    }

    // PR #8: 检测新 ANR (主进程发的 anr_<latency> instant), 触发 listener (e.g. ThreadDumper)
    if (event is PerfEvent.Instant && event.name.startsWith("anr_") &&
        !event.name.startsWith("anr_warn_")) {
      val currentAnrCount = events.count {
        it is PerfEvent.Instant &&
            it.name.startsWith("anr_") &&
            !it.name.startsWith("anr_warn_")
      }
      if (currentAnrCount > lastNotifiedAnrCount) {
        lastNotifiedAnrCount = currentAnrCount
        val latency = event.name.substring(4).toLongOrNull() ?: 0L
        anrListeners.forEach { listener ->
          runCatching { listener(event.name, latency) }
        }
      }
    }

    events.add(event)
  }

  /**
   * 注册 EndBoot 监听器.
   *
   * 监听器签名 `(events, startElapsedMs) -> Unit`, 在 [collect] 收到
   * [PerfEvent.EndBoot] 时同步调用 (监听器抛异常被吞掉, 不影响 collect 主流程).
   */
  fun addEndBootListener(listener: (List<PerfEvent>, Long) -> Unit) {
    endBootListeners.add(listener)
  }

  /**
   * 注册 ANR 监听器 (PR #8).
   *
   * 监听器签名 `(name, latencyMs) -> Unit`, 在 [collect] 收到 `anr_<latency>`
   * instant 时同步调用 (warn 不触发, 只触发真 ANR). 用于 [ThreadDumper] 自动 dump.
   *
   * 去重: 同一 ANR 不会重复触发, 监听器只在新增时调一次.
   */
  fun addAnrListener(listener: (name: String, latencyMs: Long) -> Unit) {
    anrListeners.add(listener)
  }

  /** 启动是否已结束 (用于 UI 切换 tab 颜色 / 显示 banner). */
  fun isBootEnded(): Boolean = bootEnded

  /**
   * 整个 session 的 [SystemClock.elapsedRealtime] 起点 (用于显示"启动耗时
   * 总计"). 0 表示尚未收到任何事件.
   */
  fun startElapsedMs(): Long = startElapsedMs

  /**
   * 当前累积的所有事件 (按收到顺序).
   *
   * 返回的是不可变快照, 业务代码可安全遍历.
   */
  fun snapshot(): List<PerfEvent> = events.toList()

  /** 仅 phase 事件 (按时间排序). */
  fun phases(): List<PerfEvent.Phase> = events.filterIsInstance<PerfEvent.Phase>()

  /** 仅 instant 事件. */
  fun instants(): List<PerfEvent.Instant> = events.filterIsInstance<PerfEvent.Instant>()

  /** 清空所有累积 (用于"重新记录"按钮). */
  fun clear() {
    events.clear()
    startElapsedMs = 0L
    bootEnded = false
  }

  /** 当前累积事件数 (调试用). */
  fun size(): Int = events.size
}
