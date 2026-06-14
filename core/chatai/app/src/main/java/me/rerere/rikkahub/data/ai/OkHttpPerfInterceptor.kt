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
package me.rerere.rikkahub.data.ai

import me.rerere.common.http.NetworkEventSink
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp 网络事件 interceptor (PR #9/N).
 *
 * 测量每个 HTTP 请求的耗时 + 状态码, 通过 [NetworkEventSink] 上报:
 *
 * - 成功: `net_<METHOD>_<host>_<code>_<latencyMs>ms`
 *   e.g. `net_GET_api.rikka-ai.com_200_342ms`
 * - 失败: `net_err_<METHOD>_<host>_<exception>`
 *   e.g. `net_err_POST_api.x.com_ConnectException`
 *
 * ## 接入
 *
 * 在 [me.rerere.rikkahub.di.DataSourceModule] 的 `OkHttpClient` builder 中:
 *
 * ```kotlin
 * .addInterceptor(OkHttpPerfInterceptor())
 * ```
 *
 * ## 采样
 *
 * 默认全部上报. PerfTracer 端通过 [com.itsaky.androidide.perf.tracer.PerfTracer.reportInstant]
 * 自身有 1k 事件 buffer, 超出会自动丢早期事件, 因此不需要预采样.
 *
 * @author android_zero
 */
class OkHttpPerfInterceptor : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val startNs = System.nanoTime()

    val response: Response =
        try {
          chain.proceed(request)
        } catch (t: Throwable) {
          // 失败: 上报异常类型
          val name =
              "net_err_${request.method}_${sanitizeHost(request.url.host)}_${t.javaClass.simpleName}"
          NetworkEventSink.report?.invoke(name)
          throw t
        }

    val latencyMs = (System.nanoTime() - startNs) / 1_000_000
    val name =
        "net_${request.method}_${sanitizeHost(request.url.host)}_${response.code}_${latencyMs}ms"
    NetworkEventSink.report?.invoke(name)
    return response
  }

  /**
   * 清洗 host: 替换 `.` 为 `_` (避免混淆 perf event name 切分).
   *
   * 不切 host 段, 因为 `name` 整体作为 instant 名称解析, host 中的 `.` 不会与前缀冲突.
   * 但为了让 event name 紧凑且易读, 直接保留原 host 即可.
   */
  private fun sanitizeHost(host: String): String =
      if (host.isEmpty()) "unknown" else host.replace('\n', '_').replace(' ', '_').take(64)
}
