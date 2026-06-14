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
package me.rerere.common.http

/**
 * 网络事件 sink (PR #9/N).
 *
 * `chatai/app` (RikkaHub) 模块不直接依赖 `core/app` (AndroidIDE 主 application),
 * 因此 [com.itsaky.androidide.perf.tracer.PerfTracer] 对它是不可见的.
 *
 * 通过 [NetworkEventSink] 这个静态桥:
 * 1. `chatai/app` 的 OkHttp interceptor 调用 [report] 上报
 * 2. `core/app` 的 IDEApplication 在启动时 install 一个真实实现:
 *
 * ```kotlin
 * NetworkEventSink.report = { name -> PerfTracer.reportInstant(name) }
 * ```
 *
 * ## 设计动机
 *
 * - **零依赖环**: chatai 不需要 import core/app 类型, 编译期解耦
 * - **零开销**: default null, interceptor 调 `?.invoke()` 一行代码
 * - **运行时安全**: Release build 不 install, interceptor 调用直接 no-op
 *
 * @author android_zero
 */
object NetworkEventSink {

  /**
   * 网络事件 reporter.
   *
   * - `null` = no-op (default, 用于 Release build / 未启动 Perf Console)
   * - non-null = IDEApplication 已 install, interceptor 调用后转给 PerfTracer
   *
   * 上报的 name 约定: `net_<METHOD>_<host>_<status>_<latencyMs>ms`
   * 例: `net_GET_api.rikka-ai.com_200_342ms`
   */
  @Volatile
  @JvmStatic
  var report: ((name: String) -> Unit)? = null
}
