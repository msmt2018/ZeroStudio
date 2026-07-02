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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
import com.itsaky.androidide.compose.preview.ui.DeviceOrientation
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference

/**
 * Compose 预览渲染引擎 v4.
 *
 * ## v4 重构要点
 *
 * v3 时期 [PreviewRenderEngine] 出现过几类"修一个 bug 引出两个 bug" 的连锁问题:
 *
 *  1. **作用域污染** — 私有 `applyContent` 误用了 `render` 的局部变量 (`previewDex` /
 *     `projectDex` / `fromReplay` / ...). 这些变量在 `applyContent` 不可见, 一旦
 *     brace 调整, 就会集中爆出 "Unresolved reference / private not applicable to
 *     local function" 一连串错. v4 改用 [RenderRequest] 显式传递, 杜绝这种"靠外层
 *     闭包变量"的隐式依赖.
 *
 *  2. **首屏空白 / 状态切换后黑屏** — v3 用 `lastRender` 在 `attach` 时 replay,
 *     但若 `render(...)` 在 `attach` 之前就调用了 (Compose composition 与 view
 *     状态 collect 时序竞争), 引擎直接 LOG.error 提前返回, 用户看到的是空容器.
 *     v4 引入 [pendingRequest]: `render` 早于 `attach` 时把请求缓存, `attach` 一
 *     完成就消费. 即便第一次 `previewState.collect` 在 `attach` 之前发射 Ready
 *     也只是把请求入队, 不会丢失.
 *
 *  3. **方法职责混乱** — v3 `render` 同时管 dex 加载、反射实例化、setContent、
 *     保存快照、写日志; 一行调用触发 4~5 步副作用, 测试 / 单步调试都痛苦. v4 把
 *     步骤拆成 [loadRuntime] / [loadClassOrError] / [instantiateIfNeeded] /
 *     [writeContent] 四个无副作用的 helper, 主流程 [render] 只做编排.
 *
 *  4. **快照与请求混用** — v3 [LastRender] 既要做 replay 快照, 又要塞 `previewDex` /
 *     `projectDex`. 切 deviceSim 时, `previewDex` / `projectDex` 多数情况下没变,
 *     但 `LastRender` 把它们强加进来, 反而容易在 replay 路径上误用. v4 拆成
 *     [LastSnapshot] (只存"重放"必需的 className / functionName / args /
 *     previewConfig / orientation, 不带 dex) 和 [RenderRequest] (含 dex).
 *     Replay 路径直接用现有的 activeRuntime 重新 loadClass, 不需要 dex 信息.
 *
 * ## 用法 (ComposePreviewActivity 调用)
 *
 * ```
 * val engine = PreviewRenderEngine(this, container)
 * engine.attach()                 // 主线程, 一次性
 * engine.render(RenderRequest(...))
 * engine.refresh()                // 用最近一次的请求重渲染 (用于 rebuild 后)
 * engine.detach()                 // Activity.onDestroy
 * ```
 *
 * @author android_zero
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
     * 上次成功 [render] 调用的 className / functionName / args / previewConfig /
     * orientation 快照. 不带 dex (dex 在 [activeRuntime] 里, 生命周期跟随引擎).
     *
     * 当引擎 [attach] 到一个新的 [ComposeView] (典型场景: 切 deviceSim / profile
     * 触发 [ComposePreviewActivity] 重建引擎), 自动用这份快照重新调用一次
     * [renderFromSnapshot], 让用户看到 "重新进入 preview 也能立刻显示上次渲染的
     * 内容", 而不是黑屏.
     */
    @Volatile
    private var lastSnapshot: LastSnapshot? = null

    /**
     * [render] 在 [attach] 之前就被调用时, 请求先存到这里, [attach] 完成后立刻消费.
     * 解决 v3 "首屏空白" 的 race condition.
     */
    @Volatile
    private var pendingRequest: RenderRequest? = null

    // ---------------------------------------------------------------------
    //  生命周期
    // ---------------------------------------------------------------------

    /**
     * 把 [ComposeView] 安装到 [container] 中, 后续 [render] 的内容都会写到这个 view.
     * 必须在主线程调用一次. 重复调用安全 (幂等).
     */
    fun attach() {
        if (composeView != null) return
        cleanupContainer()
        val view = newComposeView()
        container.addView(view)
        composeView = view
        LOG.info("PreviewRenderEngine attached to container (id={})", container.id)

        // 优先消费 pendingRequest, 再回放 lastSnapshot. 两者不会同时存在:
        // pendingRequest 是 render 早于 attach 的产物, lastSnapshot 是上一次成功
        // render 留下的快照. 一次 attach 只可能命中其中一个.
        val pending = pendingRequest
        if (pending != null) {
            pendingRequest = null
            LOG.info("Draining pending render on attach: {}#{}", pending.className, pending.functionName)
            doRender(pending)
        } else {
            lastSnapshot?.let { snap ->
                LOG.info("Replaying last snapshot on attach: {}#{}", snap.className, snap.functionName)
                renderFromSnapshot(snap)
            }
        }
    }

    /**
     * 释放 [DexRuntime] 并从 [container] 移除 [ComposeView]. 主线程调用.
     *
     * 注意: 不清空 [lastSnapshot] — 重建引擎后能凭它自动 replay. 调用方如果想
     * 真正丢弃历史 (例如导航离开 preview 页面), 应额外调 [clearHistory].
     */
    fun detach() {
        activeRuntime.getAndSet(null)?.release()
        composeView?.let { container.removeView(it) }
        composeView = null
    }

    /**
     * 清除 [lastSnapshot] 与 [pendingRequest]. 用户从 preview 页面离开、确认不再
     * 需要 "重新进入自动 replay" 时由 [ComposePreviewActivity] 调用.
     */
    fun clearHistory() {
        lastSnapshot = null
        pendingRequest = null
    }

    /**
     * 把当前引擎持有的 [LastSnapshot] 拿出来, 用于在 [ComposePreviewActivity] 重建
     * 引擎时把历史传过去, 避免 "切 deviceSim / profile 后黑屏".
     *
     * 配套 [preloadSnapshot] 使用. 取出后本引擎的 [lastSnapshot] 仍保留 (read-only
     * 视图), 重建后的新引擎调用 [preloadSnapshot] 注入即可.
     */
    fun snapshotForTransfer(): LastSnapshot? = lastSnapshot

    /**
     * 把 [snapshot] 注入到本引擎, 让 [attach] 时能自动 replay. 用于容器变化触发
     * 引擎重建场景. 调用前引擎不能已经成功 render 过 (否则会和现有 [lastSnapshot]
     * 冲突), 由调用方保证时序.
     */
    fun preloadSnapshot(snapshot: LastSnapshot?) {
        if (lastSnapshot == null) {
            lastSnapshot = snapshot
        }
    }

    /**
     * 当前 attach 的 [ComposeView]. 供截图 / 视图导出等场景使用. 未 attach 返回 null.
     */
    fun currentComposeView(): ComposeView? = composeView

    // ---------------------------------------------------------------------
    //  渲染入口
    // ---------------------------------------------------------------------

    /**
     * 加载 dex 并渲染指定 Composable. 线程安全 (主线程调用).
     *
     * @param request 渲染请求, 包含 dex / className / functionName / args /
     *                previewConfig / orientation. 见 [RenderRequest].
     */
    fun render(request: RenderRequest) {
        val view = composeView
        if (view == null) {
            // attach 还没调用, 排队等 attach 后消费. 解决 v3 首屏空白的 race.
            LOG.info("render() queued before attach(): {}#{}", request.className, request.functionName)
            pendingRequest = request
            return
        }
        doRender(request)
    }

    /**
     * 用最近一次成功 render 的 [LastSnapshot] 重新渲染. 用于 gradle rebuild 完成
     * 后不重新传 dex (dex 已存在) 但希望立即看到最新代码的场景.
     */
    fun refresh() {
        val view = composeView ?: run {
            LOG.warn("refresh() called before attach()")
            return
        }
        val snap = lastSnapshot ?: run {
            LOG.warn("refresh() called but no last snapshot")
            return
        }
        renderFromSnapshot(snap)
    }

    // ---------------------------------------------------------------------
    //  内部步骤 (无副作用 / 单一职责, 便于单测)
    // ---------------------------------------------------------------------

    private fun doRender(request: RenderRequest) {
        val view = composeView ?: return  // detached during render, bail

        val runtime = loadRuntime(request)
        activeRuntime.getAndSet(runtime)?.release()

        val clazz = loadClassOrError(runtime, request.className, view) ?: return

        val instance = if (functionNeedsInstance(clazz, request.functionName)) {
            instantiateOrNull(clazz, view, request.className) ?: return
        } else null

        writeContent(view, request, clazz, instance)
        lastSnapshot = LastSnapshot.from(request)
        LOG.info("Rendered composable: {}#{} (orientation={})",
            request.className, request.functionName, request.orientation)
    }

    private fun renderFromSnapshot(snap: LastSnapshot) {
        val view = composeView ?: return
        val runtime = activeRuntime.get() ?: run {
            showError(view, "Replay failed: no active DexRuntime (was the engine detached?)")
            return
        }
        val clazz = loadClassOrError(runtime, snap.className, view) ?: return
        val instance = if (functionNeedsInstance(clazz, snap.functionName)) {
            instantiateOrNull(clazz, view, snap.className)
        } else null
        // Replay 路径不写回 lastSnapshot, 也不改 pendingRequest — 它们已经是
        // 这次 replay 的来源, 写回会造成无意义的覆盖.
        writeContent(view, snap.toRequest(), clazz, instance)
        LOG.info("Replayed composable: {}#{}", snap.className, snap.functionName)
    }

    /** 加载并合并所有 dex 到一个新的 [DexRuntime]. */
    private fun loadRuntime(request: RenderRequest): DexRuntime {
        val allDex = buildList {
            val pd = request.previewDex
            if (pd != null && pd.exists() && pd.length() > 0) add(pd)
            request.projectDex.filter { it.exists() && it.length() > 0 }.forEach { add(it) }
        }.distinctBy { it.absolutePath }
        return DexRuntime.loadAll(context, allDex)
    }

    /** 通过 [DexRuntime.loadClass] 拿类, 拿不到就在 [view] 上画错误并返回 null. */
    private fun loadClassOrError(runtime: DexRuntime, className: String, view: ComposeView): Class<*>? {
        val clazz = runtime.loadClass(className)
        if (clazz == null) {
            showError(view, "Class not found: $className\n\nDex sources:\n" +
                runtime.dexSources().joinToString("\n") { "  $it" })
            return null
        }
        LOG.info("Loaded class: {} (loader={})", clazz.name, clazz.classLoader?.javaClass?.name)
        return clazz
    }

    /** 判断给定方法是否需要先 newInstance 才能调用. */
    private fun functionNeedsInstance(clazz: Class<*>, functionName: String): Boolean =
        clazz.declaredMethods.any {
            !Modifier.isStatic(it.modifiers) && it.name == functionName
        }

    /** 通过无参构造实例化, 失败则在 [view] 上画错误并返回 null. */
    private fun instantiateOrNull(clazz: Class<*>, view: ComposeView, className: String): Any? = try {
        clazz.getDeclaredConstructor().newInstance()
    } catch (e: Throwable) {
        LOG.error("Failed to instantiate {} (non-static composable)", className, e)
        showError(view, "Cannot instantiate ${clazz.simpleName}: ${e.message ?: e::class.java.simpleName}")
        null
    }

    /**
     * 把 [request] + [clazz] + [instance] 写到 [view] 的 setContent. 这是唯一会
     * 触发 Compose 重组的步骤, 其它步骤 (loadRuntime / loadClass / instantiate)
     * 失败都直接 return, 不会污染 view.
     */
    private fun writeContent(
        view: ComposeView,
        request: RenderRequest,
        clazz: Class<*>,
        instance: Any?,
    ) {
        view.setContent {
            PreviewScreen(invoker, clazz, instance, request)
        }
    }

    // ---------------------------------------------------------------------
    //  错误显示
    // ---------------------------------------------------------------------

    /**
     * 显示错误占位. Compose 渲染, 走 Surface 包好与 Preview 风格一致.
     */
    private fun showError(view: ComposeView, message: String) {
        LOG.warn("Rendering error UI: {}", message)
        view.setContent {
            MaterialTheme {
                ErrorScreen(message)
            }
        }
    }

    // ---------------------------------------------------------------------
    //  View 创建
    // ---------------------------------------------------------------------

    private fun newComposeView(): ComposeView = ComposeView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        // Activity 销毁时释放 composition
        setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
    }

    /**
     * 清掉 [container] 里残留的 [ComposeView]. 切 deviceSim / profile 时旧引擎
     * 已经 [detach], 但 view 还在 container 里, 阻碍 GC. 用 downTo 0 反向迭代,
     * 避免 removeViewAt(i) 后索引位移导致跳过下一个 view.
     */
    private fun cleanupContainer() {
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            if (child is ComposeView) {
                container.removeViewAt(i)
                LOG.debug("Removed orphan ComposeView from container before attach")
            }
        }
    }
}

// ---------------------------------------------------------------------
//  数据类
// ---------------------------------------------------------------------

/**
 * 一次 [PreviewRenderEngine.render] 调用的全部输入.
 *
 * 故意做成不可变 + 普通 class (非 data class): args 是 `Array<out Any?>`, data class
 * 自动生成的 equals 会调 `Arrays.equals` 做内容比较, 但 preview args 通常是动态
 * 构造的, 内容相等的两个数组在 hash/equals 上仍会跑完全部元素比较, 性能没必要. 这里
 * 只做"参数打包", 不用作 hash key.
 */
class RenderRequest(
    val previewDex: File?,
    val projectDex: List<File>,
    val className: String,
    val functionName: String,
    val args: Array<out Any?> = emptyArray(),
    val previewConfig: PreviewConfig? = null,
    val orientation: DeviceOrientation = DeviceOrientation.PORTRAIT,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RenderRequest) return false
        return previewDex == other.previewDex &&
            projectDex == other.projectDex &&
            className == other.className &&
            functionName == other.functionName &&
            args.contentEquals(other.args) &&
            previewConfig == other.previewConfig &&
            orientation == other.orientation
    }

    override fun hashCode(): Int {
        var result = previewDex?.hashCode() ?: 0
        result = 31 * result + projectDex.hashCode()
        result = 31 * result + className.hashCode()
        result = 31 * result + functionName.hashCode()
        result = 31 * result + args.contentHashCode()
        result = 31 * result + (previewConfig?.hashCode() ?: 0)
        result = 31 * result + orientation.hashCode()
        return result
    }
}

/**
 * v4 新增: [PreviewRenderEngine] 用于在 [PreviewRenderEngine.attach] 时自动
 * replay 的快照. 故意只存"重放"必需的字段, 不带 dex — dex 在 [DexRuntime] 里,
 * 重放时直接拿 [activeRuntime] 重新 loadClass, 不需要重新传 dex.
 */
internal class LastSnapshot(
    val className: String,
    val functionName: String,
    val args: Array<out Any?>,
    val previewConfig: PreviewConfig?,
    val orientation: DeviceOrientation,
) {
    /** 拼回一个 [RenderRequest]. dex 字段由调用方决定 (replay 路径不需要). */
    fun toRequest(previewDex: File? = null, projectDex: List<File> = emptyList()): RenderRequest =
        RenderRequest(
            previewDex = previewDex,
            projectDex = projectDex,
            className = className,
            functionName = functionName,
            args = args,
            previewConfig = previewConfig,
            orientation = orientation,
        )

    companion object {
        fun from(request: RenderRequest): LastSnapshot = LastSnapshot(
            className = request.className,
            functionName = request.functionName,
            args = request.args.copyOf(),
            previewConfig = request.previewConfig,
            orientation = request.orientation,
        )
    }
}

// ---------------------------------------------------------------------
//  Compose 树 (顶 @Composable, 独立于 PreviewRenderEngine, 便于复用)
// ---------------------------------------------------------------------

/**
 * 实际渲染的 Compose 根节点. 拆成顶层 @Composable 是为了:
 *  - 不在 [PreviewRenderEngine] 内嵌 @Composable 树, 降低阅读耦合
 *  - 写日志 / 调试时只看 PreviewScreen 就能定位渲染逻辑
 *
 * 包了 [CompositionLocalProvider] (orientation) + [PreviewConfigTheme] (uiMode +
 * showBackground) + [Surface] (背景色), 最后委托给 [RenderComposableScreen].
 */
@Composable
private fun PreviewScreen(
    invoker: ComposableInvoker,
    clazz: Class<*>,
    instance: Any?,
    request: RenderRequest,
) {
    val configuration = LocalConfiguration.current
    val orientedConfiguration = remember(configuration, request.orientation) {
        Configuration(configuration).apply {
            orientation = if (request.orientation.isLandscape) {
                Configuration.ORIENTATION_LANDSCAPE
            } else {
                Configuration.ORIENTATION_PORTRAIT
            }
        }
    }
    CompositionLocalProvider(
        LocalConfiguration provides orientedConfiguration,
    ) {
        PreviewConfigTheme(request.previewConfig) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                RenderComposableScreen(invoker, clazz, instance, request.functionName, request.args)
            }
        }
    }
}

@Composable
private fun RenderComposableScreen(
    invoker: ComposableInvoker,
    clazz: Class<*>,
    instance: Any?,
    functionName: String,
    args: Array<out Any?>,
) {
    val composer: Any = runCatching { currentComposer }.getOrNull() ?: run {
        ErrorScreen("currentComposer is null - not in a Composable scope?")
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
        ErrorScreen(result.errorMessage ?: "Unknown invoke failure")
    }
}

@Composable
internal fun ErrorScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF3F3))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
fun PreviewConfigTheme(
    config: PreviewConfig?,
    content: @Composable () -> Unit,
) {
    val dark = when (config?.uiMode) {
        android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
        android.content.res.Configuration.UI_MODE_NIGHT_NO -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors) {
        if (config?.showBackground == true) {
            val bg = config.backgroundColor.takeIf { it != 0 } ?: 0xFFFFFFFF
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(bg.toLong()))
            ) {
                content()
            }
        } else {
            content()
        }
    }
}
