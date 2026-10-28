package com.zerostudio.idea.treesitter.tsh

/**
 * @author android_zero
 *
 * 变更说明：
 * 验证 Hex 颜色、括号闭合平衡性，防止将非法语法下发导致 C 引擎崩溃。
 */
class TshValidator {

    fun validate(document: TshDocument): List<String> {
        val errors = mutableListOf<String>()

        if (document.metadata.name.isBlank()) {
            errors.add("Metadata 'name' cannot be blank.")
        }

        document.rules.forEach { rule ->
            rule.captureStyles.forEach { (tag, style) ->
                style.colorHex?.let { hex ->
                    if (!isValidHexColor(hex)) {
                        errors.add("Invalid hex color '$hex' for tag @$tag at line ${rule.position.line}")
                    }
                }
            }

            if (!isBalanced(rule.scmPatternWithPredicates)) {
                errors.add("Unbalanced parentheses in SCM pattern at line ${rule.position.line}: ${rule.scmPatternWithPredicates}")
            }
        }

        return errors
    }

    private fun isValidHexColor(hex: String): Boolean {
        if (!hex.startsWith("#")) return false
        val len = hex.length
        return (len == 7 || len == 9) && hex.substring(1).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }

    private fun isBalanced(text: String): Boolean {
        var parens = 0
        var brackets = 0
        for (c in text) {
            when (c) {
                '(' -> parens++
                ')' -> parens--
                '[' -> brackets++
                ']' -> brackets--
            }
            if (parens < 0 || brackets < 0) return false
        }
        return parens == 0 && brackets == 0
    }
}