package com.zerostudio.idea.treesitter.tsh

/**
 * @author android_zero
 *
 * 变更说明：
 * 精准处理复杂 SCM 嵌套，识别 Tree-sitter 特色语法：锚点(.)、否定字段(!)、多重引号、谓词(#)。
 */
class TshLexer(private val source: String) {
    private var index = 0
    private var line = 1
    private var column = 1

    fun tokenize(): List<TshToken> {
        val tokens = mutableListOf<TshToken>()
        while (index < source.length) {
            skipWhitespaceAndComments()
            if (index >= source.length) break

            val pos = TshSourcePosition(line, column)
            val c = source[index]

            when {
                c == '{' -> { advance(); tokens.add(TshToken(TshTokenType.LBRACE, "{", pos)) }
                c == '}' -> { advance(); tokens.add(TshToken(TshTokenType.RBRACE, "}", pos)) }
                c == ':' -> { advance(); tokens.add(TshToken(TshTokenType.COLON, ":", pos)) }
                c == '-' && peek() == '>' -> {
                    advance(); advance()
                    tokens.add(TshToken(TshTokenType.ARROW, "->", pos))
                }
                c == '@' -> tokens.add(scanTag(pos))
                c == '"' -> tokens.add(scanString(pos))
                c == '(' -> tokens.add(scanSExpression(pos))
                c == '[' -> {
                    if (isInsideHighlights(tokens) && isAstAlternationAhead()) {
                        tokens.add(scanAlternation(pos))
                    } else {
                        advance()
                        tokens.add(TshToken(TshTokenType.LBRACKET, "[", pos))
                    }
                }
                c == ']' -> { advance(); tokens.add(TshToken(TshTokenType.RBRACKET, "]", pos)) }
                isIdentifierStart(c) -> tokens.add(scanIdentifier(pos))
                else -> {
                    tokens.add(TshToken(TshTokenType.ERROR, c.toString(), pos))
                    advance()
                }
            }
        }
        tokens.add(TshToken(TshTokenType.EOF, "", TshSourcePosition(line, column)))
        return tokens
    }

    private fun advance(): Char {
        val c = source[index++]
        if (c == '\n') {
            line++
            column = 1
        } else {
            column++
        }
        return c
    }

    private fun peek(offset: Int = 1): Char {
        val target = index + offset
        return if (target < source.length) source[target] else '\u0000'
    }

    private fun skipWhitespaceAndComments() {
        while (index < source.length) {
            val c = source[index]
            if (c.isWhitespace()) {
                advance()
            } else if (c == '/' && peek() == '/') {
                while (index < source.length && source[index] != '\n') advance()
            } else if (c == ';' && isInsideHighlightsQuickCheck()) {
                while (index < source.length && source[index] != '\n') advance()
            } else if (c == '/' && peek() == '*') {
                advance(); advance()
                while (index < source.length && !(source[index] == '*' && peek() == '/')) advance()
                if (index < source.length) { advance(); advance() }
            } else {
                break
            }
        }
    }

    private fun scanTag(pos: TshSourcePosition): TshToken {
        advance()
        val sb = StringBuilder()
        while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_' || source[index] == '.')) {
            sb.append(advance())
        }
        return TshToken(TshTokenType.TAG, sb.toString(), pos)
    }

    private fun scanString(pos: TshSourcePosition): TshToken {
        advance()
        val sb = StringBuilder()
        var escaped = false
        while (index < source.length) {
            val c = advance()
            if (escaped) {
                sb.append(c)
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '"') {
                break
            } else {
                sb.append(c)
            }
        }
        return TshToken(TshTokenType.STRING, sb.toString(), pos)
    }

    private fun scanSExpression(pos: TshSourcePosition): TshToken {
        val sb = StringBuilder()
        var depth = 0
        while (index < source.length) {
            val c = source[index]
            if (c == '"') {
                sb.append(advance())
                while (index < source.length) {
                    val sc = advance()
                    sb.append(sc)
                    if (sc == '"' && source[index - 2] != '\\') break
                }
                continue
            }
            if (c == '(') depth++
            if (c == ')') depth--
            sb.append(advance())
            if (depth == 0) break
        }
        return TshToken(TshTokenType.RAW_SCM_PATTERN, sb.toString().trim(), pos)
    }

    private fun scanAlternation(pos: TshSourcePosition): TshToken {
        val sb = StringBuilder()
        var depth = 0
        while (index < source.length) {
            val c = source[index]
            if (c == '[') depth++
            if (c == ']') depth--
            sb.append(advance())
            if (depth == 0) break
        }
        return TshToken(TshTokenType.RAW_SCM_PATTERN, sb.toString().trim(), pos)
    }

    private fun scanIdentifier(pos: TshSourcePosition): TshToken {
        val sb = StringBuilder()
        while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_' || source[index] == '-')) {
            sb.append(advance())
        }
        val text = sb.toString()
        val type = when (text) {
            "metadata" -> TshTokenType.METADATA
            "behaviors" -> TshTokenType.BEHAVIORS
            "highlights" -> TshTokenType.HIGHLIGHTS
            else -> TshTokenType.IDENTIFIER
        }
        return TshToken(type, text, pos)
    }

    private fun isInsideHighlights(tokens: List<TshToken>): Boolean =
        tokens.any { it.type == TshTokenType.HIGHLIGHTS }

    private fun isInsideHighlightsQuickCheck(): Boolean =
        source.lastIndexOf("highlights", index) != -1

    private fun isAstAlternationAhead(): Boolean {
        var temp = index + 1
        while (temp < source.length && source[temp].isWhitespace()) temp++
        return temp < source.length && (source[temp] == '(' || source[temp] == '"' || source[temp].isLetter())
    }

    private fun isIdentifierStart(c: Char): Boolean = c.isLetter() || c == '_'
}