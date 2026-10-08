package com.itsaky.androidide.activities.editor.ui.state

import android.content.Context
import androidx.compose.runtime.*
import androidx.fragment.app.Fragment
import com.itsaky.androidide.R
import com.itsaky.androidide.adapters.DiagnosticsAdapter
import com.itsaky.androidide.adapters.SearchListAdapter
import com.itsaky.androidide.fragments.DiagnosticsListFragment
import com.itsaky.androidide.fragments.SearchResultFragment
import com.itsaky.androidide.fragments.output.*
import com.itsaky.androidide.fragments.toolbox.EditorToolboxFragment
import com.itsaky.androidide.debugger.fragment.*
import com.itsaky.androidide.models.LogLine

/** Bottom drawer state and durable output, independent of whether a page is mounted. */
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
    private val active = mutableStateMapOf<Class<out Fragment>, Fragment>()
    private val buildOutput = StringBuilder()
    private val appLogs = ArrayDeque<String>()
    private var diagnostics: DiagnosticsAdapter? = null
    private var search: SearchListAdapter? = null
    private var diagnosticsEmpty = true
    private var searchEmpty = true

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var closed = false
    private val appSession = EditorAppLogSession(context, ::appendApkLog)
    private val ideLines = ArrayDeque<String>()
    private val appender = com.itsaky.androidide.logging.LifecycleAwareAppender()
    private var owner: androidx.lifecycle.LifecycleOwner? = null
    private fun onMain(block: () -> Unit) {
        if (android.os.Looper.myLooper() == main.looper) { if (!closed) block() }
        else main.post { if (!closed) block() }
    }
    fun start(owner: androidx.lifecycle.LifecycleOwner) {
        this.owner = owner
        appSession.start()
        appender.consumer = { line -> onMain {
            ideLines.addLast(line)
            while (ideLines.size > LogViewFragment.MAX_LINE_COUNT) ideLines.removeFirst()
            (active[IDELogFragment::class.java] as? IDELogFragment)?.appendIdeLine(line)
        } }
        appender.attachTo(owner)
        appender.context = org.slf4j.LoggerFactory.getILoggerFactory() as ch.qos.logback.classic.LoggerContext
        appender.start()
        (org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger).addAppender(appender)
    }
    fun close() {
        closed = true
        appSession.close()
        owner?.let(appender::detachFrom)
        appender.stop()
        (org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger).detachAppender(appender)
        main.removeCallbacksAndMessages(null)
        active.clear()
    }

    init {
        listOf(
            R.string.build_output to BuildOutputFragment::class.java,
            R.string.app_logs to AppLogFragment::class.java,
            R.string.ide_logs to IDELogFragment::class.java,
            R.string.view_diags to DiagnosticsListFragment::class.java,
            R.string.view_search_results to SearchResultFragment::class.java,
            R.string.title_editor_toolbox to EditorToolboxFragment::class.java,
            R.string.editor_tab_breakpoints to BreakpointListFragment::class.java,
            R.string.editor_tab_variables to VariablesFragment::class.java,
            R.string.editor_tab_callstack to CallStackFragment::class.java,
            R.string.editor_tab_watches to WatchesFragment::class.java,
            R.string.editor_tab_logpoint to LogpointFragment::class.java,
        ).forEach { (title, type) -> pages.register(EditorPage.FragmentPage(type.name, context.getString(title), type)) }
    }

    fun attach(fragment: Fragment) = onMain {
        if (active[fragment.javaClass] === fragment) return@onMain
        active[fragment.javaClass] = fragment
        when (fragment) {
            is BuildOutputFragment -> { fragment.clearOutput(); if (buildOutput.isNotEmpty()) fragment.appendOutput(buildOutput.toString()) }
            is IDELogFragment -> { fragment.clearOutput(); ideLines.forEach(fragment::appendIdeLine) }
            is AppLogFragment -> { fragment.clearOutput(); appLogs.forEach(fragment::appendAppLine) }
            is DiagnosticsListFragment -> { diagnostics?.let(fragment::setAdapter); fragment.isEmpty = diagnosticsEmpty }
            is SearchResultFragment -> { search?.let(fragment::setAdapter); fragment.isEmpty = searchEmpty }
        }
    }
    fun detach(fragment: Fragment) = onMain { if (active[fragment.javaClass] === fragment) active.remove(fragment.javaClass) }
    fun forceCollapse() { visible = false }
    fun tryExpandSheetFromControl(): Boolean { visible = true; return true }
    fun selectTabByFragmentClass(type: Class<out Fragment>): Boolean = pages.select(type.name)
    fun setActionText(text: CharSequence) = onMain { actionText = text.toString() }
    fun setActionProgress(progress: Int) = onMain { actionProgress = progress.coerceIn(0, 100) / 100f }
    fun showChild(index: Int) = onMain { headerPage = index }
    fun setStatus(text: CharSequence, gravity: Int = 0) = onMain { status = text.toString() }
    fun appendBuildOut(text: String?) = onMain {
        val line = text.orEmpty().let { if (it.endsWith('\n')) it else "$it\n" }
        buildOutput.append(line)
        (active[BuildOutputFragment::class.java] as? BuildOutputFragment)?.appendOutput(line)
    }
    fun clearBuildOutput() = onMain { buildOutput.clear(); (active[BuildOutputFragment::class.java] as? BuildOutputFragment)?.clearOutput() }
    fun appendApkLog(line: LogLine) {
        // LogLine is pooled: snapshot before crossing threads or handing it back to the pool.
        val text = line.toString()
        line.recycle()
        onMain {
            appLogs.addLast(text)
            while (appLogs.size > LogViewFragment.MAX_LINE_COUNT) appLogs.removeFirst()
            (active[AppLogFragment::class.java] as? AppLogFragment)?.appendAppLine(text)
        }
    }

    /** Use durable buffers for sharing, including lines not yet flushed to a mounted editor. */
    fun selectedOutputSnapshot(): Pair<String, String>? = when (pages.selectedId) {
        BuildOutputFragment::class.java.name -> "build_output" to buildOutput.toString()
        AppLogFragment::class.java.name -> "app_logs" to appLogs.joinToString("\n")
        IDELogFragment::class.java.name -> "ide_logs" to ideLines.joinToString("\n")
        else -> selectedOutput?.let { it.getFilename() to it.getContent() }
    }

    fun setDiagnosticsAdapter(adapter: DiagnosticsAdapter) = onMain { diagnostics = adapter; (active[DiagnosticsListFragment::class.java] as? DiagnosticsListFragment)?.setAdapter(adapter) }
    fun setSearchResultAdapter(adapter: SearchListAdapter) = onMain { search = adapter; (active[SearchResultFragment::class.java] as? SearchResultFragment)?.setAdapter(adapter) }
    fun handleDiagnosticsResultVisibility(empty: Boolean) = onMain { diagnosticsEmpty = empty; (active[DiagnosticsListFragment::class.java] as? DiagnosticsListFragment)?.isEmpty = empty }
    fun handleSearchResultVisibility(empty: Boolean) = onMain { searchEmpty = empty; (active[SearchResultFragment::class.java] as? SearchResultFragment)?.isEmpty = empty }
    val selectedOutput: ShareableOutputFragment? get() = (pages.selectedPage as? EditorPage.FragmentPage)?.fragmentClass?.let { active[it] } as? ShareableOutputFragment
    fun clearSelectedOutput() = onMain {
        when (pages.selectedId) {
            BuildOutputFragment::class.java.name -> clearBuildOutput()
            IDELogFragment::class.java.name -> { ideLines.clear(); selectedOutput?.clearOutput() }
            AppLogFragment::class.java.name -> { appLogs.clear(); selectedOutput?.clearOutput() }
            else -> selectedOutput?.clearOutput()
        }
    }
}
