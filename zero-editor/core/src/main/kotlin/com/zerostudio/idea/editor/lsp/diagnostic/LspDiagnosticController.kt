package com.zerostudio.idea.editor.lsp.diagnostic

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.event.PublishDiagnosticsEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.diagnostic.Quickfix
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity

/**
 * 诊断严重度视觉配置规范
 */
data class DiagnosticStyleConfig(
    val color: Int,
    val waveLengthPx: Float = 14f,
    val waveAmplitudePx: Float = 3.5f,
    val strokeWidthPx: Float = 2.0f,
    val isDotted: Boolean = false
)

/**
 * LSP 诊断着色与自定义波浪线/下划线渲染控制器
 */
class LspDiagnosticController(val editor: ZeroCodeEditorView) {

    // 默认主题颜色：红、橙黄、蓝、灰绿
    var errorStyle = DiagnosticStyleConfig(color = 0xFFFF5252.toInt(), waveLengthPx = 14f, waveAmplitudePx = 3.5f, strokeWidthPx = 2.2f)
    var warningStyle = DiagnosticStyleConfig(color = 0xFFFFB74D.toInt(), waveLengthPx = 14f, waveAmplitudePx = 3.0f, strokeWidthPx = 2.0f)
    var infoStyle = DiagnosticStyleConfig(color = 0xFF42A5F5.toInt(), waveLengthPx = 12f, waveAmplitudePx = 2.5f, strokeWidthPx = 1.8f)
    var hintStyle = DiagnosticStyleConfig(color = 0xFF81C784.toInt(), strokeWidthPx = 2.0f, isDotted = true)

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val wavePath = Path()
    private val rawDiagnostics = mutableListOf<Diagnostic>()

    fun updateLspDiagnostics(diagnostics: List<Diagnostic>, fileUri: String) {
        rawDiagnostics.clear()
        rawDiagnostics.addAll(diagnostics)

        val container = DiagnosticsContainer(true)
        val indexer = editor.text.indexer

        for ((idCounter, d) in diagnostics.withIndex()) {
            val startLine = d.range.start.line
            val startCol = d.range.start.character
            val endLine = d.range.end.line
            val endCol = d.range.end.character

            val startIndex = indexer.getCharIndex(startLine, startCol)
            val endIndex = indexer.getCharIndex(endLine, endCol)

            val severity = when (d.severity ?: DiagnosticSeverity.Information) {
                DiagnosticSeverity.Error -> DiagnosticRegion.SEVERITY_ERROR
                DiagnosticSeverity.Warning -> DiagnosticRegion.SEVERITY_WARNING
                DiagnosticSeverity.Information -> DiagnosticRegion.SEVERITY_TYPO
                DiagnosticSeverity.Hint -> DiagnosticRegion.SEVERITY_NONE
            }

            val detail = DiagnosticDetail(
                briefMessage = "[${d.source ?: "LSP"}] ${d.message}",
                detailedMessage = d.code?.let { "Code: $it\n" }.orEmpty() + d.message,
                quickfixes = emptyList(), // 由 CodeAction 动态异步解析
                extraData = d
            )

            val region = DiagnosticRegion(startIndex, endIndex, severity, idCounter.toLong(), detail)
            container.addDiagnostic(region)
        }

        editor.postInLifecycle {
            editor.diagnostics = container
            editor.invalidateDiagnostics()
            editor.invalidate()
        }
    }

    /**
     * 接管原生绘制，针对不同诊断源及级别光栅化特定波浪线
     * 在每一行文字排版完成时调用
     */
    fun drawDiagnosticWaveLines(
        canvas: Canvas,
        lineIndex: Int,
        contentStartCol: Int,
        contentEndCol: Int,
        baselineY: Float,
        textRegionOffset: Float
    ) {
        val container = editor.diagnostics ?: return
        val regions = container.regions
        if (regions.isEmpty()) return

        val text = editor.text
        val lineStartIdx = text.getCharIndex(lineIndex, contentStartCol)
        val lineEndIdx = text.getCharIndex(lineIndex, contentEndCol)

        for (region in regions) {
            // 区间相交检查
            if (region.endIndex <= lineStartIdx || region.startIndex >= lineEndIdx) {
                continue
            }

            val overlapStart = maxOf(region.startIndex, lineStartIdx)
            val overlapEnd = minOf(region.endIndex, lineEndIdx)

            val startPos = text.indexer.getCharPosition(overlapStart)
            val endPos = text.indexer.getCharPosition(overlapEnd)

            val startOffsetArr = editor.getCharLayoutOffset(startPos.line, startPos.column)
            val endOffsetArr = editor.getCharLayoutOffset(endPos.line, endPos.column)

            val leftX = startOffsetArr[1] + textRegionOffset
            val rightX = endOffsetArr[1] + textRegionOffset
            val waveBaseY = baselineY + (editor.dpUnit * 2.5f)

            val style = when (region.severity) {
                DiagnosticRegion.SEVERITY_ERROR -> errorStyle
                DiagnosticRegion.SEVERITY_WARNING -> warningStyle
                DiagnosticRegion.SEVERITY_TYPO -> infoStyle
                else -> hintStyle
            }

            wavePaint.color = style.color
            wavePaint.strokeWidth = style.strokeWidthPx

            if (style.isDotted) {
                drawDottedLine(canvas, leftX, rightX, waveBaseY, wavePaint)
            } else {
                drawSquigglyCurve(canvas, leftX, rightX, waveBaseY, style.waveLengthPx, style.waveAmplitudePx, wavePaint)
            }
        }
    }

    private fun drawSquigglyCurve(
        canvas: Canvas,
        startX: Float,
        endX: Float,
        baseY: Float,
        waveLength: Float,
        amplitude: Float,
        paint: Paint
    ) {
        if (startX >= endX) return
        wavePath.reset()
        wavePath.moveTo(startX, baseY)

        var x = startX
        var direction = -1f // 向上或向下摆动

        while (x < endX) {
            val nextX = minOf(x + (waveLength / 2f), endX)
            val midX = (x + nextX) / 2f
            val controlY = baseY + (direction * amplitude)
            wavePath.quadTo(midX, controlY, nextX, baseY)
            direction = -direction
            x = nextX
        }
        canvas.drawPath(wavePath, paint)
    }

    private fun drawDottedLine(canvas: Canvas, startX: Float, endX: Float, y: Float, paint: Paint) {
        val step = editor.dpUnit * 4f
        var x = startX
        while (x < endX) {
            canvas.drawPoint(x, y, paint)
            x += step
        }
    }

    fun findDiagnosticAt(charIndex: Int): Diagnostic? {
        val region = editor.diagnostics?.regions?.find { charIndex in it.startIndex..it.endIndex }
        return region?.detail?.extraData as? Diagnostic
    }
}