package com.zerostudio.idea.editor.lsp.window

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.zerostudio.idea.editor.lsp.bridge.ZeroLspConnector
import com.zerostudio.idea.editor.lsp.model.LspCapabilitiesHandler
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.event.ClickEvent
import io.github.rosemoe.sora.event.EditorMotionEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.widget.base.EditorPopupWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.*
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity

class ZeroDiagnosticTooltipWindow(
    private val editor: ZeroCodeEditorView,
    private val connectorProvider: () -> ZeroLspConnector?
) : EditorPopupWindow(editor, FEATURE_SCROLL_AS_CONTENT or FEATURE_SHOW_OUTSIDE_VIEW_ALLOWED) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val rootLayout: LinearLayout
    private val titleText: TextView
    private val messageText: TextView
    private val actionsContainer: LinearLayout
    private var activeJob: Job? = null

    init {
        val dp = editor.dpUnit
        rootLayout = LinearLayout(editor.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 10 * dp
                setColor(editor.colorScheme.getColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_BACKGROUND))
                setStroke((1.5f * dp).toInt(), editor.colorScheme.getColor(EditorColorScheme.LINE_DIVIDER))
            }
            elevation = 16 * dp
        }

        // 顶栏：严重度标签与规则码
        val headerLayout = LinearLayout(editor.context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        titleText = TextView(editor.context).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        headerLayout.addView(titleText)
        rootLayout.addView(headerLayout)

        // 详细错误说明
        val scroll = ScrollView(editor.context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (60 * dp).toInt())
        }
        messageText = TextView(editor.context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11.5f
            setTextColor(editor.colorScheme.getColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_DETAILED_MSG))
            setTextIsSelectable(true)
        }
        scroll.addView(messageText)
        rootLayout.addView(scroll)

        // 快速修复容器
        actionsContainer = LinearLayout(editor.context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, (6 * dp).toInt(), 0, 0)
        }
        rootLayout.addView(actionsContainer)

        setContentView(rootLayout)
        registerClickTrigger()
    }

    private fun registerClickTrigger() {
        editor.subscribeAlways(ClickEvent::class.java) { event ->
            if (event.motionRegion == EditorMotionEvent.REGION_TEXT) {
                val diagnostic = editor.diagnosticController.findDiagnosticAt(event.index)
                if (diagnostic != null) {
                    showForDiagnostic(diagnostic, event.line, event.column)
                    event.intercept()
                } else if (isShowing) {
                    dismiss()
                }
            }
        }

        editor.subscribeAlways(ScrollEvent::class.java) {
            if (isShowing) dismiss()
        }
    }

    fun showForDiagnostic(diagnostic: Diagnostic, line: Int, column: Int) {
        activeJob?.cancel()

        val severityColor = when (diagnostic.severity ?: DiagnosticSeverity.Error) {
            DiagnosticSeverity.Error -> 0xFFFF5252.toInt()
            DiagnosticSeverity.Warning -> 0xFFFFB74D.toInt()
            DiagnosticSeverity.Information -> 0xFF42A5F5.toInt()
            DiagnosticSeverity.Hint -> 0xFF81C784.toInt()
        }

        val sourceLabel = "[${diagnostic.source ?: "LSP"}]"
        val codeLabel = diagnostic.code?.let { " ($it)" } ?: ""
        titleText.text = "$sourceLabel$codeLabel"
        titleText.setTextColor(severityColor)

        messageText.text = diagnostic.message
        actionsContainer.removeAllViews()

        // 异步查询匹配的 Code Action
        activeJob = scope.launch {
            val connector = connectorProvider() ?: return@launch
            val handler = LspCapabilitiesHandler(connector)
            val actions = handler.getCodeActions(diagnostic.range, listOf(diagnostic))

            if (actions.isNotEmpty()) {
                for (action in actions.take(2)) { // 最多展示前两条推荐快速修复
                    val fixButton = Button(editor.context).apply {
                        text = "💡 ${action.title}"
                        textSize = 10f
                        setOnClickListener {
                            scope.launch {
                                handler.applyCodeAction(action)
                                dismiss()
                            }
                        }
                    }
                    actionsContainer.addView(fixButton)
                }
            }
        }

        // 计算气泡位置
        val offset = editor.getCharLayoutOffset(line, column)
        val x = offset[1] + editor.measureTextRegionOffset()
        val y = offset[0] - editor.rowHeight - (editor.dpUnit * 80)

        val maxWidth = (editor.width * 0.85f).toInt()
        rootLayout.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        setSize(rootLayout.measuredWidth, rootLayout.measuredHeight)
        setLocationAbsolutely(
            x.toInt().coerceIn(16, (editor.width - rootLayout.measuredWidth - 16).coerceAtLeast(16)),
            maxOf(16, y.toInt())
        )

        if (!isShowing) show()
    }

    fun release() {
        activeJob?.cancel()
        scope.cancel()
        dismiss()
    }
}