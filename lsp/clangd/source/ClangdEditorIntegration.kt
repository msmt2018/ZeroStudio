/*
 * This file is part of ZeroStudio.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 *
 * License Information:
 *
 * This file is distributed under the following licenses, which apply
 * cumulatively to the same work:
 *
 * 1. GNU Affero General Public License v3.0 only (AGPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    When a modified version is used to provide network services to users,
 *    the corresponding source code must be made available under the
 *    applicable AGPL terms.
 *
 * 2. GNU General Public License v3.0 only (GPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    Distribution of covered binaries requires compliance with the source
 *    code, license notice, attribution, and other applicable GPL terms.
 *
 * 3. Apache License 2.0 (Apache-2.0):
 *    Grants copyright and applicable patent rights to use, modify, and
 *    distribute this work, subject to its license conditions, including
 *    preservation of notices, inclusion of the license, and marking changes.
 *
 * The SPDX operator "AND" means that all listed license terms apply
 * cumulatively. It does not mean that recipients may choose only one
 * of the listed licenses.
 *
 * You must comply with the applicable terms of all three licenses.
 * This notice is a summary and does not replace the full license texts.
 *
 * Full license texts:
 * - GNU AGPL v3.0: https://www.gnu.org/licenses/agpl-3.0.html
 * - GNU GPL v3.0:  https://www.gnu.org/licenses/gpl-3.0.html
 * - Apache 2.0:    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Author: android_zero
 * GitHub: https://github.com/msmt2018
 * WeChat: android_zero
 */


package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.api.ILanguageServerRegistry
import com.itsaky.androidide.lsp.models.DidChangeTextDocumentParams
import com.itsaky.androidide.lsp.models.DidCloseTextDocumentParams
import com.itsaky.androidide.lsp.models.DidOpenTextDocumentParams
import com.itsaky.androidide.lsp.models.TextDocumentContentChangeEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.subscribeEvent
import java.nio.file.Path

/**
 * 针对 CodeEditor 的 Clangd 集成扩展工具类。
 *
 * 功能与用途：
 * 这是联系前端 UI (CodeEditor) 和 LSP 后端 (ClangdLanguageServer) 的纽带。
 * 负责在编辑器打开文件时发送 `didOpen`，文本修改时防抖发送 `didChange`，关闭时发送 `didClose`。
 * 
 * 工作流程线路图：
 * [EditorFragment] -> `editor.bindClangdLsp(filePath)`
 *        |
 *        v
 * [ClangdEditorIntegration] -> 初始化订阅，向 Server 发送 `didOpen`
 *        | -> [SoraEditor 事件回调] -> 向 Server 发送 `didChange`
 *
 * @author android_zero
 */
object ClangdEditorIntegration {

    /**
     * 将给定的 [CodeEditor] 与 Clangd 语言服务器绑定，建立文档同步机制。
     *
     * @param editor 目标代码编辑器
     * @param file 当前编辑的源文件路径
     * @return 返回用于取消事件订阅的 Receipt，在 Fragment 销毁时需调用 unsubscribe()。
     */
    @JvmStatic
    fun bindClangdLsp(editor: CodeEditor, file: Path): SubscriptionReceipt<ContentChangeEvent>? {
        val server = ILanguageServerRegistry.getDefault().getServer("clangd-native") 
            ?: return null

        // 1. 文档打开：同步初始全部内容
        val initialContent = editor.text.toString()
        val languageId = if (file.fileName.toString().endsWith(".c")) "c" else "cpp"
        server.didOpen(
            DidOpenTextDocumentParams(
                file = file,
                languageId = languageId,
                version = 1,
                text = initialContent
            )
        )

        var documentVersion = 1

        // 2. 订阅文本改变事件：发生变化时同步内容给 Server
        // 注意：LSP 支持增量同步，但为确保状态绝对一致且性能允许，可采用全量同步。此处采用全量同步。
        val receipt = editor.subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
            // 如果是由重做/撤销等引发的微小变化，也可在此做过滤防抖
            documentVersion++
            
            // 构建全量更新的 Event
            val changeEvent = TextDocumentContentChangeEvent(
                range = null,
                rangeLength = null,
                text = editor.text.toString()
            )
            
            server.didChange(
                DidChangeTextDocumentParams(
                    file = file,
                    version = documentVersion,
                    contentChanges = listOf(changeEvent)
                )
            )
        }

        return receipt
    }

    /**
     * 断开 CodeEditor 与 Clangd 语言服务器的绑定。
     *
     * @param file 正在关闭的文件路径
     */
    @JvmStatic
    fun unbindClangdLsp(file: Path) {
        val server = ILanguageServerRegistry.getDefault().getServer("clangd-native") ?: return
        server.didClose(DidCloseTextDocumentParams(file))
    }
}