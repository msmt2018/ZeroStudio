package com.zerostudio.idea.editor.lsp.window

import android.graphics.Typeface
import android.text.Html
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.zerostudio.idea.editor.lsp.bridge.ZeroLspConnector
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.event.HoverEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.widget.base.EditorPopupWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.*
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier

class LspHoverTooltipWindow(
    private val editor: ZeroCodeEditorView,
    private val connectorProvider: () -> ZeroLspConnector?,
    private val onJumpDefinition: (Int, Int) -> Unit
) : EditorPopupWindow(editor, FEATURE_SCROLL_AS_CONTENT or FEATURE_SHOW_OUTSIDE_VIEW_ALLOWED) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val rootLayout: LinearLayout
    private val contentText: TextView
    private val jumpDefButton: Button
    private var hoverJob: Job? = null
    private var targetLine: Int = 0
    private var targetCol: Int = 0

    init {
        val dp = editor.dpUnit
        rootLayout = LinearLayout(editor.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 10 * dp
                setColor(editor.colorScheme.getColor(EditorColorScheme.HOVER_BACKGROUND))
                setStroke((1 * dp).toInt(), editor.colorScheme.getColor(EditorColorScheme.HOVER_BORDER))
            }
            elevation = 16 * dp
        }

        val scroll = ScrollView(editor.context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
        }

        contentText = TextView(editor.context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(editor.colorScheme.getColor(EditorColorScheme.HOVER_TEXT_NORMAL))
            setTextIsSelectable(true)
        }
        scroll.addView(contentText)

        jumpDefButton = Button(editor.context).apply {
            text = "跳转到定义 (Go to Def)"
            textSize = 11f
            setOnClickListener {
                onJumpDefinition(targetLine, targetCol)
                dismiss()
            }
        }

        rootLayout.addView(scroll)
        rootLayout.addView(jumpDefButton)
        setContentView(rootLayout)

        registerHoverListener()
    }

    private fun registerHoverListener() {
        editor.subscribeAlways(HoverEvent::class.java) { event ->
            if (event.causingEvent.action == MotionEvent.ACTION_HOVER_MOVE) {
                targetLine = event.line
                targetCol = event.column
                queryHoverDebounced(targetLine, targetCol)
            }
        }

        editor.subscribeAlways(ScrollEvent::class.java) {
            if (isShowing) dismiss()
        }
    }

    private fun queryHoverDebounced(line: Int, column: Int) {
        hoverJob?.cancel()
        hoverJob = scope.launch {
            delay(350)
            val connector = connectorProvider() ?: return@launch
            val server = connector.getRemoteServer() ?: return@launch

            val hover: Hover? = withContext(Dispatchers.IO) {
                val params = HoverParams(TextDocumentIdentifier(connector.fileUri), Position(line, column))
                runCatching {
                    server.textDocumentService.hover(params).get()
                }.getOrNull()
            }

            if (hover != null && hover.contents != null) {
                val markdown = when {
                    hover.contents.isRight -> hover.contents.right.value
                    hover.contents.isLeft -> hover.contents.left.joinToString("\n\n") {
                        if (it.isLeft) it.left else it.right.value
                    }
                    else -> ""
                }

                if (markdown.isNotBlank()) {
                    showHoverContent(markdown, line, column)
                } else {
                    dismiss()
                }
            } else {
                dismiss()
            }
        }
    }

    private fun showHoverContent(markdown: String, line: Int, col: Int) {
        contentText.text = Html.fromHtml(
            markdown.replace("\n", "<br/>")
                .replace("```", "<pre>")
                .replace("`", "<code>"),
            Html.FROM_HTML_MODE_COMPACT
        )

        val offset = editor.getCharLayoutOffset(line, col)
        val x = offset[1] + editor.measureTextRegionOffset()
        val y = offset[0] + (editor.dpUnit * 6)

        val maxWidth = (editor.width * 0.85f).toInt()
        val maxHeight = (editor.height * 0.45f).toInt()

        rootLayout.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
        )

        setSize(rootLayout.measuredWidth, rootLayout.measuredHeight)
        setLocationAbsolutely(x.toInt().coerceAtMost(editor.width - rootLayout.measuredWidth - 16), y.toInt())

        if (!isShowing) show()
    }

    fun release() {
        hoverJob?.cancel()
        scope.cancel()
        dismiss()
    }
}