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
import com.itsaky.androidide.lsp.models.DiagnosticItem
import com.itsaky.androidide.lsp.models.DiagnosticResult
import com.itsaky.androidide.lsp.models.DiagnosticSeverity
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Paths

/**
 * 接收来自 C++ 底层的 LSP 回调通知，并将其路由到 AndroidIDE 的 [ILanguageClient]。
 * 
 * 功能与用途：
 * 语言服务器通常会有服务端主动向客户端推送消息的行为，例如最重要的 `textDocument/publishDiagnostics` (语法分析报错)。
 * 本单例类负责提供 `@JvmStatic` 方法供 C++/JNI 层调用，并在内部将 JSON 解析为 AndroidIDE 模型，
 * 最后通过关联的 [ILanguageClient.publishDiagnostics] 在 UI 上渲染波浪线。
 *
 * 工作流程线路图：
 * [clangd_server.cpp (检测到代码有报错)] 
 *        |
 *        v
 * [JNI 调用 handleNativeDiagnostics(fileUri, jsonString)]
 *        |
 *        v
 * [ClangdNativeCallback] (反序列化 JSON 到 List<DiagnosticItem>)
 *        |
 *        v
 * [ILanguageClient.publishDiagnostics] (AndroidIDE 框架执行 UI 渲染)
 * 
 * 上下文关系：
 * 这是一个完全的桥接工具对象，和 [ClangdLanguageServer] 绑定生命周期。
 *
 * @author android_zero
 */
object ClangdNativeCallback {

    @Volatile
    private var languageClient: ILanguageClient? = null

    /**
     * 绑定当前的 [ILanguageClient]。由 Server 在 `connectClient` 时调用。
     */
    fun attachClient(client: ILanguageClient?) {
        this.languageClient = client
    }

    fun detachClient() {
        this.languageClient = null
    }

    /**
     * 接收到底层的 Diagnostic 通知 (代码报错/警告)。
     * 注意：C++ 侧需要调用这个方法，并传递 JSON 字符串。
     *
     * @param fileUri 产生诊断信息的源文件 URI。
     * @param jsonStr Diagnostics 的 JSON 数组结构字符串。
     */
    @JvmStatic
    fun handleNativeDiagnostics(fileUri: String, jsonStr: String) {
        val client = languageClient ?: return
        if (fileUri.isEmpty() || jsonStr.isEmpty()) return

        val filePath = if (fileUri.startsWith("file://")) fileUri.substring(7) else fileUri
        val path = Paths.get(filePath)

        val items = mutableListOf<DiagnosticItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val diagObj = jsonArray.optJSONObject(i) ?: continue
                
                val message = diagObj.optString("message", "Unknown error")
                val source = diagObj.optString("source", "clangd")
                val code = diagObj.optString("code", "")
                val severityInt = diagObj.optInt("severity", 1)
                
                val rangeObj = diagObj.optJSONObject("range") ?: continue
                val startObj = rangeObj.optJSONObject("start") ?: continue
                val endObj = rangeObj.optJSONObject("end") ?: continue
                
                val range = Range(
                    Position(startObj.optInt("line", 0), startObj.optInt("character", 0)),
                    Position(endObj.optInt("line", 0), endObj.optInt("character", 0))
                )
                
                val item = DiagnosticItem(
                    message = message,
                    code = code,
                    range = range,
                    source = source,
                    severity = mapDiagnosticSeverity(severityInt),
                    tags = emptyList() // 若需标记如 Unnecessary/Deprecated，可在此解析
                )
                
                items.add(item)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 推送给 AndroidIDE 核心渲染
        client.publishDiagnostics(DiagnosticResult(path, items))
    }

    /**
     * 映射 LSP 中的 Diagnostics 严重等级到 AndroidIDE 内部枚举。
     */
    private fun mapDiagnosticSeverity(severity: Int): DiagnosticSeverity {
        return when (severity) {
            1 -> DiagnosticSeverity.ERROR
            2 -> DiagnosticSeverity.WARNING
            3 -> DiagnosticSeverity.INFO
            4 -> DiagnosticSeverity.HINT
            else -> DiagnosticSeverity.ERROR
        }
    }
}