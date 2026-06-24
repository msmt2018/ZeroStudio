package android.zero.studio.editor.language.toml

import android.os.Bundle
import com.itsaky.androidide.editor.language.IDELanguage
import com.itsaky.androidide.editor.language.utils.CommonSymbolPairs
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch

/**
 * 针对 AndroidIDE/sora-editor 扩展的 TOML 语言支持驱动类。
 * 
 * <p>该类统筹了 TOML 的高亮分析、自动补全、括号自动配对等特性。
 *
 * @author android_zero
 */
class TomlLanguageImpl : IDELanguage() {

    private var analyzer: TomlAnalyzer? = TomlAnalyzer()
    private val completer = TomlAutoComplete()
    private val symbolPairs = CommonSymbolPairs()

    /**
     * 返回语言关联的高亮分析管理器。
     */
    override fun getAnalyzeManager(): AnalyzeManager {
        return analyzer ?: TomlAnalyzer().also { analyzer = it }
    }

    /**
     * 获取语言的编辑中断容忍级别。
     */
    override fun getInterruptionLevel(): Int {
        return INTERRUPTION_LEVEL_STRONG
    }

    /**
     * 提供语言支持的括号、引号自动配对规则。
     */
    override fun getSymbolPairs(): SymbolPairMatch {
        return symbolPairs
    }

    /**
     * 响应编辑器触发的自动补全请求。
     */
    @Throws(CompletionCancelledException::class)
    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        completer.complete(content, position, publisher, extraArguments)
    }

    /**
     * 释放与当前语言绑定的资源。
     */
    override fun destroy() {
        analyzer?.destroy()
        analyzer = null
        super.destroy()
    }
}