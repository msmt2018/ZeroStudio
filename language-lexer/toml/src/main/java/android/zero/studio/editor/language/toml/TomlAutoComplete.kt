package android.zero.studio.editor.language.toml

import android.os.Bundle
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.util.MyCharacter

/**
 * TOML 语言的自动补全器。
 * 
 * <p>该类拦截用户的输入，在用户输入时提示常用的 TOML 块结构（如 `[dependencies]`）和基础值。
 * 
 * @author android_zero
 */
class TomlAutoComplete {

    private val commonKeywords = listOf(
        "true", "false", "inf", "nan"
    )
    
    private val commonCargoTables = listOf(
        "package", "dependencies", "dev-dependencies", "build-dependencies",
        "profile.release", "profile.dev", "features", "workspace", "patch.crates-io"
    )

    /**
     * 执行补全项的计算与装载。
     *
     * @param content 编辑器内容引用
     * @param position 当前光标位置
     * @param publisher 补全列表的发布者
     * @param extraArguments 附带参数
     */
    fun complete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        val prefix = CompletionHelper.computePrefix(content, position) { c ->
            MyCharacter.isJavaIdentifierPart(c) || c == '-' || c == '_'
        }

        if (prefix.isBlank()) return

        publisher.setUpdateThreshold(0)
        val prefixLower = prefix.lowercase()

        // 补全布尔值等基本类型
        for (keyword in commonKeywords) {
            if (keyword.lowercase().startsWith(prefixLower)) {
                publisher.addItem(createItem(keyword, "Value", prefix.length, CompletionItemKind.Value))
            }
        }

        // 补全常用的 Table (如 Rust Cargo.toml 中的常用节点)
        for (table in commonCargoTables) {
            if (table.lowercase().startsWith(prefixLower)) {
                publisher.addItem(createItem(table, "Table", prefix.length, CompletionItemKind.Class))
            }
        }
    }

    private fun createItem(
        label: String,
        desc: String,
        prefixLen: Int,
        kind: CompletionItemKind
    ): SimpleCompletionItem {
        return SimpleCompletionItem(label, desc, prefixLen, label).kind(kind)
    }
}