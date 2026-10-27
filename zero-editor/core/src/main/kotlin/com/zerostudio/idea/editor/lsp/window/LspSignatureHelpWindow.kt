package com.zerostudio.idea.editor.lsp.window

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.zerostudio.idea.editor.lsp.bridge.ZeroLspConnector
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.base.EditorPopupWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.*
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.TextDocumentIdentifier

class LspSignatureHelpWindow(
    private val editor: ZeroCodeEditorView,
    private val connectorProvider: () -> ZeroLspConnector?
) : EditorPopupWindow(editor, FEATURE_SCROLL_AS_CONTENT or FEATURE_DISMISS_WHEN_OBSCURING_CURSOR) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val labelText: TextView
    private val docText: TextView
    private val rootLayout: LinearLayout
    private var activeJob: Job? = null

    init {
        val dp = editor.dpUnit
        rootLayout = LinearLayout(editor.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8 * dp
                setColor(editor.colorScheme.getColor(EditorColorScheme.SIGNATURE_BACKGROUND))
                setStroke((1 * dp).toInt(), editor.colorScheme.getColor(EditorColorScheme.SIGNATURE_BORDER))
            }
            elevation = 12 * dp
        }

        labelText = TextView(editor.context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(editor.colorScheme.getColor(EditorColorScheme.SIGNATURE_TEXT_NORMAL))
        }
        docText = TextView(editor.context).apply {
            textSize = 11.5f
            setTextColor(editor.colorScheme.getColor(EditorColorScheme.TEXT_NORMAL))
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }

        rootLayout.addView(labelText)
        rootLayout.addView(docText)
        setContentView(rootLayout)

        registerTriggers()
    }

    private fun registerTriggers() {
        // 键入触发符: '(' 或 ','
        editor.subscribeAlways(ContentChangeEvent::class.java) { event ->
            if (event.action == ContentChangeEvent.ACTION_INSERT) {
                val txt = event.changedText
                if (txt.contains("(") || txt.contains(",")) {
                    val p = event.changeEnd
                    requestSignatureHelp(p.line, p.column)
                }
            }
        }

        // 光标在括号内移动时持续跟进参数焦点
        editor.subscribeAlways(SelectionChangeEvent::class.java) { event ->
            if (isShowing && !event.isSelected) {
                requestSignatureHelp(event.left.line, event.left.column)
            }
        }
    }

    fun requestSignatureHelp(line: Int, column: Int) {
        activeJob?.cancel()
        activeJob = scope.launch {
            val connector = connectorProvider() ?: return@launch
            val server = connector.getRemoteServer() ?: return@launch

            val help = withContext(Dispatchers.IO) {
                val params = SignatureHelpParams(
                    TextDocumentIdentifier(connector.fileUri),
                    Position(line, column)
                )
                runCatching {
                    server.textDocumentService.signatureHelp(params).get()
                }.getOrNull()
            }

            if (help != null && help.signatures.isNotEmpty()) {
                displaySignatures(help)
            } else {
                dismiss()
            }
        }
    }

    private fun displaySignatures(help: SignatureHelp) {
        val sigIdx = help.activeSignature ?: 0
        if (sigIdx !in help.signatures.indices) {
            dismiss()
            return
        }

        val sig = help.signatures[sigIdx]
        val activeParamIdx = help.activeParameter ?: sig.activeParameter ?: 0

        val builder = SpannableStringBuilder(sig.label)
        val highlightColor = editor.colorScheme.getColor(EditorColorScheme.SIGNATURE_TEXT_HIGHLIGHTED_PARAMETER)

        // 高亮当前活动的形参
        if (sig.parameters != null && activeParamIdx in sig.parameters.indices) {
            val param = sig.parameters[activeParamIdx]
            val paramLabel = when {
                param.label.isLeft -> param.label.left
                param.label.isRight -> {
                    val range = param.label.right
                    sig.label.substring(range[0], range[1])
                }
                else -> null
            }

            if (paramLabel != null) {
                val start = sig.label.indexOf(paramLabel)
                if (start != -1) {
                    builder.setSpan(StyleSpan(Typeface.BOLD), start, start + paramLabel.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(ForegroundColorSpan(highlightColor), start, start + paramLabel.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            // 参数说明
            val doc = param.documentation?.let {
                if (it.isLeft) it.left else it.right.value
            } ?: sig.documentation?.let {
                if (it.isLeft) it.left else it.right.value
            }
            if (!doc.isNullOrBlank()) {
                docText.visibility = View.VISIBLE
                docText.text = doc
            } else {
                docText.visibility = View.GONE
            }
        } else {
            docText.visibility = View.GONE
        }

        labelText.text = builder

        // 测量并布局在光标正上方
        val cursor = editor.cursor.left()
        val offset = editor.getCharLayoutOffset(cursor.line, cursor.column)
        val x = offset[1] + editor.measureTextRegionOffset()
        val y = offset[0] - editor.rowHeight - (editor.dpUnit * 48)

        rootLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        setSize(rootLayout.measuredWidth.coerceAtMost((editor.width * 0.9f).toInt()), rootLayout.measuredHeight)
        setLocationAbsolutely(x.toInt(), maxOf(0, y.toInt()))

        if (!isShowing) show()
    }

    fun release() {
        activeJob?.cancel()
        scope.cancel()
        dismiss()
    }
}