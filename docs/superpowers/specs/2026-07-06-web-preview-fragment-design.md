# 设计文档：Web 预览 Fragment

**日期**: 2026-07-06
**作者**: ZeroStudio
**前置**: 音频/视频预览 Fragment 已完成 (commit `4b3f6428`)
**不在本次范围**: proot Linux 容器生态 (本次预留 `BackendRuntime` 抽象, 仅实现 Termux 后端)

---

## 1. 背景与目标

### 1.1 现状

编辑器已有 5 个预览 tab: Markdown / Image / Universal(C/C++) / Audio / Video。
点击 `.html` 等前端文件当前走系统 `Intent.ACTION_VIEW` 调外部浏览器, 把用户切出 IDE。
项目内无 HTML 专用预览, `UniversalPreviewEngineFragment` 的 WebView 是为 Three.js 3D 可视化服务, 不是通用 HTML 预览。

### 1.2 目标

新增 **Web 预览 Fragment**:
- 基于 `androidx.webkit:1.17.0-alpha03` (APK 0 增加, 用系统 WebView)
- 支持静态 HTML/CSS/JS + Vue/React 构建产物 + 本地 dev server 代理 + 后端运行时预览
- 内嵌完整 Chrome DevTools UI (用户给的 `devtools://devtools/bundled/devtools_app.html` 方案)
- 设备切换 (UA + 视口真实重渲染, 预置 11 档设备参数, 真实发起网络请求含 POST)
- 缩放 (手势捏合 + 按钮)
- UI 控件全部用 FrostedGlass 磨砂玻璃效果 (与音视频 fragment 一致), 图标颜色随主题自适应
- 后端运行时通过项目内置 Termux 模块同进程执行 (用户 `pkg install nodejs/python/php` 按需装环境)

### 1.3 非目标

- 不内置编译浏览器内核 (用 `androidx.webkit` compat 层 + 系统 WebView)
- 不打包 Node.js/Python/PHP 运行时到 APK (违背轻量化, 由 Termux 按需安装)
- 不实现 proot Linux 容器 (本次仅预留 `BackendRuntime` 抽象接口)

---

## 2. 架构

### 2.1 模块组织

```
modules/web-preview/                    # 新建 library 模块 (纯逻辑)
├── build.gradle.kts
└── src/main/java/com/zerostudio/webpreview/
    ├── engine/                         # WebView 引擎封装
    │   ├── WebPreviewEngine.kt         # Compose WebView 组件
    │   ├── WebContent.kt               # sealed class: Url / File / Data
    │   ├── WebViewState.kt             # 状态持有者
    │   └── WebSettingsConfig.kt        # WebSettings 配置
    ├── device/                         # 设备参数库
    │   ├── DeviceProfile.kt            # data class
    │   └── DevicePresets.kt            # 11 档预置设备
    ├── backend/                        # 后端运行时抽象
    │   ├── BackendRuntime.kt           # interface
    │   ├── RuntimeSession.kt           # data class
    │   └── TermuxBackendRuntime.kt     # Termux 实现 (同进程 TermuxCommand)
    └── devtools/                       # DevTools 桥接
        ├── DevToolsBridge.kt           # CDP socket 桥接
        ├── DevToolsFrontendLoader.kt   # DevTools 前端 URL 构造
        └── LocalSocketForwarder.kt     # unix socket → localhost TCP

core/app/src/main/java/com/itsaky/androidide/fragments/editor/web/
└── WebPreviewFragment.kt               # Fragment (UI 组装, 用 FrostedComponents)
```

**模块依赖**:
- `core/app` → `modules/web-preview` (逻辑)
- `modules/web-preview` → `termux/shell` (BackendRuntime 用 TermuxCommand)
- `modules/web-preview` → `androidx.webkit:1.17.0-alpha03`

### 2.2 主题感知

与音视频 fragment 一致:
- 所有控件用 `FrostedGlass` / `FrostedIconButton` / `FrostedSlider` / `FrostedText`
- 磨砂玻璃效果 (Haze API 31+ 真模糊 / 低版本渐变模拟) 固定不随主题切换
- 图标/文本颜色通过 `LocalDarkMode` 在黑白主题自适应 (暗色→白图标, 亮色→黑图标)
- WebView 内容自身的暗色适配通过 `WebSettingsCompat.setForceDark` 控制 (androidx.webkit API)

### 2.3 DevTools 嵌入方案

**挑战**: Android WebView 不能直接加载 `devtools://` scheme。

**方案** (三段式):
1. **被调试 WebView**: 调用 `WebView.setWebContentsDebuggingEnabled(true)`, 暴露 CDP unix domain socket (`@webview_devtools_remote_<pid>`)
2. **LocalSocket 桥接**: `LocalSocketForwarder` 把 abstract unix socket 转发到 `localhost:9222` TCP, 让 HTTP + WS 可达
3. **DevTools 前端 WebView**: 第二个 WebView 加载 `https://chrome-devtools-frontend.appspot.com/serve_file/@<hash>/devtools_app.html?ws=localhost:9222/devtools/page/<id>`, 显示完整 DevTools UI

**降级**: 若 LocalSocket 桥接失败 (权限/SELinux), 回退到提示用户用电脑 Chrome `chrome://inspect` 远程调试。

### 2.4 后端运行时集成

```kotlin
interface BackendRuntime {
    val name: String  // "Termux" / "Proot"(未来)
    fun isAvailable(): Boolean
    suspend fun startService(workDir: File, command: String, port: Int): RuntimeSession
    fun stopService(session: RuntimeSession)
}

// Termux 实现 (同进程, 用 TermuxCommand DSL)
class TermuxBackendRuntime : BackendRuntime {
    override suspend fun startService(workDir: File, command: String, port: Int): RuntimeSession {
        // FireAndForgetRunner 启动 node/python/php 服务, 监听 port
        // 返回 RuntimeSession(pid, port, workDir, stopCallback)
    }
}
```

**用户工作流**:
1. 用户在 IDE 编辑 `server.js` / `app.py` / `index.php`
2. 点击 Web fragment 的"启动后端"按钮
3. 选择运行时 (Node.js / Python / PHP / 静态), 输入启动命令
4. `TermuxBackendRuntime` 通过 `TermuxCommand.run` 在 Termux HOME 启动服务
5. WebView 加载 `http://localhost:<port>` 预览
6. 关闭 fragment 时 `stopService` 终止后端进程

---

## 3. WebPreviewEngine 详细设计

### 3.1 WebContent

```kotlin
sealed class WebContent {
    data class Url(val url: String) : WebContent()                    // http(s)://, localhost:port
    data class File(val file: java.io.File) : WebContent()            // 本地 .html 文件
    data class Data(val html: String, val baseUrl: String?) : WebContent()  // inline HTML
}
```

### 3.2 WebViewState

```kotlin
class WebViewState(
    var content: WebContent,
    var deviceProfile: DeviceProfile = DevicePresets.DEFAULT,
    var zoomLevel: Float = 1.0f,
) {
    var loading: Boolean by mutableStateOf(false)
    var progress: Int by mutableIntStateOf(0)            // 0-100
    var pageTitle: String by mutableStateOf("")
    var canGoBack: Boolean by mutableStateOf(false)
    var canGoForward: Boolean by mutableStateOf(false)
    var consoleMessages: List<ConsoleMessage> by mutableStateOf(emptyList())
    var networkRequests: List<NetworkRequest> by mutableStateOf(emptyList())
    var javascriptInterface: ((String, Any?) -> Unit)? = null
    var lastError: String? by mutableStateOf(null)
    var devToolsEnabled: Boolean by mutableStateOf(true)
}
```

### 3.3 WebPreviewEngine (Composable)

```kotlin
@Composable
fun WebPreviewEngine(
    state: WebViewState,
    modifier: Modifier = Modifier,
    onUrlChange: (String) -> Unit = {},
    consoleCapture: Boolean = true,
    networkCapture: Boolean = true,
)
```

实现要点:
- `AndroidView { WebView(it) }` + `rememberSaveable` 保持状态
- `WebSettingsCompat.setForceDark(theme)` 主题适配
- `WebChromeClient.onConsoleMessage` → state.consoleMessages
- `WebViewClient.shouldInterceptRequest` → state.networkRequests
- `WebView.setWebContentsDebuggingEnabled(state.devToolsEnabled)`
- UA / viewport 通过 `WebSettings.userAgent` + `setInitialScale` + CSS viewport meta 控制

---

## 4. 设备参数库

### 4.1 DeviceProfile

```kotlin
data class DeviceProfile(
    val name: String,                    // "iPhone 14 Pro Max"
    val category: DeviceCategory,        // PHONE / TABLET / DESKTOP
    val userAgent: String,               // 完整 UA 字符串
    val viewportWidth: Int,              // CSS px
    val viewportHeight: Int,             // CSS px
    val devicePixelRatio: Float,         // 2.0 / 3.0
    val isMobile: Boolean,               // 影响 touch events
    val hasTouch: Boolean,
)
```

### 4.2 预置 11 档

| 设备 | 分类 | 视口 | DPR |
|---|---|---|---|
| iPhone SE (3rd) | PHONE | 375×667 | 2.0 |
| iPhone 14 | PHONE | 390×844 | 3.0 |
| iPhone 14 Pro Max | PHONE | 430×932 | 3.0 |
| Pixel 7 | PHONE | 412×915 | 2.625 |
| Pixel 7 Pro | PHONE | 412×915 | 3.5 |
| Galaxy S23 | PHONE | 360×780 | 3.0 |
| iPad Mini (6th) | TABLET | 744×1133 | 2.0 |
| iPad Pro 11 (4th) | TABLET | 834×1194 | 2.0 |
| Desktop 1080p | DESKTOP | 1920×1080 | 1.0 |
| Desktop 4K | DESKTOP | 3840×2160 | 1.0 |
| MacBook Air | DESKTOP | 1440×900 | 2.0 |

切换时: `WebSettings.userAgentString = profile.userAgent` + 注入 `<meta name="viewport" content="width=device-width">` + `setInitialScale` + 强制重新布局。WebView 真实重渲染并真实发起所有网络请求 (含 POST, 这是 WebView 默认行为)。

---

## 5. 后端运行时

### 5.1 BackendRuntime 接口

```kotlin
interface BackendRuntime {
    val name: String
    fun isAvailable(): Boolean
    suspend fun startService(workDir: File, command: String, port: Int): RuntimeSession
    fun stopService(session: RuntimeSession)
}

data class RuntimeSession(
    val pid: Int,
    val port: Int,
    val workDir: File,
    val runtime: String,                 // "node" / "python" / "php"
    val startedAt: Long,
    private val stopCallback: () -> Unit,
) {
    fun stop() = stopCallback()
}
```

### 5.2 TermuxBackendRuntime

```kotlin
class TermuxBackendRuntime : BackendRuntime {
    override val name = "Termux"

    override fun isAvailable(): Boolean {
        // 检查 Termux HOME 目录存在 + node/python/php 二进制可执行
        val home = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
        return home.exists()
    }

    override suspend fun startService(workDir: File, command: String, port: Int): RuntimeSession {
        // 用 FireAndForgetRunner 异步启动 (服务进程需常驻)
        // command 形如 "node server.js" / "python -m http.server 8000"
        // 返回 RuntimeSession, stopCallback 内部用 pkill 终止
    }
}
```

### 5.3 预置启动命令模板

| 运行时 | 命令模板 | 默认端口 |
|---|---|---|
| Node.js (Express) | `node server.js` | 3000 |
| Node.js (Vite dev) | `npx vite --port 5173` | 5173 |
| Node.js (webpack dev) | `npx webpack serve --port 8080` | 8080 |
| Python (http.server) | `python -m http.server 8000` | 8000 |
| Python (Flask) | `python app.py` | 5000 |
| PHP (built-in) | `php -S localhost:8000` | 8000 |
| Ruby (WEBrick) | `ruby -run -e httpd . -p 8000` | 8000 |
| Go (static) | `go run main.go` | 8080 |
| 静态文件 | 直接 WebView 加载 `file://` | - |

---

## 6. DevTools 桥接

### 6.1 DevToolsBridge

```kotlin
class DevToolsBridge(private val context: Context) {
    private var forwarder: LocalSocketForwarder? = null

    fun start(): DevToolsEndpoint {
        // 1. 找到被调试 WebView 的 CDP unix socket 名
        //    pattern: @webview_devtools_remote_<pid>
        val socketName = findDevToolsSocket() ?: return DevToolsEndpoint.Failed

        // 2. 启动 LocalSocketForwarder: abstract socket → localhost:9222
        forwarder = LocalSocketForwarder(socketName, 9222).also { it.start() }

        // 3. 查询 http://localhost:9222/json 拿到 page 列表
        val pages = queryDevToolsPages()

        return DevToolsEndpoint.Ready(wsUrl = "ws://localhost:9222/devtools/page/${pages.first().id}")
    }

    fun stop() { forwarder?.stop() }
}
```

### 6.2 LocalSocketForwarder

```kotlin
class LocalSocketForwarder(
    private val abstractSocketName: String,  // "@webview_devtools_remote_1234"
    private val tcpPort: Int,                 // 9222
) {
    fun start() {
        // 启动 ServerSocket(tcpPort)
    }
    // 每个 TCP 连接 → 建一个 LocalSocket(abstract, abstractSocketName) → 双向 pump 字节流
}
```

实现:
- `ServerSocket(tcpPort).accept()` 阻塞接收 TCP 连接
- 每个连接对应一个 `LocalSocket()` 连接到 abstract socket
- 两个线程 pump 双向字节流 (TCP→Local, Local→TCP)
- 注意: abstract socket 名以 `@` 开头 (Linux abstract namespace, 不占文件系统)

### 6.3 DevToolsFrontendLoader

```kotlin
object DevToolsFrontendLoader {
    // Chrome DevTools 前端版本 hash (用户提供的)
    private const val FRONTEND_HASH = "9c25b0453847af990895e9f681a4710784e34245"

    fun buildDevToolsUrl(wsUrl: String): String {
        return "https://chrome-devtools-frontend.appspot.com/serve_file/@${FRONTEND_HASH}/devtools_app.html" +
               "?ws=${wsUrl.removePrefix("ws://")}" +
               "&targetType=tab"
    }
}
```

加载这个 URL 到 DevTools WebView, 显示完整 Chrome DevTools UI。

---

## 7. WebPreviewFragment UI

### 7.1 布局

```
┌─────────────────────────────────────────┐
│ [← → ⟳] [地址栏磨砂] [设备▾] [🔧] [⋯]   │  ← 顶部磨砂工具栏
├─────────────────────────────────────────┤
│                                         │
│           WebView 渲染区                 │
│        (按设备视口尺寸缩放显示)           │
│                                         │
├─────────────────────────────────────────┤
│ [▶后端] [Node▾] [端口:3000] [启动]       │  ← 底部磨砂后端控制栏 (可隐藏)
└─────────────────────────────────────────┘

DevTools 面板 (从底部滑出, 半屏高度):
┌─────────────────────────────────────────┐
│ DevTools WebView (完整 Chrome DevTools)  │
│ Elements / Console / Sources / Network  │
└─────────────────────────────────────────┘
```

### 7.2 顶部工具栏 (FrostedGlass)

- 后退/前进/刷新 (FrostedIconButton)
- 地址栏 (FrostedText + 可编辑输入)
- 设备切换下拉 (FrostedGlass 弹出菜单, 11 档)
- DevTools 切换按钮 (FrostedToggleIconButton, 激活时滑出 DevTools 面板)
- 更多菜单 (缩放 / 后端 / 主题适配开关)

### 7.3 后端控制栏 (FrostedGlass, 底部可隐藏)

- 运行时选择 (Node.js / Python / PHP / Ruby / Go / 静态)
- 端口输入
- 启动/停止按钮
- 启动后地址栏自动填入 `http://localhost:<port>`

### 7.4 DevTools 面板 (可滑出)

- 半屏高度 BottomSheet 风格
- 内嵌第二个 WebView 加载 DevTools 前端 URL
- 可拖拽调整高度
- 关闭按钮 (FrostedIconButton)

### 7.5 缩放

- 双指捏合手势 (detectTransformGestures)
- 放大/缩小按钮 (FrostedIconButton, 25% 步进)
- 适配屏幕按钮 (FrostedIconButton, 重置 zoomLevel=1.0)

---

## 8. 注册与路由

### 8.1 EditorFragmentTabRegistrar

```kotlin
const val WEB_PREVIEW = "web_preview"  // order=150

private fun registerWebPreview() {
    FragmentTabRegistry.register(
      FragmentTabEntry(
        id = WEB_PREVIEW,
        title = WebPreviewFragment.TAB_TITLE,
        iconRes = R.drawable.ic_file_type_image,  // 暂复用, 后续换 web 图标
        fragmentClass = WebPreviewFragment::class.java,
        fileExtensions = WebPreviewFragment.SUPPORTED_EXTENSIONS,
        order = 150,
        fragmentFactory = { WebPreviewFragment() },
      )
    )
}
```

### 8.2 WebPreviewAction

参照 `ImagePreviewAction` 模式, id = `ide.editor.webPreview`, 匹配 `WebPreviewFragment.SUPPORTED_EXTENSIONS`。

### 8.3 FileTreeActionHandler 路由

```kotlin
private fun isSupportedWebFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    return ext.isNotEmpty() && ext in WebPreviewFragment.SUPPORTED_EXTENSIONS
}
```

### 8.4 支持的扩展名

```kotlin
val SUPPORTED_EXTENSIONS: Set<String> = setOf(
    "html", "htm",                 // 直接加载
    "vue", "jsx", "tsx",           // 检测 dist/, 提示构建
    "css", "js", "ts", "mjs",      // 源码高亮, 关联 html 加载
)
```

---

## 9. 依赖新增

### 9.1 gradle/libs.versions.toml

```toml
[versions]
androidx-webkit = "1.17.0-alpha03"

[libraries]
androidx-webkit = { module = "androidx.webkit:webkit", version.ref = "androidx-webkit" }
```

### 9.2 modules/web-preview/build.gradle.kts

```kotlin
dependencies {
    implementation(libs.androidx.webkit)
    implementation(projects.termux.shell)       // BackendRuntime
    implementation(projects.termux.shared)      // TermuxConstants
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
}
```

### 9.3 core/app/build.gradle.kts

```kotlin
implementation(projects.modules.web.preview)   // 或 include 的路径
```

### 9.4 settings.gradle.kts

```kotlin
include(":modules:web-preview")
```

APK 体积影响: `androidx.webkit` 是 compat 库 (~200KB), 不打包 WebView 实现 (用系统 WebView), 满足轻量化。

---

## 10. 字符串资源

```xml
<string name="title_web_preview">Web Preview</string>
<string name="desc_web_preview">Preview HTML/Vue/React with built-in Chrome DevTools and backend runtime</string>
<string name="web_preview_device">Device</string>
<string name="web_preview_devtools">DevTools</string>
<string name="web_preview_backend">Backend</string>
<string name="web_preview_start_backend">Start backend</string>
<string name="web_preview_stop_backend">Stop backend</string>
<string name="web_preview_zoom_in">Zoom in</string>
<string name="web_preview_zoom_out">Zoom out</string>
<string name="web_preview_zoom_reset">Reset zoom</string>
```

---

## 11. 实现顺序

1. **阶段 1**: 创建 `modules/web-preview/` library 模块 + settings.gradle + build.gradle + androidx.webkit 依赖
2. **阶段 2**: `WebPreviewEngine` (Compose WebView 封装, WebContent, WebViewState)
3. **阶段 3**: `DeviceProfile` + `DevicePresets` (11 档设备参数库)
4. **阶段 4**: `BackendRuntime` 接口 + `TermuxBackendRuntime` 实现
5. **阶段 5**: `DevToolsBridge` + `LocalSocketForwarder` + `DevToolsFrontendLoader`
6. **阶段 6**: `WebPreviewFragment` (UI 组装: FrostedGlass 工具栏 + WebView + DevTools 面板)
7. **阶段 7**: 注册与路由 (EditorFragmentTabRegistrar + WebPreviewAction + FileTreeActionHandler)
8. **阶段 8**: strings.xml + commit

---

## 12. 测试策略

- 编译验证: `:core:app:compileDebugKotlin` (沙箱环境受 Gradle Plugin Portal CDN 限制, 可能无法完整编译)
- 手动验证点:
  - 加载本地 `index.html` 显示正常
  - 设备切换后 UA + 视口真实变化
  - console.log 在 DevTools 面板可见
  - 后端 Node.js 启动后 `localhost:3000` 可访问
  - DevTools 完整 UI (Elements/Console/Network) 可用

---

## 13. 风险与缓解

| 风险 | 缓解 |
|---|---|
| LocalSocket 桥接在部分设备失败 (SELinux) | 降级提示用户用电脑 Chrome `chrome://inspect` |
| `chrome-devtools-frontend.appspot.com` 需要网络 | 提示用户联网; 未来可 bundling 本地 DevTools 前端 |
| Termux 未安装 Node.js 等运行时 | `isAvailable()` 检测 + 提示用户 `pkg install nodejs` |
| Vue/React 源码不能直接运行 | 检测 `dist/` 目录, 无则提示用户构建 |
| abstract socket 名因 PID 变化 | `findDevToolsSocket()` 动态扫描 `/proc/net/unix` |

---

## 14. 未来扩展

- `ProotBackendRuntime`: 集成 proot Linux 容器, 支持完整 Linux 生态
- 本地 DevTools 前端 bundling: 离线可用 DevTools
- 多 WebView tab: 同时预览多个页面
- 录屏/截图: WebView 内容导出
- 性能 profiler: 集成 CDP Performance domain
