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

/**
 * DevTools 前端 URL 构造器。
 *
 * 用户提供的 Chrome DevTools 前端 URL 形如:
 * ```
 * https://chrome-devtools-frontend.appspot.com/serve_file/@<hash>/devtools_app.html?ws=<ws>&targetType=tab
 * ```
 *
 * 由 [buildDevToolsUrl] 拼接被调试 WebView 的 WebSocket 端点 (`ws://localhost:9222/devtools/page/<id>`)
 * 与前端 hash, 得到一个可直接加载到第二个 WebView 的完整 URL。
 *
 * 参考: https://developer.chrome.com/docs/devtools
 */
object DevToolsFrontendLoader {

    /**
     * Chrome DevTools 前端版本 hash。
     *
     * 来自用户提供的示例 URL:
     * `devtools://devtools/bundled/devtools_app.html?remoteBase=https://chrome-devtools-frontend.appspot.com/serve_file/@9c25b0453847af990895e9f681a4710784e34245/&targetType=tab`
     *
     * 该 hash 由 Google 部署的 DevTools 前端 CDN 维护, 切换版本只需改这一处。
     */
    private const val FRONTEND_HASH = "9c25b0453847af990895e9f681a4710784e34245"

    /** DevTools 前端 CDN 根 */
    private const val FRONTEND_BASE =
        "https://chrome-devtools-frontend.appspot.com/serve_file/@$FRONTEND_HASH/devtools_app.html"

    /**
     * 构造可加载到 DevTools WebView 的完整 URL。
     *
     * @param wsUrl 被调试 WebView 的 CDP WebSocket 端点,
     *              形如 `ws://localhost:9222/devtools/page/<id>`
     * @return 完整的 DevTools 前端 URL, 形如
     *         `https://chrome-devtools-frontend.appspot.com/serve_file/@<hash>/devtools_app.html?ws=localhost:9222/devtools/page/<id>&targetType=tab`
     */
    fun buildDevToolsUrl(wsUrl: String): String {
        // ws://localhost:9222/devtools/page/<id> → localhost:9222/devtools/page/<id>
        val wsTarget = wsUrl.removePrefix("ws://").removePrefix("wss://")
        return "$FRONTEND_BASE?ws=$wsTarget&targetType=tab"
    }

    /** 仅返回前端 base URL (不含 ws 参数), 用于诊断或预加载 */
    fun frontendBaseUrl(): String = FRONTEND_BASE
}
