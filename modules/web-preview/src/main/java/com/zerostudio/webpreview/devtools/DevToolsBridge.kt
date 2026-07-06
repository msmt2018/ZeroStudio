/*
 * This file is part of ZeroStudio.
 *
 * ZeroStudio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ZeroStudio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ZeroStudio. If not, see <https://www.gnu.org/licenses/>.
 */

package com.zerostudio.webpreview.devtools

import android.content.Context
import android.os.Process
import org.json.JSONArray
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * DevTools 桥接器: 把被调试 WebView 的 CDP unix socket 桥接到 localhost TCP,
 * 并查询 page 列表, 给 [DevToolsFrontendLoader] 提供可用的 WebSocket 端点。
 *
 * <p>
 * <b>三段式流程</b>:
 * <ol>
 *   <li>被调试 WebView 调 `WebView.setWebContentsDebuggingEnabled(true)` →
 *       暴露 abstract unix socket `@webview_devtools_remote_<pid>`</li>
 *   <li>[LocalSocketForwarder] 把 abstract socket 转发到 `localhost:9222` TCP</li>
 *   <li>查询 `http://localhost:9222/json` 拿到 page 列表 →
 *       [DevToolsFrontendLoader.buildDevToolsUrl] 构造 DevTools 前端 URL →
 *       加载到 DevTools WebView 显示完整 UI</li>
 * </ol>
 *
 * <p>
 * <b>降级</b>: 若 [findDevToolsSocket] 失败 (WebView 未开调试 / SELinux 阻断 /proc/net/unix)
 * 或 forwarder 启动失败, 返回 [DevToolsEndpoint.Failed], 由 Fragment 提示用户用电脑 Chrome
 * `chrome://inspect` 远程调试。
 *
 * @param context Android Context
 * @param tcpPort 转发到的 localhost TCP 端口 (默认 9222)
 */
class DevToolsBridge(
    private val context: Context,
    private val tcpPort: Int = DEFAULT_TCP_PORT,
) {
    private val log = LoggerFactory.getLogger("DevToolsBridge")

    @Volatile private var forwarder: LocalSocketForwarder? = null
    @Volatile private var currentEndpoint: DevToolsEndpoint = DevToolsEndpoint.Idle

    /**
     * 启动桥接, 返回可用的 [DevToolsEndpoint]。
     *
     * <p>
     * 注意: 需在调用方确保被调试 WebView 已经 `setWebContentsDebuggingEnabled(true)`
     * 并至少加载过一次内容 (CDP socket 才会被创建)。
     *
     * @param retryCount 找不到 socket 时的重试次数 (WebView 创建 socket 有延迟)
     * @param retryIntervalMs 重试间隔
     */
    fun start(retryCount: Int = 5, retryIntervalMs: Long = 200L): DevToolsEndpoint {
        if (forwarder?.isRunning() == true) {
            log.info("Forwarder already running")
            return currentEndpoint
        }

        // 1. 找到被调试 WebView 的 CDP unix socket 名 (带重试)
        val socketName = findDevToolsSocketWithRetry(retryCount, retryIntervalMs)
            ?: run {
                log.error("No DevTools socket found for pid {}", Process.myPid())
                currentEndpoint = DevToolsEndpoint.Failed(
                    "未找到 WebView DevTools socket。请确保已开启 DevTools 调试开关。"
                )
                return currentEndpoint
            }

        // 2. 启动 LocalSocketForwarder: abstract socket → localhost:tcpPort
        try {
            forwarder = LocalSocketForwarder(socketName, tcpPort).also { it.start() }
        } catch (e: IOException) {
            log.error("Failed to start forwarder on port {}", tcpPort, e)
            currentEndpoint = DevToolsEndpoint.Failed(
                "端口 $tcpPort 被占用或无权限: ${e.message}"
            )
            return currentEndpoint
        }

        // 3. 等 forwarder 就绪, 查询 page 列表
        val pages = try {
            Thread.sleep(100L)  // 给 forwarder 一点时间进入 accept
            queryDevToolsPages()
        } catch (e: Exception) {
            log.error("Failed to query DevTools pages", e)
            currentEndpoint = DevToolsEndpoint.Failed(
                "查询 DevTools 页面失败: ${e.message}"
            )
            return currentEndpoint
        }

        if (pages.isEmpty()) {
            log.warn("DevTools returned empty page list")
            currentEndpoint = DevToolsEndpoint.Failed(
                "DevTools 页面列表为空。请确保被调试 WebView 已加载内容。"
            )
            return currentEndpoint
        }

        val firstPage = pages.first()
        log.info("DevTools ready: {}", firstPage.webSocketDebuggerUrl)
        currentEndpoint = DevToolsEndpoint.Ready(
            wsUrl = firstPage.webSocketDebuggerUrl,
            frontendUrl = DevToolsFrontendLoader.buildDevToolsUrl(firstPage.webSocketDebuggerUrl),
            pages = pages,
        )
        return currentEndpoint
    }

    /** 停止桥接, 关闭 forwarder */
    fun stop() {
        forwarder?.stop()
        forwarder = null
        currentEndpoint = DevToolsEndpoint.Idle
    }

    /** 当前 endpoint 状态 (Idle / Ready / Failed) */
    fun currentEndpoint(): DevToolsEndpoint = currentEndpoint

    // ── 内部: 找 DevTools socket ─────────────────────────────

    /**
     * 找到本进程 (com.itsaky.androidide) 的 DevTools abstract socket 名。
     *
     * <p>
     * WebView 在 abstract namespace 暴露 socket, 形如:
     * - `@webview_devtools_remote_<pid>`  (本进程)
     * - `@webview_devtools_remote_<pid>_1` (多 WebView 时)
     *
     * <p>
     * 实现: 读 `/proc/net/unix`, 找匹配 `webview_devtools_remote_<myPid>` 前缀的行。
     * abstract socket 在该文件中第一字段为 00000000 (无文件系统路径)。
     */
    private fun findDevToolsSocket(): String? {
        val myPid = Process.myPid()
        val prefix = "webview_devtools_remote_$myPid"
        return try {
            File("/proc/net/unix").useLines { lines ->
                // 格式: Num RefCount Protocol Flags Type St Inode Path
                // abstract socket 的 Path 以 NULL 字节开头, 在文本中表现为前缀 \0
                // 但读出来时 Path 是字符串, abstract namespace 路径前会有 \u0000
                lines.forEach { line ->
                    val cols = line.trim().split(Regex("\\s+"))
                    if (cols.size >= 8) {
                        val path = cols[7]
                        // abstract socket: path 形如 "\u0000webview_devtools_remote_1234"
                        val cleaned = path.removePrefix("\u0000")
                        if (cleaned.startsWith(prefix)) {
                            return@useLines "@$cleaned"  // 还原 @ 前缀表示 abstract
                        }
                    }
                }
                null
            }
        } catch (e: IOException) {
            log.warn("Failed to read /proc/net/unix (SELinux?): {}", e.message)
            null
        }
    }

    /** 带重试的 [findDevToolsSocket] (WebView 创建 socket 有几十 ms 延迟) */
    private fun findDevToolsSocketWithRetry(retryCount: Int, intervalMs: Long): String? {
        repeat(retryCount + 1) { i ->
            findDevToolsSocket()?.let { return it }
            if (i < retryCount) Thread.sleep(intervalMs)
        }
        return null
    }

    // ── 内部: 查询 DevTools page 列表 ────────────────────────

    /**
     * HTTP GET `http://localhost:<tcpPort>/json` 拿到 CDP page 列表。
     *
     * <p>
     * 返回 JSON 数组, 每项形如:
     * ```json
     * {
     *   "id": "ABCDEF1234567890",
     *   "type": "page",
     *   "title": "Page Title",
     *   "url": "https://example.com",
     *   "webSocketDebuggerUrl": "ws://localhost:9222/devtools/page/ABCDEF1234567890"
     * }
     * ```
     */
    private fun queryDevToolsPages(): List<DevToolsPage> {
        val url = URL("http://localhost:$tcpPort/json")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3000
            readTimeout = 3000
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code != 200) {
                throw IOException("HTTP $code from DevTools /json")
            }
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            return parsePages(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parsePages(json: String): List<DevToolsPage> {
        val arr = JSONArray(json)
        val result = mutableListOf<DevToolsPage>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val type = obj.optString("type", "")
            // 只要 page 类型 (跳过 service_worker / browser / iframe 等)
            if (type != "page") continue
            result += DevToolsPage(
                id = obj.optString("id", ""),
                type = type,
                title = obj.optString("title", ""),
                url = obj.optString("url", ""),
                webSocketDebuggerUrl = obj.optString("webSocketDebuggerUrl", ""),
            )
        }
        return result
    }

    /** 后台异步启动桥接, 完成后回调 */
    fun startAsync(onReady: (DevToolsEndpoint) -> Unit) {
        thread(name = "DevToolsBridge-start", isDaemon = true) {
            val endpoint = start()
            onReady(endpoint)
        }
    }

    private companion object {
        const val DEFAULT_TCP_PORT = 9222
    }
}

// ── 数据类 ──────────────────────────────────────────────────

/** CDP page 列表项 */
data class DevToolsPage(
    val id: String,
    val type: String,
    val title: String,
    val url: String,
    val webSocketDebuggerUrl: String,
)

/** DevTools 桥接端点状态 */
sealed class DevToolsEndpoint {
    /** 初始/未启动 */
    object Idle : DevToolsEndpoint()

    /**
     * 桥接就绪, 可加载 [frontendUrl] 到 DevTools WebView。
     *
     * @param wsUrl 被调试 WebView 的 WebSocket 端点, 如 `ws://localhost:9222/devtools/page/<id>`
     * @param frontendUrl DevTools 前端 URL, 直接 loadUrl 到 WebView 即可显示完整 UI
     * @param pages 可用的 page 列表
     */
    data class Ready(
        val wsUrl: String,
        val frontendUrl: String,
        val pages: List<DevToolsPage>,
    ) : DevToolsEndpoint()

    /**
     * 桥接失败, [reason] 用于 UI 提示。
     *
     * <p>
     * 建议向用户提供降级指引: 用电脑 Chrome 访问 `chrome://inspect`,
     * 通过 USB / 网络远程调试本机 WebView。
     */
    data class Failed(val reason: String) : DevToolsEndpoint()
}
