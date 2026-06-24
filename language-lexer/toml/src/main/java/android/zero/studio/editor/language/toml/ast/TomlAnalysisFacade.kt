package android.zero.studio.editor.language.toml.ast

/** TOML 分析聚合入口（AST + Symbol）。 */
object TomlAnalysisFacade {

    fun analyze(code: String): TomlAstResult {
        val root = TomlAstParser.parseToNode(code)
        val symbols = TomlSymbolResolver.parseSymbols(code)
        return TomlAstResult(root = root, symbols = symbols)
    }
}
