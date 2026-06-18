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

package com.itsaky.androidide.compose.preview.runtime

import android.content.Context
import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.compose.preview.PreviewConfig
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Compose 预览渲染引擎 v3.
 *
 * ## 关键设计 (取代 v2 [ComposableRenderer])
 *
 * 1. **ComposeView 由 Activity 直接持有, 不嵌入 Composable 子树**:
 *    v2 方案在 [androidx.compose.ui.viewinterop.AndroidView] 中嵌套 [ComposeView],
 *    会因为 `AndroidView` 的测量/布局与 Compose 的 SubcomposeLayout 冲突, 导致
 *    实际渲染区域为零, 出现"白屏 / BUILD SUCCESSFUL 后不显示". v3 直接把
 *    [ComposeView] add 到 Activity 的根 FrameLayout, 跟 IDE 编辑器侧的预览实现一致.
 *
 * 2. **ClassLoader 一次性加载, 不可变**:
 *    v2 的 [ComposeClassLoader.setProjectDexFiles] / [setRuntimeDex] 是 "可变容器",
 *    在 `LaunchedEffect` 中调用, 与 setContent 存在时序竞争. v3 改用 [DexRuntime] 一次性
 *    把 preview dex + project dex + runtime dex 全部装入, 之后 [ComposableInvoker] 任意
 *    `loadClass` 都能命中.
 *
 * 3. **错误显式上抛**:
 *    任何步骤失败 (loadClass null / 函数未找到 / invoke 抛错) 都会渲染到 ComposeView 上,
 *    而非像 v2 那样只 LOG.warn 后展示一个空白 Box.
 *
 * 4. **可重入**:
 *    [render] 可多次调用, 内部用 [AtomicReference] 保证同一时间只有一个生效, 旧的
 *    [DexRuntime] 会在新一次 render 开始前释放.
 */
class PreviewRenderEngine(
    private val context: Context,
    /**
     * 外部可见 (internal), 让 [com.itsaky.androidide.compose.preview.ComposePreviewActivity.attachPreviewContainer]
     * 能判断 "container 是否改变" 来决定是否 detach + 重建引擎. v3.2 修复切
     * deviceSim / profile 时显示黑屏的 bug.
     */
    internal val container: ViewGroup,
) {

    private val LOG = LoggerFactory.getLogger(PreviewRenderEngine::class.java)

    private val invoker = ComposableInvoker()
    private val activeRuntime = AtomicReference<DexRuntime?>(null)
    @Volatile
    private var composeView: ComposeView? = null

    /**
     * 把 [ComposeView] 安装到 [container] 中, 后续 [render] 的内容都会写到这个 view.
     * 必须在 Activity.onCreate 之后 (主线程) 调用一次.
     *
     * 重复调用安全, 内部会先 detach 旧的.
     */
    fun attach() {
        if (composeView != null) return
        // 【v3.2】如果 container 已经有别的 engine 添加的 ComposeView, 先清理.
        // 切 deviceSim / profile 时旧 engine 已经被 Activity.detach() 释放, 但
        // 旧 ComposeView 可能仍残留 (Activity 是 main thread, GC 还没跑).
        repeat(container.childCount) { i ->
            val child = container.getChildAt(i)
            if (child is ComposeView) {
                container.removeViewAt(i)
                LOG.debug("Removed orphan ComposeView from container before attach")
                return@repeat
            }
        }
        val view = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // Activity 销毁时释放 composition
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
        }
        container.addView(view)
        composeView = view
        LOG.info("PreviewRenderEngine attached to container (id={})", container.id)
    }

    /**
     * 释放所有资源: 清空 DexRuntime, 把 ComposeView 从 container 移除.
     * Activity.onDestroy 时调用.
     */
    fun detach() {
        activeRuntime.getAndSet(null)?.release()
        composeView?.let { container.removeView(it) }
        composeView = null
        LOG.info("PreviewRenderEngine detached")
    }

    /**
     * 加载 dex 并渲染指定 Composable.
     *
     * @param previewDex    项目 dex 中包含用户 Composable 的那个 (通常是 mergeProjectDex*
     *                      或 project_dex_archive 中的第一个). **v3.1 不再是 K2 编译产物**,
     *                      而是 gradle assemble 的直接产物.
     * @param projectDex    项目运行时 dex 集合 (含 previewDex + 其它 merge / archive dex).
     *                      **v3.1 不再有 runtimeDex 参数** — compose runtime 类由 IDE 主
     *                      APK 的 PathClassLoader 解析.
     * @param className     用户 Composable 所在类 (含 package).
     * @param functionName  Composable 函数名.
     * @param args          透传给 Composable 的 user 参数.
     * @param previewConfig v3.4 新增 — `@Preview` 标注的完整配置 (背景色 / showBackground /
     *                      uiMode / showSystemUi). 为 null 时使用默认 (无背景色 / 浅色主题).
     * @param orientation   v3.5 新增 — 设备方向. 通过 [android.content.res.Configuration]
     *                      的 ORIENTATION_PORTRAIT / ORIENTATION_LANDSCAPE 字段传给
     *                      Compose 的 [androidx.compose.ui.platform.LocalConfiguration],
     *                      让 [androidx.compose.foundation.layout.BoxWithConstraints] /
     *                      `Modifier.aspectRatio` / `LocalConfiguration.current.orientation`
     *                      等能正确响应方向. 真机模式: preview 内容按 orientation 重新布局.
     */
    fun render(
        previewDex: File?,
        projectDex: List<File>,
        className: String,
        functionName: String,
        args: Array<out Any?> = emptyArray(),
        previewConfig: PreviewConfig? = null,
        orientation: com.itsaky.androidide.compose.preview.ui.DeviceOrientation =
            com.itsaky.androidide.compose.preview.ui.DeviceOrientation.PORTRAIT,
    ) {
        val view = composeView ?: run {
            LOG.error("render() called before attach()")
            return
        }

        // 1) 一次性加载所有 dex. v3.1 永远不传 runtimeDex; 内部合并到同一个 dex 列表.
        val allDex = buildList {
            if (previewDex != null && previewDex.exists() && previewDex.length() > 0) {
                add(previewDex)
            }
            projectDex.filter { it.exists() && it.length() > 0 }.forEach { add(it) }
        }.distinctBy { it.absolutePath }

        val runtime = DexRuntime.loadAll(
            context = context,
            dexFiles = allDex,
        )

        // 替换旧 runtime
        activeRuntime.getAndSet(runtime)?.release()

        // 2) loadClass
        val clazz = runtime.loadClass(className)
        if (clazz == null) {
            showError(view, "Class not found: $className\n\nDex sources:\n" +
                runtime.dexSources().joinToString("\n") { "  $it" })
            return
        }
        LOG.info("Loaded class: {} (loader={})", clazz.name, clazz.classLoader?.javaClass?.name)

        // 3) 实例化 (仅当函数非静态时)
        val instance: Any? = if (clazz.declaredMethods.any { !java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name == functionName }) {
            try {
                clazz.getDeclaredConstructor().newInstance()
            } catch (e: Throwable) {
                LOG.error("Failed to instantiate {} (non-static composable)", className, e)
                showError(view, "Cannot instantiate ${clazz.simpleName}: ${e.message ?: e::class.java.simpleName}")
                return
            }
        } else {
            null
        }

        // 4) setContent + 通过 currentComposer 注入 (v3.4: 应用 PreviewConfig; v3.5: 应用 orientation)
        view.setContent {
            // 【v3.5】用 LocalConfiguration 注入 orientation. Compose 在
            // BoxWithConstraints / Modifier.aspectRatio / LocalConfiguration.current.orientation
            // 处能正确响应, preview 内容按 orientation 重新布局.
            val configuration = LocalConfiguration.current
            val orientedConfiguration = remember(configuration, orientation) {
                val updated = android.content.res.Configuration(configuration)
                updated.orientation = if (orientation.isLandscape) {
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                } else {
                    android.content.res.Configuration.ORIENTATION_PORTRAIT
                }
                updated
            }
            CompositionLocalProvider(
                LocalConfiguration provides orientedConfiguration,
            ) {
                PreviewConfigTheme(previewConfig) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        RenderComposable(invoker, clazz, instance, functionName, args)
                    }
                }
            }
        }
        LOG.info("Rendered composable: {}#{} (orientation={})", className, functionName, orientation)
    }

    /**
     * 显示错误占位 (Compose 渲染, 走 Surface 包好与 Preview 风格一致).
     */
    private fun showError(view: ComposeView, message: String) {
        LOG.warn("Rendering error UI: {}", message)
        view.setContent {
            MaterialTheme {
                ErrorContent(message)
            }
        }
    }

    @Composable
    private fun RenderComposable(
        invoker: ComposableInvoker,
        clazz: Class<*>,
        instance: Any?,
        functionName: String,
        args: Array<out Any?>,
    ) {
        val composer: Any = runCatching { currentComposer }.getOrNull() ?: run {
            ErrorContent("currentComposer is null - not in a Composable scope?")
            return
        }
        val result = invoker.invoke(
            clazz = clazz,
            functionName = functionName,
            composer = composer,
            instance = instance,
            args = args,
        )
        if (!result.ok) {
            ErrorContent(result.errorMessage ?: "Unknown invoke failure")
        }
    }

    @Composable
    private fun ErrorContent(message: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF3F3))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Preview Error",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFB00020),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * v3.4 新增: 应用 [PreviewConfig] 的 uiMode (浅色/深色) + showBackground 背景.
 *
 * 行为:
 * - [PreviewConfig.uiMode] = `UI_MODE_NIGHT_YES` → 强制 darkColorScheme
 * - [PreviewConfig.uiMode] = `UI_MODE_NIGHT_NO`  → 强制 lightColorScheme
 * - [PreviewConfig.uiMode] = null                → 跟随系统 (isSystemInDarkTheme)
 * - [PreviewConfig.showBackground] = true       → 整个预览包一层 [Color] 背景
 * - [PreviewConfig.backgroundColor] 提供具体色值, 缺省 `0xFFFFFFFF` (白)
 */
@Composable
private fun PreviewConfigTheme(
    config: PreviewConfig?,
    content: @Composable () -> Unit,
) {
    val isDark = when {
        config?.uiMode == null -> isSystemInDarkTheme()
        config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES -> true
        else -> false
    }
    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        if (config?.showBackground == true) {
            val bg = config.backgroundColor?.let { Color(it) } ?: Color(0xFFFFFFFF)
            Box(modifier = Modifier.fillMaxSize().background(bg)) {
                content()
            }
        } else {
            content()
        }
    }
}
