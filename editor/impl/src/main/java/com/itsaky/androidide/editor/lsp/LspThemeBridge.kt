// FILE: editor/impl/src/main/java/com/itsaky/androidide/editor/lsp/LspThemeBridge.kt
/*
 *  This file is part of AndroidIDE.
 *  @author android_zero
 */

package com.itsaky.androidide.editor.lsp

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

object LspThemeBridge {
    fun getStyleIdForType(typeName: String): Int {
        return when (typeName) {
            "type", "class", "interface", "enum", "struct" -> EditorColorScheme.IDENTIFIER_NAME
            "parameter" -> EditorColorScheme.IDENTIFIER_VAR
            "variable" -> EditorColorScheme.IDENTIFIER_VAR
            "property", "enumMember" -> EditorColorScheme.IDENTIFIER_NAME
            "function", "method" -> EditorColorScheme.FUNCTION_NAME
            "keyword" -> EditorColorScheme.KEYWORD
            "comment" -> EditorColorScheme.COMMENT
            "string", "number" -> EditorColorScheme.LITERAL
            "operator" -> EditorColorScheme.OPERATOR
            "macro" -> EditorColorScheme.ANNOTATION
            else -> EditorColorScheme.TEXT_NORMAL
        }
    }
}
