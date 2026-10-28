package com.zerostudio.idea.treesitter

import com.zerostudio.idea.treesitter.tsh.TshCompiledResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * @author android_zero
 *
 * 变更说明与设计架构：
 * 1. 采用 SharedFlow + Conflated 防抖设计，保证打字输入不阻塞 Android UI 线程。
 * 2. 结合视口参数（visibleStartLine, visibleEndLine），仅刷新屏幕可见区间，保证 120fps 平滑流畅。
 * 3. 既支持通过编译后的 TshCompiledResult 一键驱动，也支持原始 SCM 查询。
 */
class HighlightDispatcher(
    soPath: String,
    languageFuncName: String,
    tshBundle: TshCompiledResult? = null,
    rawScmQuery: String? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : Closeable {

    private val engine = NativeHighlightEngine().apply {
        setLanguageFromSharedLib(soPath, languageFuncName)
        if (tshBundle != null) {
            setQuery(tshBundle.standardScmQuery)
            setCaptureStyleTable(tshBundle.flatStyleTable, tshBundle.captureCount)
        } else if (rawScmQuery != null) {
            setQuery(rawScmQuery)
        }
    }

    private val actionIdMap = tshBundle?.actionIdMap ?: emptyMap()
    private val captureNameCache = ConcurrentHashMap<Int, String>()

    private val _highlightFlow = MutableSharedFlow<HighlightResult>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val highlightFlow: SharedFlow<HighlightResult> = _highlightFlow.asSharedFlow()

    data class HighlightResult(
        val visibleStartLine: Int,
        val visibleEndLine: Int,
        val spans: HighlightSpanArray,
        val actionResolver: (actionId: Int) -> String?,
        val nameResolver: (captureId: Int) -> String?
    )

    fun initFullText(content: String, visibleStartLine: Int = 0, visibleEndLine: Int = 100) {
        scope.launch {
            engine.setText(content)
            refreshViewport(visibleStartLine, visibleEndLine)
        }
    }

    fun dispatchEdit(
        event: TextEditEvent,
        visibleStartLine: Int,
        visibleEndLine: Int
    ) {
        scope.launch {
            engine.applyEdit(event)
            refreshViewport(visibleStartLine, visibleEndLine)
        }
    }

    fun refreshViewport(visibleStartLine: Int, visibleEndLine: Int) {
        scope.launch {
            val spans = engine.computeViewportHighlight(visibleStartLine, visibleEndLine)
            _highlightFlow.emit(
                HighlightResult(
                    visibleStartLine = visibleStartLine,
                    visibleEndLine = visibleEndLine,
                    spans = spans,
                    actionResolver = { actionId -> actionIdMap[actionId] },
                    nameResolver = ::resolveCaptureName
                )
            )
        }
    }

    private fun resolveCaptureName(captureId: Int): String? {
        return captureNameCache.getOrPut(captureId) {
            engine.getCaptureName(captureId) ?: "unknown"
        }
    }

    override fun close() {
        scope.cancel()
        engine.close()
    }
}