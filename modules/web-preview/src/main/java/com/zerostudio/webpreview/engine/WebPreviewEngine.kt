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

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.slf4j.LoggerFactory

/**
 * Compose WebView 预览引擎。
 *
 * 基于 androidx.webkit, 封装 [WebView] + [WebChromeClient] + [WebViewClient],
 * 提供状态可观察的预览组件。
 *
 * 功能:
 * - 加载 [WebContent] (Url / File / Data)
 * - 设备切换 (UA + viewport, 真实重渲染)
 * - console 消息捕获
 * - 网络请求捕获 (shouldInterceptRequest)
 * - Chrome DevTools 远程调试开关
 * - 主题暗色适配 (WebSettingsCompat.setForceDark)
 * - 缩放 (zoomLevel)
 *
 * @param state WebView 状态
 * @param modifier 布局修饰
 * @param onUrlChange URL 变化回调
 * @param consoleCapture 是否捕获 console 消息 (默认 true)
 * @param networkCapture 是否捕获网络请求 (默认 true)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPreviewEngine(
    state: WebViewState,
    modifier: Modifier = Modifier,
    onUrlChange: (String) -> Unit = {},
    onWebViewCreated: (WebView) -> Unit = {},
    consoleCapture: Boolean = true,
    networkCapture: Boolean = true,
) {
    val log = remember { LoggerFactory.getLogger("WebPreviewEngine") }

    // 保留 WebView 实例 (跨重组复用, 避免每次重组都重建)
    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                // 开启全局 DevTools 调试 (静态方法, 进程级生效, API 19+)
                // 暴露 abstract unix socket: @webview_devtools_remote_<pid>
                WebView.setWebContentsDebuggingEnabled(state.devToolsEnabled)

                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    configureSettings(state, consoleCapture, networkCapture)
                    setupClients(state, onUrlChange, consoleCapture, networkCapture, log)
                    injectJavascriptInterfaces(state)
                    loadContent(state.content)
                    webView = this
                    onWebViewCreated(this)
                }
            },
            update = { view ->
                // 设备参数变化 → 更新 UA + viewport
                applyDeviceProfile(view, state.deviceProfile)
                // 缩放变化
                if (view.scaleX != state.zoomLevel && state.zoomLevel > 0f) {
                    view.scaleX = state.zoomLevel
                    view.scaleY = state.zoomLevel
                }
                // 主题暗色变化
                applyDarkMode(view.settings, state.darkMode)
                // DevTools 开关变化
                WebView.setWebContentsDebuggingEnabled(state.devToolsEnabled)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    // content 变化 → reload
    LaunchedEffect(state.content) {
        webView?.loadContent(state.content)
    }

    // javascriptInterfaces 变化 → 重新注入
    LaunchedEffect(state.javascriptInterfaces) {
        webView?.let { injectJavascriptInterfaces(state, it) }
    }

    // 销毁时释放 WebView
    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                removeAllViews()
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
            webView = null
        }
    }
}

// ── 内部: WebView 配置 ──────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureSettings(
    state: WebViewState,
    consoleCapture: Boolean,
    networkCapture: Boolean,
) {
    settings.apply {
        // JavaScript + DOM
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true

        // 文件访问 (本地 HTML 加载)
        allowFileAccess = true
        allowContentAccess = true

        // 缩放
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false

        // 视口
        useWideViewPort = true
        loadWithOverviewMode = true

        // 混合内容 (允许 https 页面加载 http 资源, dev server 常用)
        @Suppress("DEPRECATION")
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Media
        mediaPlaybackRequiresUserGesture = false

        // Cache
        cacheMode = WebSettings.LOAD_DEFAULT

        // UA + viewport (设备参数)
        applyDeviceProfile(this@configureSettings, state.deviceProfile)

        // 主题暗色
        applyDarkMode(this, state.darkMode)
    }

    // Cookie
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
}

private fun WebView.setupClients(
    state: WebViewState,
    onUrlChange: (String) -> Unit,
    consoleCapture: Boolean,
    networkCapture: Boolean,
    log: org.slf4j.Logger,
) {
    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            state.loading = true
            state.progress = 0
            state.currentUrl = url ?: ""
            onUrlChange(state.currentUrl)
            state.lastError = null
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            state.loading = false
            state.progress = 100
            state.canGoBack = view?.canGoBack() ?: false
            state.canGoForward = view?.canGoForward() ?: false
            view?.title?.let { state.pageTitle = it }
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?,
        ) {
            // dev server 常用自签证书, 放行
            handler?.proceed()
            log.warn("SSL error ignored: {}", error)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: android.webkit.WebResourceError?,
        ) {
            if (request?.isForMainFrame == true) {
                state.lastError = error?.description?.toString() ?: "Unknown error"
                log.warn("Main frame error: {}", error?.description)
            }
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?,
        ): WebResourceResponse? {
            if (networkCapture && request != null) {
                val entry = NetworkRequestEntry(
                    url = request.url?.toString() ?: "",
                    method = request.method ?: "GET",
                )
                state.addNetworkRequest(entry)
            }
            return super.shouldInterceptRequest(view, request)
        }
    }

    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            state.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            state.pageTitle = title ?: ""
        }

        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
            if (consoleCapture && consoleMessage != null) {
                state.addConsoleMessage(consoleMessage)
                log.debug("JS console [{}]: {} ({}:{})",
                    consoleMessage.messageLevel(),
                    consoleMessage.message(),
                    consoleMessage.sourceId(),
                    consoleMessage.lineNumber())
            }
            return true
        }
    }
}

// ── 内部: 设备参数应用 ─────────────────────────────────────

private fun applyDeviceProfile(webView: WebView, profile: com.zerostudio.webpreview.device.DeviceProfile) {
    val settings = webView.settings
    if (profile.userAgent.isNotEmpty()) {
        settings.userAgentString = profile.userAgent
    }
    // viewport 尺寸通过 setInitialScale + useWideViewPort 控制
    // 真实视口尺寸由 WebView 容器决定, 这里只设 UA + 缩放策略
    // 注: setInitialScale 是 WebView 的方法, 不是 WebSettings 的
    if (profile.viewportWidth > 0 && webView.width > 0) {
        val density = webView.context.resources.displayMetrics.density
        val scale = (profile.viewportWidth * density / webView.width.toFloat()).coerceIn(0.1f, 10f)
        webView.setInitialScale((scale * 100).toInt())
    }
}

@Suppress("DEPRECATION")  // FORCE_DARK 在 androidx.webkit 1.6.0+ 弃用, 但 1.17.0-alpha03 仍保留
private fun applyDarkMode(settings: WebSettings, darkMode: Boolean) {
    try {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(
                settings,
                if (darkMode) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF,
            )
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
            WebSettingsCompat.setForceDarkStrategy(
                settings,
                WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY,
            )
        }
    } catch (e: Exception) {
        // androidx.webkit 版本不支持, 忽略
    }
}

// ── 内部: JS 接口注入 ──────────────────────────────────────

private fun WebView.injectJavascriptInterfaces(state: WebViewState) {
    injectJavascriptInterfaces(state, this)
}

private fun injectJavascriptInterfaces(state: WebViewState, webView: WebView) {
    // 先移除旧的
    state.javascriptInterfaces.keys.forEach { name ->
        webView.removeJavascriptInterface(name)
    }
    // 注入新的
    state.javascriptInterfaces.forEach { (name, obj) ->
        webView.addJavascriptInterface(obj, name)
    }
}

// ── 内部: 内容加载 ─────────────────────────────────────────

private fun WebView.loadContent(content: WebContent) {
    when (content) {
        is WebContent.Url -> loadUrl(content.url)
        is WebContent.File -> loadUrl(content.toFileUri())
        is WebContent.Data -> {
            loadDataWithBaseURL(
                content.baseUrl,
                content.html,
                content.mimeType,
                "utf-8",
                null,
            )
        }
    }
}
