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

import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.models.Location
import org.greenrobot.eventbus.EventBus
import java.io.File

/**
 * Clangd 语言客户端 (Language Client)。
 *
 * 功能与用途：
 * 实现了 AndroidIDE 核心的 [ILanguageClient] 接口。它是语言服务器向 IDE 前端推送信息的唯一通道。
 * 主要用途是接收底层 C++ 产生的 Diagnostics（语法报错/警告），并通过 [EventBus] 广播到对应的编辑器 UI 中进行渲染。
 * 
 * 工作流程线路图：
 * [ClangdLanguageServer] -> 解析 C++ 回调后触发 publishDiagnostics
 *        |
 *        v
 * [ClangdLanguageClient.publishDiagnostics]
 *        |
 *        v
 * [EventBus.post] 广播 [ClangdDiagnosticsEvent]
 *        |
 *        v
 * [EditorFragment] (订阅事件) -> 将波浪线渲染到 CodeEditor。
 *
 * 上下文与父类关系：
 * 实现 [ILanguageClient]。生命周期跟随 [ClangdLanguageServer]。
 * 
 * @author android_zero
 */
class ClangdLanguageClient : ILanguageClient {

    /**
     * 接收语言服务器发布的代码诊断结果，并向全局事件总线广播。
     * 
     * @param result 包含文件路径和诊断列表的结果对象。
     */
    override fun publishDiagnostics(result: DiagnosticResult?) {
        if (result == null || result === DiagnosticResult.NO_UPDATE) return
        EventBus.getDefault().post(ClangdDiagnosticsEvent(result))
    }

    override fun getDiagnosticAt(file: File?, line: Int, column: Int): DiagnosticItem? {
        // 在 AndroidIDE 架构中，单点诊断通常由 UI 层的 DiagnosticsContainer 直接处理。
        // Client 层通常不需要实现单点查询，除非有特定需求。
        return null
    }

    override fun performCodeAction(params: PerformCodeActionParams?) {
        // Clangd 自动修复 (Code Action) 预留接口，暂不实现
    }

    override fun showDocument(params: ShowDocumentParams?): ShowDocumentResult {
        // 请求前端打开某个文件并滚动到特定范围，预留接口
        return ShowDocumentResult(false)
    }

    override fun showLocations(locations: MutableList<Location>?) {
        // 请求前端显示多个位置 (如 Find References 的结果)，可发送 EventBus 事件让 UI 弹窗
    }

    override fun showMessage(params: ShowMessageParams?) {
        // 预留：接收来自 Clangd 的 Toast 提示
    }

    override fun logMessage(params: LogMessageParams?) {
        // 预留：接收来自 Clangd 的日志打印
    }
}