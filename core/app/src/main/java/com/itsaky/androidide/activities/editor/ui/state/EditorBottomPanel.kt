package com.itsaky.androidide.activities.editor.ui.state

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import com.itsaky.androidide.R
import com.itsaky.androidide.adapters.DiagnosticsAdapter
import com.itsaky.androidide.adapters.SearchListAdapter
import com.itsaky.androidide.debugger.fragment.*
import com.itsaky.androidide.editor.logs.ComposeLogBuffer
import com.itsaky.androidide.editor.logs.LogConsoleScreen
import com.itsaky.androidide.fragments.DiagnosticsListFragment
import com.itsaky.androidide.fragments.SearchResultFragment
import com.itsaky.androidide.fragments.toolbox.EditorToolboxFragment
import com.itsaky.androidide.models.LogLine
import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory

/**
 * 彻底优化后的 EditorBottomPanel：三大高频日志彻底由 Compose 纯粹驱动。
 */
class EditorBottomPanel(context: Context) {
    val pages = EditorPageRegistry()
    var visible by mutableStateOf(false)
    var status by mutableStateOf("")
        private set
    var actionText by mutableStateOf("")
        private set
    var actionProgress by mutableFloatStateOf(0f)
        private set
    var headerPage by mutableIntStateOf(0)
        private set

    // 三大专属环形 Compose 日志池
    val buildLogBuffer = ComposeLogBuffer(maxLines = 4000)
    val appLogBuffer = ComposeLogBuffer(maxLines = 4000)
    val ideLogBuffer = ComposeLogBuffer(maxLines = 4000)

    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private val appSession = EditorAppLogSession(context, ::appendApkLog)
    private val appender = com.itsaky.androidide.logging.LifecycleAwareAppender()
    private var owner: androidx.lifecycle.LifecycleOwner? = null

    // 构建日志后台批处理合并通道
    private val buildBatch = ArrayList<String>(128)
    private var isBuildFlushScheduled = false

    init {
        // 1. 三大高吞吐日志：全部注册为纯粹声明式的 ScreenPage
        pages.register(
            EditorPage.ScreenPage(
                id = "page_build_output",
                title = context.getString(R.string.build_output),
            ) {
                LogConsoleScreen(buffer = buildLogBuffer, title = "Build Output")
            }
        )

        pages.register(
            EditorPage.ScreenPage(
                id = "page_app_logs",
                title = context.getString(R.string.app_logs),
            ) {
                LogConsoleScreen(buffer = appLogBuffer, title = "App Logcat")
            }
        )

        pages.register(
            EditorPage.ScreenPage(
                id = "page_ide_logs",
                title = context.getString(R.string.ide_logs),
            ) {
                LogConsoleScreen(buffer = ideLogBuffer, title = "IDE Internal Log")
            }
        )

        // 2. 其它低频工具保持原有 FragmentPage 规范
        listOf(
            R.string.view_diags to DiagnosticsListFragment::class.java,
            R.string.view_search_results to SearchResultFragment::class.java,
            R.string.title_editor_toolbox to EditorToolboxFragment::class.java,
            R.string.editor_tab_breakpoints to BreakpointListFragment::class.java,
            R.string.editor_tab_variables to VariablesFragment::class.java,
            R.string.editor_tab_callstack to CallStackFragment::class.java,
            R.string.editor_tab_watches to WatchesFragment::class.java,
            R.string.editor_tab_logpoint to LogpointFragment::class.java,
        ).forEach { (title, type) ->
            pages.register(EditorPage.FragmentPage(type.name, context.getString(title), type))
        }
    }

    fun start(owner: androidx.lifecycle.LifecycleOwner) {
        this.owner = owner
        appSession.start()

        appender.consumer = { line ->
            if (!ideLogBuffer.isSourcePaused) {
                main.post { ideLogBuffer.appendLine(line) }
            }
        }
        appender.attachTo(owner)
        appender.context = LoggerFactory.getILoggerFactory() as LoggerContext
        appender.start()
        (LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger).addAppender(appender)
    }

    fun close() {
        closed = true
        appSession.close()
        owner?.let(appender::detachFrom)
        appender.stop()
        (LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger).detachAppender(appender)
        main.removeCallbacksAndMessages(null)
    }

    fun appendBuildOut(text: String?) {
        if (text == null || buildLogBuffer.isSourcePaused) return
        val line = if (text.endsWith('\n')) text else "$text\n"

        synchronized(buildBatch) {
            buildBatch.add(line)
            if (!isBuildFlushScheduled) {
                isBuildFlushScheduled = true
                main.postDelayed({ flushBuildBatch() }, 50) // 50ms 批量合并推往 Compose
            }
        }
    }

    private fun flushBuildBatch() {
        val linesToFlush: List<String>
        synchronized(buildBatch) {
            if (closed) return
            linesToFlush = ArrayList(buildBatch)
            buildBatch.clear()
            isBuildFlushScheduled = false
        }
        buildLogBuffer.appendBatch(linesToFlush)
    }

    fun clearBuildOutput() {
        synchronized(buildBatch) { buildBatch.clear() }
        buildLogBuffer.clear()
    }

    fun appendApkLog(line: LogLine) {
        if (appLogBuffer.isSourcePaused) {
            line.recycle()
            return
        }
        val text = line.toString()
        line.recycle()
        main.post { appLogBuffer.appendLine(text) }
    }

    fun forceCollapse() { visible = false }
    fun tryExpandSheetFromControl(): Boolean { visible = true; return true }
    fun selectTabByFragmentClass(type: Class<*>): Boolean = pages.select(type.name)
    fun setActionText(text: CharSequence) { actionText = text.toString() }
    fun setActionProgress(progress: Int) { actionProgress = progress.coerceIn(0, 100) / 100f }
    fun showChild(index: Int) { headerPage = index }
    fun setStatus(text: CharSequence, gravity: Int = 0) { status = text.toString() }

    // 提供兼容数据导出接口
    fun selectedOutputSnapshot(): Pair<String, String>? = when (pages.selectedId) {
        "page_build_output" -> "build_output" to buildLogBuffer.dump()
        "page_app_logs" -> "app_logs" to appLogBuffer.dump()
        "page_ide_logs" -> "ide_logs" to ideLogBuffer.dump()
        else -> null
    }

    fun clearSelectedOutput() {
        when (pages.selectedId) {
            "page_build_output" -> clearBuildOutput()
            "page_app_logs" -> appLogBuffer.clear()
            "page_ide_logs" -> ideLogBuffer.clear()
        }
    }
}