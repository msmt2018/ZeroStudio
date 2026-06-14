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

import com.itsaky.androidide.perf.server.PerfServerSocket
import com.itsaky.androidide.perf.server.PhaseCollector

/**
 * :perf 进程内的全局 holder (PR #3/5).
 *
 * [com.itsaky.androidide.perf.PerfApplication.onCreate] 启动 [PerfServerSocket]
 * 时, 把 [PhaseCollector] 和 server 实例 bind 到本单例.
 * UI (PR #5) 通过 [collector] 读当前累积数据, 通过 [server] 在 onDestroy
 * 时关闭.
 *
 * ## 设计
 *
 * - 单例 (object) 因为 :perf 进程内只有一个 Application + 一个 server
 * - 不在主进程使用 ([com.itsaky.androidide.perf.tracer.PerfTracer] 是主进程
 *   自己的 socket client, 不通过本 holder 通信)
 * - 没有锁: bind() 只在 onCreate 后台线程调一次, 读在 UI 线程, 内存模型上
 *   safe (init 顺序保证 happens-before 关系)
 *
 * @author android_zero
 */
object PhaseStore {

  @Volatile private var _collector: PhaseCollector? = null

  @Volatile private var _server: PerfServerSocket? = null

  /**
   * 在 [com.itsaky.androidide.perf.PerfApplication.onCreate] 调一次.
   */
  fun bind(collector: PhaseCollector, server: PerfServerSocket) {
    _collector = collector
    _server = server
  }

  /** 当前 [PhaseCollector], 启动前返回 null. */
  fun collector(): PhaseCollector? = _collector

  /** 当前 [PerfServerSocket], 启动前返回 null. */
  fun server(): PerfServerSocket? = _server

  /** 是否已 bind (UI 用此判断"等待 server 启动" vs "已连接"). */
  fun isReady(): Boolean = _collector != null && _server != null
}
