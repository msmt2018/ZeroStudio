package com.zerostudio.idea.editor.core.highlight

import android.content.Context
import com.zerostudio.idea.editor.core.config.EditorPreferencesState
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 语言语法定义元数据
 */
data class LanguageDefinition(
    val languageId: String,
    val extensions: List<String>,
    val textMateScope: String,
    val fastKeywords: Set<String>
)

object ZeroSyntaxHighlighterRegistry {

    private val supportedLanguages = listOf(
        LanguageDefinition("kotlin", listOf("kt", "kts"), "source.kotlin", setOf("fun", "val", "var", "class", "package", "import", "return", "if", "else")),
        LanguageDefinition("java", listOf("java"), "source.java", setOf("public", "private", "protected", "class", "void", "return", "new", "static")),
        LanguageDefinition("python", listOf("py", "pyw"), "source.python", setOf("def", "class", "import", "from", "return", "if", "elif", "else")),
        LanguageDefinition("rust", listOf("rs"), "source.rust", setOf("fn", "let", "mut", "struct", "impl", "trait", "pub", "use")),
        LanguageDefinition("cpp", listOf("cpp", "cxx", "cc", "h", "hpp"), "source.cpp", setOf("int", "void", "class", "template", "typename", "return")),
        LanguageDefinition("javascript", listOf("js", "mjs"), "source.js", setOf("function", "const", "let", "var", "return", "export", "import")),
        LanguageDefinition("json", listOf("json"), "source.json", emptySet())
    )

    private var initialized = false

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.assets))
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        initialized = true
    }

    /**
     * 根据文件名与流畅模式状态分配最优渲染引擎
     */
    fun resolveLanguage(
        fileName: String,
        isFluencyMode: Boolean,
        prefs: EditorPreferencesState
    ): Language {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        val definition = supportedLanguages.find { it.extensions.contains(ext) } ?: return EmptyLanguage()

        return if (isFluencyMode) {
            // 流畅模式：挂载 O(N) 极速分词器，杜绝正则回溯停顿
            FastStatelessLanguage(definition.fastKeywords)
        } else {
            // 标准模式：挂载 TextMate 深度着色引擎
            runCatching {
                TextMateLanguage.create(definition.textMateScope, prefs.showWhitespaceCharacters)
            }.getOrElse { EmptyLanguage() }
        }
    }
}

/**
 * 流畅模式下的极速词法解析器
 */
private class FastStatelessLanguage(private val keywords: Set<String>) : EmptyLanguage() {
    override fun getAnalyzeManager(): AnalyzeManager {
        return object : SimpleAnalyzeManager<Unit>() {
            override fun analyze(text: StringBuilder, delegate: Delegate<Unit>): Styles {
                val styles = Styles()
                val spans = mutableListOf<Span>()
                var i = 0
                val len = text.length

                while (i < len) {
                    if (delegate.isCancelled) return styles
                    val ch = text[i]

                    if (ch.isLetter()) {
                        val start = i
                        while (i < len && text[i].isLetterOrDigit()) {
                            i++
                        }
                        val word = text.substring(start, i)
                        if (keywords.contains(word)) {
                            spans.add(SpanFactory.obtain(start, EditorColorScheme.KEYWORD.toLong()))
                            spans.add(SpanFactory.obtain(i, EditorColorScheme.TEXT_NORMAL.toLong()))
                        }
                        continue
                    }
                    i++
                }
                styles.finishBuilding()
                return styles
            }
        }
    }
}