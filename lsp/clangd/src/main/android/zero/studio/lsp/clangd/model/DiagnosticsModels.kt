package android.zero.studio.lsp.clangd.model

/**
 * 代表 LSP diagnostics 项，与 Native 结构保持一致。
 */
data class DiagnosticItem(
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int,
    val severity: Int,
    val message: String,
    val source: String?,
    val code: String?
)

/**
 * 用于 UI 显示的诊断信息（适配 LSP 标准格式）
 */
data class Diagnostic(
    val uri: String,                    // 文件路径
    val range: Range,                   // 位置范围
    val severity: Int,                  // 严重程度 (1=Error, 2=Warning, 3=Info, 4=Hint)
    val message: String,                // 诊断消息
    val source: String? = null,         // 来源（如 "clangd"）
    val code: String? = null            // 错误代码
) {
    data class Range(
        val start: Position,
        val end: Position
    )
    
    data class Position(
        val line: Int,          // 行号（0-based）
        val character: Int      // 列号（0-based）
    )
    
    companion object {
        /**
         * 从 DiagnosticItem 转换为 Diagnostic
         */
        fun fromDiagnosticItem(item: DiagnosticItem, uri: String): Diagnostic {
            return Diagnostic(
                uri = uri,
                range = Range(
                    start = Position(item.startLine, item.startCharacter),
                    end = Position(item.endLine, item.endCharacter)
                ),
                severity = item.severity,
                message = item.message,
                source = item.source,
                code = item.code
            )
        }
    }
}

/**
 * 扩展函数：将 DiagnosticItem 转换为 Diagnostic
 */
fun DiagnosticItem.toDiagnostic(uri: String): Diagnostic {
    return Diagnostic.fromDiagnosticItem(this, uri)
}
