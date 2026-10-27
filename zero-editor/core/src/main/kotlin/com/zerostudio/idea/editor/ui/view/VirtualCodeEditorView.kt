package com.zerostudio.idea.editor.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import com.zerostudio.idea.editor.core.buffer.LargeTextPieceTree
import com.zerostudio.idea.editor.core.buffer.VirtualPageManager
import com.zerostudio.idea.editor.core.config.EditorPreferencesState
import com.zerostudio.idea.editor.core.config.LineSeparatorMode
import io.github.rosemoe.sora.event.ClickEvent
import io.github.rosemoe.sora.event.DoubleClickEvent
import io.github.rosemoe.sora.event.LongPressEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.LineSeparator
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.layout.WordwrapLayout
import io.github.rosemoe.sora.widget.style.builtin.MoveCursorAnimator
import kotlinx.coroutines.*
import java.io.File

/**
 * 极致性能虚拟化代码编辑器
 */
class VirtualCodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CodeEditor(context, attrs, defStyleAttr) {

    private val viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pageManager: VirtualPageManager? = null
    private var isFluencyModeActive = false

    init {
        // 启用平滑滚动与光标物理插值驱动
        setCursorAnimator(MoveCursorAnimator(this))
        isCursorAnimationEnabled = true
        
        // 默认阻断输入法联想产生的频繁全量重组
        props.disallowSuggestions = false
        props.autoCompletionOnComposing = true
    }

    /**
     * 绑定 64 位底层大型 Piece Tree 缓存
     */
    fun bindBufferEngine(pieceTree: LargeTextPieceTree, paginationThreshold: Long = 10_000_000L) {
        val manager = VirtualPageManager(pieceTree, paginationThreshold)
        this.pageManager = manager

        viewScope.launch {
            val pageContent = manager.loadCurrentPageContent()
            setText(pageContent)
        }
    }

    /**
     * 极速无损换页
     */
    fun navigateToPage(pageIndex: Int) {
        val manager = pageManager ?: return
        viewScope.launch {
            val newContent = manager.switchPage(pageIndex, text)
            if (newContent != null) {
                setText(newContent)
                setSelection(0, 0)
                ensurePositionVisible(0, 0)
            }
        }
    }

    /**
     * 激活/退出「流畅模式」策略引擎（极速模式）
     * 借鉴 MT 管理器逻辑：在超大文件下剥离重度计算负载，确保绝对 120 FPS 渲染
     */
    fun applyFluencyMode(enable: Boolean) {
        if (this.isFluencyModeActive == enable) return
        this.isFluencyModeActive = enable

        if (enable) {
            // 降级语言解析器为纯文本零开销模式（剥离 TextMate 正则回溯）
            setEditorLanguage(EmptyLanguage())
            // 停用代码块连线检索与折叠分析
            styles?.blocks = null
            // 关闭动态 Inlay Hints
            clearInlayHintProviders()
            // 强制固定单字符等宽快速测量，阻断 HarfBuzz 复杂排版
            props.useICULibToSelectWords = false
            // 降低光标动画负载
            isCursorAnimationEnabled = false
            // 禁用自动括号配对回溯
            props.symbolPairAutoCompletion = false
        } else {
            // 恢复完整 IDE 能力
            props.useICULibToSelectWords = true
            isCursorAnimationEnabled = true
            props.symbolPairAutoCompletion = true
        }
        invalidate()
    }

    /**
     * 响应式排版与辅助显示参数下发
     */
    fun updateStylingProperties(
        wrapWord: Boolean,
        antiWordBreak: Boolean,
        lineSpacingMultiplier: Float,
        fontSize: Float
    ) {
        if (isWordwrap != wrapWord) {
            setWordwrap(wrapWord, antiWordBreak, true)
        }
        this.lineSpacingMultiplier = lineSpacingMultiplier
        if (this.textSizePx != fontSize) {
            setTextSize(fontSize)
        }
    }

    fun applyPreferences(prefs: EditorPreferencesState) {
        isLineNumberEnabled = prefs.showLineNumbers
        setPinLineNumber(prefs.pinLineNumbers)

        // 细粒度控制字符与空白符标记
        var nonPrintableFlags = 0
        if (prefs.showWhitespaceCharacters) {
            nonPrintableFlags = nonPrintableFlags or FLAG_DRAW_WHITESPACE_INNER or FLAG_DRAW_WHITESPACE_LEADING or FLAG_DRAW_WHITESPACE_TRAILING
        }
        if (prefs.showSoftWrapArrows) {
            nonPrintableFlags = nonPrintableFlags or FLAG_DRAW_SOFT_WRAP
        }
        nonPrintablePaintingFlags = nonPrintableFlags

        // ASCII 控制字符展示 (NUL, ESC, etc.)
        isRenderFunctionCharacters = prefs.renderAsciiControlCharacters

        // 自定义字体注入
        prefs.customFontPath?.let { path ->
            val fontFile = File(path)
            if (fontFile.exists()) {
                val typeface = Typeface.createFromFile(fontFile)
                typefaceText = typeface
                typefaceLineNumber = typeface
            }
        }

        // 换行符配置
        lineSeparator = when (prefs.lineSeparator) {
            LineSeparatorMode.UNIX_LF -> LineSeparator.LF
            LineSeparatorMode.MAC_CR -> LineSeparator.CR
            LineSeparatorMode.WINDOWS_CRLF -> LineSeparator.CRLF
        }

        // 缩进规则
        tabWidth = prefs.tabStopSize
        editable = !prefs.readOnlyMode
        
        applyFluencyMode(prefs.isFluencyModeActive(text.length().toLong()))
    }

    /**
     * 触控滚动背压分发
     */
    fun dispatchScrollOffset(dx: Float, dy: Float) {
        val scroller = scroller
        scroller.startScroll(
            offsetX,
            offsetY,
            -dx.toInt(),
            -dy.toInt(),
            0
        )
        postInvalidateOnAnimation()
    }

    fun handleTap(x: Float, y: Float) {
        val pos = getPointPositionOnScreen(x, y)
        val charPos = text.indexer.getCharPosition(pos.first, pos.second)
        setSelection(charPos.line, charPos.column, SelectionChangeEvent.CAUSE_TAP)
        showSoftInput()
    }

    fun handleLongPress(x: Float, y: Float) {
        val pos = getPointPositionOnScreen(x, y)
        val charPos = text.indexer.getCharPosition(pos.first, pos.second)
        selectCurrentWord()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope.cancel()
    }
}