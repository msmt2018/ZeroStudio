package com.zerostudio.idea.treesitter.tsh

/**
 * @author android_zero
 *
 * 变更说明：
 * 1. 负责提取全部 AST Pattern，彻底分离样式块 `-> { ... }`。
 * 2. 糖化还原：将内嵌的 `{ match: "regex" }` 智能还原为 Tree-Sitter 核心标准的 `(#match? ...)` 谓词。
 */
class TshParser(private val tokens: List<TshToken>) {
    private var current = 0
    val errors = mutableListOf<String>()

    fun parse(): TshDocument? {
        var metadata: TshMetadata? = null
        var behaviors = TshBehaviors()
        val rules = mutableListOf<TshRule>()

        while (!isAtEnd()) {
            when {
                match(TshTokenType.METADATA) -> metadata = parseMetadata()
                match(TshTokenType.BEHAVIORS) -> behaviors = parseBehaviors()
                match(TshTokenType.HIGHLIGHTS) -> rules.addAll(parseHighlights())
                else -> {
                    errors.add("Unknown block at line ${peek().position.line}: ${peek().value}")
                    advance()
                }
            }
        }

        if (metadata == null) {
            errors.add("Missing required 'metadata' block.")
            return null
        }
        return TshDocument(metadata, behaviors, rules)
    }

    private fun parseMetadata(): TshMetadata {
        consume(TshTokenType.LBRACE)
        var name = ""
        var scope = ""
        var version = "1.0.0"
        val exts = mutableListOf<String>()

        while (!check(TshTokenType.RBRACE) && !isAtEnd()) {
            val key = consume(TshTokenType.IDENTIFIER).value
            consume(TshTokenType.COLON)
            when (key) {
                "name" -> name = consume(TshTokenType.STRING).value
                "scope" -> scope = consume(TshTokenType.STRING).value
                "version" -> version = consume(TshTokenType.STRING).value
                "extensions" -> {
                    consume(TshTokenType.LBRACKET)
                    while (!check(TshTokenType.RBRACKET) && !isAtEnd()) {
                        exts.add(consume(TshTokenType.STRING).value)
                    }
                    consume(TshTokenType.RBRACKET)
                }
            }
        }
        consume(TshTokenType.RBRACE)
        return TshMetadata(name, exts, scope, version)
    }

    private fun parseBehaviors(): TshBehaviors {
        consume(TshTokenType.LBRACE)
        var lineComment: String? = null
        var blockComment: Pair<String, String>? = null
        val brackets = mutableListOf<Pair<String, String>>()
        val autoClose = mutableListOf<Pair<String, String>>()

        while (!check(TshTokenType.RBRACE) && !isAtEnd()) {
            val section = consume(TshTokenType.IDENTIFIER).value
            consume(TshTokenType.LBRACE)
            when (section) {
                "comments" -> {
                    while (!check(TshTokenType.RBRACE) && !isAtEnd()) {
                        val k = consume(TshTokenType.IDENTIFIER).value
                        consume(TshTokenType.COLON)
                        if (k == "line") {
                            lineComment = consume(TshTokenType.STRING).value
                        } else if (k == "block") {
                            consume(TshTokenType.LBRACKET)
                            val s = consume(TshTokenType.STRING).value
                            val e = consume(TshTokenType.STRING).value
                            consume(TshTokenType.RBRACKET)
                            blockComment = Pair(s, e)
                        }
                    }
                }
                "brackets" -> parsePairs(brackets)
                "auto_close" -> parsePairs(autoClose)
            }
            consume(TshTokenType.RBRACE)
        }
        consume(TshTokenType.RBRACE)
        return TshBehaviors(lineComment, blockComment, brackets, autoClose)
    }

    private fun parsePairs(list: MutableList<Pair<String, String>>) {
        while (!check(TshTokenType.RBRACE) && !isAtEnd()) {
            consume(TshTokenType.IDENTIFIER)
            consume(TshTokenType.COLON)
            consume(TshTokenType.LBRACKET)
            val a = consume(TshTokenType.STRING).value
            val b = consume(TshTokenType.STRING).value
            consume(TshTokenType.RBRACKET)
            list.add(Pair(a, b))
        }
    }

    private fun parseHighlights(): List<TshRule> {
        consume(TshTokenType.LBRACE)
        val list = mutableListOf<TshRule>()

        while (!check(TshTokenType.RBRACE) && !isAtEnd()) {
            val rule = parseRule()
            if (rule != null) list.add(rule)
        }
        consume(TshTokenType.RBRACE)
        return list
    }

    private fun parseRule(): TshRule? {
        val patternToken = consume(TshTokenType.RAW_SCM_PATTERN)
        var scmText = patternToken.value
        var mainCaptureTag: String? = null

        if (check(TshTokenType.TAG)) {
            mainCaptureTag = consume(TshTokenType.TAG).value
        }

        // 识别扩展糖化块 { match: "..." }
        if (check(TshTokenType.LBRACE)) {
            consume(TshTokenType.LBRACE)
            while (!check(TshTokenType.RBRACE) && !isAtEnd()) {
                val filterKey = consume(TshTokenType.IDENTIFIER).value
                consume(TshTokenType.COLON)
                val filterVal = consume(TshTokenType.STRING).value
                if (filterKey == "match" && mainCaptureTag != null) {
                    // 语法糖降级转译为原生谓词
                    scmText = "($scmText @$mainCaptureTag\n  (#match? @$mainCaptureTag \"$filterVal\"))"
                    mainCaptureTag = null
                }
            }
            consume(TshTokenType.RBRACE)
        }

        consume(TshTokenType.ARROW)
        consume(TshTokenType.LBRACE)

        val captureStyles = mutableMapOf<String, SingleCaptureStyle>()

        if (check(TshTokenType.TAG)) {
            while (!check(TshTokenType.RBRACE) && !isAtEnd()) {
                val tag = consume(TshTokenType.TAG).value
                consume(TshTokenType.COLON)
                consume(TshTokenType.LBRACE)
                captureStyles[tag] = parseSingleStyleProperties()
                consume(TshTokenType.RBRACE)
            }
        } else {
            val style = parseSingleStyleProperties()
            if (mainCaptureTag != null) {
                captureStyles[mainCaptureTag] = style
                scmText = "$scmText @$mainCaptureTag"
            } else {
                captureStyles["default"] = style
            }
        }

        consume(TshTokenType.RBRACE)
        return TshRule(scmText, captureStyles, patternToken.position)
    }

    private fun parseSingleStyleProperties(): SingleCaptureStyle {
        var color: String? = null
        var bold = false
        var italic = false
        var underline = false
        var strikethrough = false
        var priority = 100
        var action: String? = null
        var tooltip: String? = null

        while (!check(TshTokenType.RBRACE) && !isAtEnd()) {
            val key = consume(TshTokenType.IDENTIFIER).value
            consume(TshTokenType.COLON)
            when (key) {
                "color" -> color = consume(TshTokenType.STRING).value
                "action" -> action = consume(TshTokenType.STRING).value
                "tooltip" -> tooltip = consume(TshTokenType.STRING).value
                "priority" -> {
                    val tok = advance()
                    priority = tok.value.toIntOrNull() ?: 100
                }
                "style" -> {
                    if (check(TshTokenType.LBRACKET)) {
                        advance()
                        while (!check(TshTokenType.RBRACKET) && !isAtEnd()) {
                            when (consume(TshTokenType.IDENTIFIER).value) {
                                "bold" -> bold = true
                                "italic" -> italic = true
                                "underline" -> underline = true
                                "strikethrough" -> strikethrough = true
                            }
                        }
                        consume(TshTokenType.RBRACKET)
                    } else {
                        when (consume(TshTokenType.IDENTIFIER).value) {
                            "bold" -> bold = true
                            "italic" -> italic = true
                            "underline" -> underline = true
                            "strikethrough" -> strikethrough = true
                        }
                    }
                }
            }
        }
        return SingleCaptureStyle(color, bold, italic, underline, strikethrough, priority, action, tooltip)
    }

    private fun match(type: TshTokenType): Boolean = if (check(type)) { advance(); true } else false
    private fun check(type: TshTokenType): Boolean = !isAtEnd() && peek().type == type
    private fun advance(): TshToken = if (!isAtEnd()) tokens[current++] else tokens.last()
    private fun isAtEnd(): Boolean = peek().type == TshTokenType.EOF
    private fun peek(): TshToken = tokens[current]
    private fun consume(type: TshTokenType): TshToken {
        if (check(type)) return advance()
        throw IllegalStateException("Expect $type at line ${peek().position.line}, found ${peek().type}")
    }
}