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
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.api.IServerSettings
import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.models.Location
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.progress.ICancelChecker
import com.itsaky.androidide.projects.IWorkspace
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Clangd 语言服务器核心实现类。
 *
 * 功能与用途：
 * 本类实现了 AndroidIDE 的标准 [ILanguageServer] 接口，作为 AndroidIDE 核心与底层 Clangd C++ 进程的中间件。
 * 负责接收来自 AndroidIDE 编辑器的标准 LSP 请求（如代码补全、悬停提示、定义跳转、文档同步等），
 * 并将其转换为原生调用分发给 [ClangdNativeBridge]，随后轮询获取原生结果，最终反序列化为 AndroidIDE 的标准模型。
 *
 * 工作流程线路图：
 * 1. [AndroidIDE Editor] -> 触发事件 (例如: 用户输入代码)
 * 2. [ILanguageServer.complete] -> 接收带有上下文的 CompletionParams
 * 3. [ClangdLanguageServer] -> 提取行号、列号、文件URI -> 发起 [ClangdNativeBridge.nativeRequestCompletion]
 * 4. [ClangdLanguageServer] -> 阻塞/挂起轮询 [nativeGetResult] 直到结果返回或被 cancelChecker 取消
 * 5. [ClangdLanguageServer] -> 解析 JSON 结果 -> 映射为 [CompletionResult] -> 返回给 [AndroidIDE Editor] 显示
 *
 * 上下文与父类关系：
 * 实现了 [ILanguageServer]。该类的实例需要注册到 AndroidIDE 的 [ILanguageServerRegistry] 中。
 * 内部持有 [ILanguageClient] 的引用，用于主动向前端发送诸如 Diagnostics(代码检查报错) 的通知。
 *
 * @author android_zero
 */
class ClangdLanguageServer : ILanguageServer {

    override val serverId: String = "clangd-native"
    override var client: ILanguageClient? = null
        private set

    private var settings: IServerSettings? = null
    private var workspace: IWorkspace? = null

    // 默认轮询间隔 (毫秒)
    private val POLL_INTERVAL_MS = 25L

    override fun connectClient(client: ILanguageClient?) {
        this.client = client
        // 将当前实例的 Client 绑定给回调处理器，以便接收 C++ 推送的报错信息
        ClangdNativeCallback.attachClient(client)
    }

    override fun applySettings(settings: IServerSettings?) {
        this.settings = settings
    }

    override fun setupWorkspace(workspace: IWorkspace) {
        this.workspace = workspace
        val projectDir = workspace.getProjectDir().absolutePath
        val clangdPath = File(projectDir, "clangd").absolutePath // 假定 clangd 二进制文件路径或根据设置获取
        
        // 注册到回调系统，并初始化 C++ 底层
        ClangdNativeBridge.nativeInitialize(clangdPath, projectDir, 100)
    }

    override fun shutdown() {
        ClangdNativeBridge.nativeShutdown()
        ClangdNativeCallback.detachClient()
    }

    // ========================================================================
    // 文档生命周期同步 (Document Synchronization)
    // ========================================================================

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val uri = params.file.toUri().toString()
        ClangdNativeBridge.nativeDidOpen(uri, params.text, params.languageId)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val uri = params.file.toUri().toString()
        // 通常 didChange 提供增量更新，但如果需要全量，AndroidIDE 提供完整的 text。
        // 这里取出最后一次变更的内容发送给底层
        val content = params.contentChanges.lastOrNull()?.text ?: return
        ClangdNativeBridge.nativeDidChange(uri, content, params.version)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        val uri = params.file.toUri().toString()
        ClangdNativeBridge.nativeDidClose(uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // Clangd 原生层通常依靠 didChange 实时同步 AST，didSave 是可选的，可视情况补充。
    }

    // ========================================================================
    // LSP 请求实现 (LSP Requests)
    // ========================================================================

    /**
     * 阻塞式轮询获取 Native 层返回的 JSON 结果。
     * 由于 AndroidIDE 的 [complete] 和部分格式化方法不是 suspend 的，我们需要使用 Thread.sleep()。
     * 对于 suspend 方法，应使用 [pollResultAsync]。
     */
    private fun pollResultSync(requestId: Long, cancelChecker: ICancelChecker): String? {
        if (requestId == 0L) return null
        while (!cancelChecker.isCancelled) {
            val result = ClangdNativeBridge.nativeGetResult(requestId)
            if (result != null) return result
            Thread.sleep(POLL_INTERVAL_MS)
        }
        // 如果被取消，通知底层取消任务
        ClangdNativeBridge.nativeCancelRequestInternal(requestId)
        return null
    }

    /**
     * 协程挂起式的轮询获取 Native 层返回的 JSON 结果。
     */
    private suspend fun pollResultAsync(requestId: Long, cancelChecker: ICancelChecker): String? {
        if (requestId == 0L) return null
        while (!cancelChecker.isCancelled) {
            val result = ClangdNativeBridge.nativeGetResult(requestId)
            if (result != null) return result
            delay(POLL_INTERVAL_MS)
        }
        ClangdNativeBridge.nativeCancelRequestInternal(requestId)
        return null
    }

    override fun complete(params: CompletionParams?): CompletionResult {
        if (params == null || settings?.completionsEnabled() == false) {
            return CompletionResult.EMPTY
        }

        val fileUri = params.file.toUri().toString()
        // AndroidIDE 提供的 Position 是 0-based
        val requestId = ClangdNativeBridge.nativeRequestCompletion(
            fileUri,
            params.position.line,
            params.position.column,
            null
        )

        val jsonStr = pollResultSync(requestId, params.cancelChecker) ?: return CompletionResult.EMPTY

        return parseCompletionResult(jsonStr, params.requirePrefix())
    }

    override suspend fun findDefinition(params: DefinitionParams): DefinitionResult {
        if (settings?.definitionsEnabled() == false) return DefinitionResult(emptyList())

        val fileUri = params.file.toUri().toString()
        val requestId = ClangdNativeBridge.nativeRequestDefinition(
            fileUri,
            params.position.line,
            params.position.column
        )

        val jsonStr = pollResultAsync(requestId, params.cancelChecker) ?: return DefinitionResult(emptyList())
        return DefinitionResult(parseLocations(jsonStr))
    }

    override suspend fun findReferences(params: ReferenceParams): ReferenceResult {
        if (settings?.referencesEnabled() == false) return ReferenceResult(emptyList())

        val fileUri = params.file.toUri().toString()
        val requestId = ClangdNativeBridge.nativeRequestReferences(
            fileUri,
            params.position.line,
            params.position.column,
            params.includeDeclaration
        )

        val jsonStr = pollResultAsync(requestId, params.cancelChecker) ?: return ReferenceResult(emptyList())
        return ReferenceResult(parseLocations(jsonStr))
    }

    override suspend fun hover(params: DefinitionParams): MarkupContent {
        val fileUri = params.file.toUri().toString()
        val requestId = ClangdNativeBridge.nativeRequestHover(fileUri, params.position.line, params.position.column)
        
        val jsonStr = pollResultAsync(requestId, params.cancelChecker) ?: return MarkupContent()
        return parseHoverResult(jsonStr)
    }

    override suspend fun analyze(file: Path): DiagnosticResult {
        // Clangd 是通过服务端主动推送 (PublishDiagnostics) 到 Client 的，
        // 这里 analyze 方法在 AndroidIDE 中常用于手动触发全量检查。
        // 由于我们在 didChange/didOpen 时底层已经会自动触发，这里可以直接返回缓存或 NO_UPDATE。
        return DiagnosticResult.NO_UPDATE
    }

    // ========================================================================
    // JSON 解析工具 (JSON Parsing to AndroidIDE Models)
    // ========================================================================

    private fun parseCompletionResult(jsonStr: String, prefix: String): CompletionResult {
        try {
            val root = JSONObject(jsonStr)
            if (root.has("error")) return CompletionResult.EMPTY
            val resultObj = root.optJSONObject("result") ?: return CompletionResult.EMPTY
            
            val isIncomplete = resultObj.optBoolean("isIncomplete", false)
            val itemsArray = resultObj.optJSONArray("items") ?: return CompletionResult.EMPTY
            
            val completionResult = CompletionResult()
            completionResult.isIncomplete = isIncomplete
            
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                val label = itemObj.optString("label", "")
                
                // 计算匹配等级 MatchLevel
                val matchLevel = CompletionItem.matchLevel(
                    label, 
                    prefix, 
                    settings?.completionFuzzyMatchMinRatio() ?: CompletionsKt.DEFAULT_MIN_MATCH_RATIO
                )
                
                if (matchLevel == MatchLevel.NO_MATCH) continue
                
                val kindInt = itemObj.optInt("kind", 1)
                val detail = itemObj.optString("detail", "")
                val insertText = itemObj.optJSONObject("textEdit")?.optString("newText", "") 
                                 ?: itemObj.optString("insertText", label)
                
                val doc = itemObj.opt("documentation")
                val docStr = when(doc) {
                    is String -> doc
                    is JSONObject -> doc.optString("value", "")
                    else -> ""
                }
                
                val item = CompletionItem().apply {
                    this.ideLabel = label
                    this.detail = detail
                    // this.documentation = docStr (暂无独立属性，可通过 data 承载，AndroidIDE 会通过额外提供器查询)
                    this.insertText = insertText
                    this.completionKind = mapCompletionKind(kindInt)
                    this.matchLevel = matchLevel
                    this.ideSortText = itemObj.optString("sortText", label)
                    
                    // 如果 insertText 是 Snippet 格式 (如使用 $1, ${2:foo})
                    val formatInt = itemObj.optInt("insertTextFormat", 1)
                    if (formatInt == 2) {
                        this.insertTextFormat = InsertTextFormat.SNIPPET
                    } else {
                        this.insertTextFormat = InsertTextFormat.PLAIN_TEXT
                    }
                }
                completionResult.add(item)
            }
            return completionResult
        } catch (e: Exception) {
            e.printStackTrace()
            return CompletionResult.EMPTY
        }
    }

    private fun parseLocations(jsonStr: String): List<Location> {
        val locations = mutableListOf<Location>()
        try {
            val root = JSONObject(jsonStr)
            if (root.has("error")) return locations
            val result = root.opt("result") ?: return locations
            
            if (result is JSONArray) {
                for (i in 0 until result.length()) {
                    parseLocation(result.optJSONObject(i))?.let { locations.add(it) }
                }
            } else if (result is JSONObject) {
                parseLocation(result)?.let { locations.add(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return locations
    }

    private fun parseLocation(obj: JSONObject?): Location? {
        if (obj == null) return null
        val uri = obj.optString("uri", "")
        if (uri.isEmpty()) return null
        
        val filePath = if (uri.startsWith("file://")) uri.substring(7) else uri
        val rangeObj = obj.optJSONObject("range") ?: return null
        val startObj = rangeObj.optJSONObject("start") ?: return null
        val endObj = rangeObj.optJSONObject("end") ?: return null
        
        // Location(path, line, column) in AndroidIDE usually just expects a start location or Range.
        // By standard, AndroidIDE Location class uses path, startLine, startCharacter, endLine, endCharacter.
        return Location(
            File(filePath), // AndroidIDE Location uses File
            startObj.optInt("line", 0),
            startObj.optInt("character", 0),
            endObj.optInt("line", 0),
            endObj.optInt("character", 0)
        )
    }

    private fun parseHoverResult(jsonStr: String): MarkupContent {
        try {
            val root = JSONObject(jsonStr)
            val resultObj = root.optJSONObject("result") ?: return MarkupContent()
            val contents = resultObj.opt("contents")
            
            val value = when(contents) {
                is String -> contents
                is JSONObject -> contents.optString("value", "")
                is JSONArray -> {
                    val sb = StringBuilder()
                    for (i in 0 until contents.length()) {
                        val item = contents.opt(i)
                        if (item is String) sb.append(item).append("\n")
                        else if (item is JSONObject) sb.append(item.optString("value", "")).append("\n")
                    }
                    sb.toString().trim()
                }
                else -> ""
            }
            
            return MarkupContent(value, MarkupKind.MARKDOWN)
        } catch (e: Exception) {
            e.printStackTrace()
            return MarkupContent()
        }
    }

    /**
     * 映射 Clangd 补全类型至 AndroidIDE 的标准 CompletionItemKind。
     */
    private fun mapCompletionKind(kind: Int): CompletionItemKind {
        return when (kind) {
            1 -> CompletionItemKind.TEXT
            2 -> CompletionItemKind.METHOD
            3 -> CompletionItemKind.FUNCTION
            4 -> CompletionItemKind.CONSTRUCTOR
            5 -> CompletionItemKind.FIELD
            6 -> CompletionItemKind.VARIABLE
            7 -> CompletionItemKind.CLASS
            8 -> CompletionItemKind.INTERFACE
            9 -> CompletionItemKind.MODULE
            10 -> CompletionItemKind.PROPERTY
            11 -> CompletionItemKind.UNIT
            12 -> CompletionItemKind.VALUE
            13 -> CompletionItemKind.ENUM
            14 -> CompletionItemKind.KEYWORD
            15 -> CompletionItemKind.SNIPPET
            16 -> CompletionItemKind.COLOR
            17 -> CompletionItemKind.FILE
            18 -> CompletionItemKind.REFERENCE
            19 -> CompletionItemKind.ENUM_MEMBER
            20 -> CompletionItemKind.CONSTANT
            21 -> CompletionItemKind.STRUCT
            22 -> CompletionItemKind.EVENT
            23 -> CompletionItemKind.OPERATOR
            24 -> CompletionItemKind.TYPE_PARAMETER
            else -> CompletionItemKind.NONE
        }
    }

    // ========================================================================
    // Not implemented / Optional features from ILanguageServer
    // ========================================================================
    override suspend fun expandSelection(params: ExpandSelectionParams): Range = Range.NONE
    override suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp = SignatureHelp(emptyList(), 0, 0)
}