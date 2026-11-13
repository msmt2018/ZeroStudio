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

import com.itsaky.androidide.lsp.api.AbstractServiceProvider
import com.itsaky.androidide.lsp.api.ICompletionProvider
import com.itsaky.androidide.lsp.api.ILanguageServerRegistry
import com.itsaky.androidide.lsp.models.CompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult
import java.nio.file.Path

/**
 * 针对 C/C++ 语言的代码补全提供者 (Completion Provider)。
 *
 * 功能与用途：
 * 作为 AndroidIDE 编辑器前端与 LSP 后端补全系统的桥梁。每当用户在编辑器中键入代码时，
 * AndroidIDE 的编辑器核心会调度此类，请求匹配的代码提示。
 *
 * 工作流程线路图：
 * [CodeEditor] (检测到输入或主动触发补全)
 *        |
 *        v
 * [ClangdCompletionProvider.complete] (携带行列号及上下文信息的 CompletionParams)
 *        | (校验取消状态 & 文件后缀验证)
 *        v
 * [ILanguageServerRegistry] -> 获取 "clangd-native" 服务器实例
 *        |
 *        v
 * [ClangdLanguageServer.complete] -> (将请求传递至 C++ 处理)
 *        |
 *        v
 * 返回 [CompletionResult] 给编辑器进行 UI 渲染。
 *
 * 上下文与父类关系：
 * 实现了 [ICompletionProvider] 接口以对接补全系统，继承自 [AbstractServiceProvider] 以获取 [IServerSettings] 支持。
 * 
 * @author android_zero
 */
class ClangdCompletionProvider : AbstractServiceProvider(), ICompletionProvider {

    /**
     * 判断当前提供者是否支持补全给定的文件类型。
     *
     * @param file 正在编辑的文件路径
     * @return 如果是 C/C++ 及其头文件则返回 true。
     */
    override fun canComplete(file: Path?): Boolean {
        if (file == null) return false
        if (!super.canComplete(file)) return false
        
        val ext = file.fileName.toString().substringAfterLast('.', "").lowercase()
        return ext in setOf("c", "cpp", "cc", "cxx", "h", "hpp", "hh", "hxx")
    }

    /**
     * 核心补全方法。
     * AndroidIDE 将在后台线程 (非 UI 线程) 调用此方法。
     *
     * @param params 包含当前文件 URI、光标位置、前缀文本以及取消检查器 (CancelChecker)。
     * @return 包含补全项的 [CompletionResult]。如果被取消或不支持，则返回 [CompletionResult.EMPTY]。
     */
    override fun complete(params: CompletionParams): CompletionResult {
        // 1. 及时响应用户的取消操作（例如用户继续快速打字导致之前的请求失效）
        abortCompletionIfCancelled()

        // 2. 验证文件类型
        if (!canComplete(params.file)) {
            return CompletionResult.EMPTY
        }

        // 3. 从全局注册表中寻找我们之前注册的 ClangdLanguageServer
        val server = ILanguageServerRegistry.getDefault().getServer("clangd-native") 
            ?: return CompletionResult.EMPTY

        // 4. 将补全请求委托给 ClangdLanguageServer
        // 注意：此处调用会自动应用我们在 ClangdLanguageServer 中编写的 JNI 轮询逻辑
        return server.complete(params)
    }
}