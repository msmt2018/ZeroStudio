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
package com.itsaky.androidide.perf.proto

/**
 * 主进程 → :perf 进程的跨进程事件.
 *
 * 三种类型:
 *
 * - [Phase]: 一段代码块的耗时 (用 [com.itsaky.androidide.perf.tracer.PerfTracer.trace])
 * - [Instant]: 无耗时的瞬时事件 (e.g. `ide_on_create_begin` / `end_boot` 之前的 marker)
 * - [EndBoot]: 启动阶段结束标记. server 收到后切换为"启动后采样"模式 (PR #4)
 *
 * 序列化: line-delimited JSON, 由 [PerfSocketProtocol] 编解码.
 *
 * @author android_zero
 */
sealed class PerfEvent {

  /** 一段代码块的耗时测量. */
  data class Phase(
      val name: String,
      val elapsedMs: Long,
  ) : PerfEvent()

  /** 瞬时事件 (无耗时, 仅记录时间点). */
  data class Instant(
      val name: String,
  ) : PerfEvent()

  /** 启动阶段结束. */
  data object EndBoot : PerfEvent()
}
