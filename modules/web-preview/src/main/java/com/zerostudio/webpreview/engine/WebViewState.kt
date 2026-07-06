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

package com.zerostudio.webpreview.engine

import android.webkit.ConsoleMessage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zerostudio.webpreview.device.DeviceProfile
import com.zerostudio.webpreview.device.DevicePresets

/**
 * WebView 状态持有者。
 *
 * 由 [WebPreviewEngine] 读取并更新, 由 WebPreviewFragment 持有。
 * Compose 可观察, 任何字段变化触发 UI 重组。
 */
class WebViewState(
    initialContent: WebContent,
    initialDeviceProfile: DeviceProfile = DevicePresets.DEFAULT,
) {
    /** 当前加载内容 (变化时触发 reload)。 */
    var content: WebContent by mutableStateOf(initialContent)

    /** 当前设备参数 (变化时触发 UA + viewport 切换)。 */
    var deviceProfile: DeviceProfile by mutableStateOf(initialDeviceProfile)

    /** 缩放级别 (0.25 - 5.0)。 */
    var zoomLevel: Float by mutableStateOf(1.0f)

    /** 是否正在加载。 */
    var loading: Boolean by mutableStateOf(false)

    /** 加载进度 0-100。 */
    var progress: Int by mutableIntStateOf(0)

    /** 页面标题。 */
    var pageTitle: String by mutableStateOf("")

    /** 当前 URL (由 WebViewClient.onPageStarted 更新)。 */
    var currentUrl: String by mutableStateOf("")

    /** 能否后退。 */
    var canGoBack: Boolean by mutableStateOf(false)

    /** 能否前进。 */
    var canGoForward: Boolean by mutableStateOf(false)

    /** console 消息列表 (最多保留 500 条)。 */
    var consoleMessages: List<ConsoleMessageEntry> by mutableStateOf(emptyList())
        internal set

    /** 网络请求列表 (最多保留 200 条)。 */
    var networkRequests: List<NetworkRequestEntry> by mutableStateOf(emptyList())
        internal set

    /** 最后一个错误 (加载失败时设置)。 */
    var lastError: String? by mutableStateOf(null)

    /** 是否开启 Chrome DevTools 远程调试 (setWebContentsDebuggingEnabled)。 */
    var devToolsEnabled: Boolean by mutableStateOf(true)

    /** 主题暗色模式 (控制 WebSettingsCompat.setForceDark)。 */
    var darkMode: Boolean by mutableStateOf(false)

    /** JS 接口注入回调 (key = 接口名, value = 接口对象)。 */
    var javascriptInterfaces: Map<String, Any> by mutableStateOf(emptyMap())

    /** 添加一条 console 消息 (内部调用)。 */
    internal fun addConsoleMessage(message: ConsoleMessage) {
        val entry = ConsoleMessageEntry(
            message = message.message(),
            sourceId = message.sourceId(),
            lineNumber = message.lineNumber(),
            level = message.messageLevel(),
        )
        consoleMessages = (consoleMessages + entry).takeLast(MAX_CONSOLE_ENTRIES)
    }

    /** 添加一条网络请求 (内部调用)。 */
    internal fun addNetworkRequest(request: NetworkRequestEntry) {
        networkRequests = (networkRequests + request).takeLast(MAX_NETWORK_ENTRIES)
    }

    /** 清空 console 消息。 */
    fun clearConsole() {
        consoleMessages = emptyList()
    }

    /** 清空网络请求。 */
    fun clearNetwork() {
        networkRequests = emptyList()
    }

    companion object {
        private const val MAX_CONSOLE_ENTRIES = 500
        private const val MAX_NETWORK_ENTRIES = 200
    }
}

/** console 消息条目。 */
data class ConsoleMessageEntry(
    val message: String,
    val sourceId: String,
    val lineNumber: Int,
    val level: ConsoleMessage.MessageLevel,
)

/** 网络请求条目。 */
data class NetworkRequestEntry(
    val url: String,
    val method: String,
    val timestamp: Long = System.currentTimeMillis(),
    val statusCode: Int = 0,
    val mimeType: String? = null,
)
