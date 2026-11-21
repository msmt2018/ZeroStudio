/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.lsp.kotlin

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.ActionsRegistry
import com.itsaky.androidide.actions.locations.CodeActionsMenu
import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.api.IServerSettings
import com.itsaky.androidide.lsp.kotlin.actions.KotlinLspActionsProvider
import com.itsaky.androidide.lsp.kotlin.events.KotlinTextDocumentSyncHandler
import com.itsaky.androidide.lsp.models.CodeFormatResult
import com.itsaky.androidide.lsp.models.CompletionItemKind
import com.itsaky.androidide.lsp.models.CompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult
import com.itsaky.androidide.lsp.models.DefinitionParams
import com.itsaky.androidide.lsp.models.DefinitionResult
import com.itsaky.androidide.lsp.models.DiagnosticItem
import com.itsaky.androidide.lsp.models.DiagnosticResult
import com.itsaky.androidide.lsp.models.DiagnosticSeverity
import com.itsaky.androidide.lsp.models.DidChangeTextDocumentParams
import com.itsaky.androidide.lsp.models.DidCloseTextDocumentParams
import com.itsaky.androidide.lsp.models.DidOpenTextDocumentParams
import com.itsaky.androidide.lsp.models.DidSaveTextDocumentParams
import com.itsaky.androidide.lsp.models.DocumentChange
import com.itsaky.androidide.lsp.models.ExecuteCommandOptions
import com.itsaky.androidide.lsp.models.ExecuteCommandParams as IdeExecuteCommandParams
import com.itsaky.androidide.lsp.models.ExpandSelectionParams
import com.itsaky.androidide.lsp.models.FormatCodeParams
import com.itsaky.androidide.lsp.models.MarkupContent
import com.itsaky.androidide.lsp.models.MarkupKind
import com.itsaky.androidide.lsp.models.MatchLevel
import com.itsaky.androidide.lsp.models.MessageType
import com.itsaky.androidide.lsp.models.ReferenceParams
import com.itsaky.androidide.lsp.models.ReferenceResult
import com.itsaky.androidide.lsp.models.RenameParams
import com.itsaky.androidide.lsp.models.ShowMessageParams
import com.itsaky.androidide.lsp.models.SignatureHelp
import com.itsaky.androidide.lsp.models.SignatureHelpParams
import com.itsaky.androidide.lsp.models.TextEdit
import com.itsaky.androidide.lsp.models.WorkspaceEdit
import com.itsaky.androidide.lsp.util.LSPEditorActions
import com.itsaky.androidide.models.Location
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.preferences.internal.EditorPreferences
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.IWorkspace
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CompletionCapabilities
import org.eclipse.lsp4j.CompletionItemCapabilities
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.ExecuteCommandCapabilities
import org.eclipse.lsp4j.ExecuteCommandParams as LspExecuteCommandParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.HoverCapabilities
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.SignatureHelpCapabilities
import org.eclipse.lsp4j.SignatureHelpParams as LspSignatureHelpParams
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 核心重构：基于 Eclipse lsp4j 标准协议栈的 Kotlin Language Server 代理层。
 *
 * @author android_zero
 */
class KotlinLanguageServerImpl(
    private val process: Process
) : ILanguageServer {
    private lateinit var lspServer: LanguageServer

    override val serverId: String
        get() = SERVER_ID

    override var client: ILanguageClient? = null

    @Volatile
    private var isInitialized = false
    @Volatile
    private var lastOpenedFile: Path? = null
    @Volatile
    private var lastInitError: String? = null
    private val gson = Gson()
    private val completionConverter = KotlinCompletionConverter()
    private val initLock = Any()

    @Volatile
    private var remoteExecuteCommandOptions = ExecuteCommandOptions(KNOWN_WORKSPACE_COMMANDS)

    companion object {
        const val SERVER_ID = "kotlin-lsp"
        private val log = Logger.instance("KotlinLanguageServerImpl")

        private val KNOWN_WORKSPACE_COMMANDS =
            listOf(
                "convertJavaToKotlin",
                "kotlin/jarClassContents",
                "kotlinRefreshBazelClassPath",
                "kotestTestsInfo",
                "textDocument/codeAction",
            )
    }

    /**
     * 内置标准 lsp4j 客户端端点，接收 KLS 的反向调用
     */
    inner class ClientEndpoint : LanguageClient {
        override fun telemetryEvent(obj: Any) {}

        override fun publishDiagnostics(params: PublishDiagnosticsParams) {
            try {
                val uriStr = params.uri
                val path = File(URI(uriStr)).toPath()
                val ideDiagnostics = params.diagnostics.map { it.toIde(uriStr) }
                client?.publishDiagnostics(DiagnosticResult(path, ideDiagnostics))
            } catch (e: Exception) {
                log.error("Failed to parse and publish diagnostics", e)
            }
        }

        override fun showMessage(params: MessageParams) {
            val type = when (params.type) {
                org.eclipse.lsp4j.MessageType.Error -> MessageType.Error
                org.eclipse.lsp4j.MessageType.Warning -> MessageType.Warning
                org.eclipse.lsp4j.MessageType.Info -> MessageType.Info
                else -> MessageType.Log
            }
            client?.showMessage(ShowMessageParams(type, params.message))
        }

        override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem> {
            return CompletableFuture.completedFuture(null)
        }

        override fun logMessage(message: MessageParams) {
            when (message.type) {
                org.eclipse.lsp4j.MessageType.Error -> log.error(message.message)
                org.eclipse.lsp4j.MessageType.Warning -> log.warn(message.message)
                else -> log.debug(message.message)
            }
        }

        override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> {
            return try {
                val edit = params.edit.toIdeWorkspaceEdit()
                val success = client?.applyWorkspaceEdit(edit) ?: false
                CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(success))
            } catch (e: Exception) {
                log.error("Failed to apply workspace edit", e)
                CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(false))
            }
        }
    }

    val clientEndpoint = ClientEndpoint()

    fun bindRemoteServer(remoteServer: LanguageServer) {
        this.lspServer = remoteServer
    }

    fun isReady(): Boolean = isInitialized

    fun getLastInitError(): String? = lastInitError

    private fun requireServer(): LanguageServer {
        check(::lspServer.isInitialized) { "Kotlin LSP remote server is not bound yet." }
        return lspServer
    }

    override fun connectClient(client: ILanguageClient?) {
        this.client = client
    }

    override fun applySettings(settings: IServerSettings?) {
        if (settings != null && isInitialized) {
            val configParams = DidChangeConfigurationParams(
                mapOf(
                    "kotlin" to mapOf(
                        "diagnostics" to mapOf("enabled" to settings.diagnosticsEnabled()),
                        "formatting" to mapOf(
                            "formatter" to "ktfmt",
                            "ktfmt" to mapOf(
                                "style" to "google",
                                "indent" to EditorPreferences.tabSize
                            )
                        )
                    )
                )
            )
            requireServer().workspaceService.didChangeConfiguration(configParams)
        }
    }

    override fun setupWorkspace(workspace: IWorkspace) {
        ensureActionsMenuRegisteredWithRetry()

        val bridge = KotlinJavaCompilerBridge(workspace)
        completionConverter.setJavaCompilerBridge(bridge)
        ensureInitialized(workspace.getProjectDir())
    }

    private fun ensureInitialized(projectDir: File? = resolveInitializationRoot()): Boolean {
        if (isInitialized) return true
        val rootDir = projectDir ?: return false
        synchronized(initLock) {
            if (isInitialized) return true
            initializeServer(rootDir)
            return isInitialized
        }
    }

    private fun resolveInitializationRoot(file: Path? = null): File? {
        // 治本：projectDir 改 nullable 后，可以直接用 safe-call 链，
        // 不再需要 runCatching 包裹（commit e935009 的"快速止血"修复遗留）
        val managerProjectDir = IProjectManager.getInstance().projectDir
        if (managerProjectDir != null && managerProjectDir.exists()) {
            return managerProjectDir
        }

        val anchor = file ?: lastOpenedFile
        val fromOpenedFile = anchor?.toAbsolutePath()?.parent?.toFile()
        if (fromOpenedFile != null && fromOpenedFile.exists()) {
            return fromOpenedFile
        }

        return null
    }

    private fun initializeServer(projectDir: File) {
        val rootUri = projectDir.toURI().toString()
        val cacheDir = Environment.getProjectCacheDir(projectDir)

        val initParams = InitializeParams().apply {
            processId = android.os.Process.myPid()
            this.rootUri = rootUri
            workspaceFolders = listOf(WorkspaceFolder(rootUri, projectDir.name))

            capabilities = ClientCapabilities().apply {
                textDocument = TextDocumentClientCapabilities().apply {
                    completion = CompletionCapabilities(CompletionItemCapabilities(true))
                    hover = HoverCapabilities()
                    signatureHelp = SignatureHelpCapabilities()
                }
                this.workspace = WorkspaceClientCapabilities().apply {
                    applyEdit = true
                    executeCommand = ExecuteCommandCapabilities(true)
                }
            }

            initializationOptions = mapOf(
                "storagePath" to cacheDir.absolutePath,
                "lazyCompilation" to false,
                "jvmConfiguration" to mapOf("target" to "17")
            )
        }

        runBlocking {
            try {
                val initializeResult = requireServer().initialize(initParams).await()
                remoteExecuteCommandOptions =
                    initializeResult?.capabilities?.executeCommandProvider.toIdeExecuteCommandOptions()
                requireServer().initialized(InitializedParams())
                isInitialized = true
                lastInitError = null
                KotlinTextDocumentSyncHandler.onServerReady()
                log.info("LSP4J Kotlin Server Initialized Successfully for ${projectDir.name}.")
            } catch (e: Exception) {
                isInitialized = false
                lastInitError = e.message
                log.error("Failed to initialize Kotlin LSP via LSP4J", e)
            }
        }
    }

    private fun ensureActionsMenuRegisteredWithRetry() {
        val provider = KotlinLspActionsProvider()
        runBlocking {
            repeat(20) {
                val menu = ActionsRegistry.getInstance().findAction(ActionItem.Location.EDITOR_TEXT_ACTIONS, CodeActionsMenu.ID)
                if (menu != null) {
                    LSPEditorActions.ensureActionsMenuRegistered(provider)
                    return@runBlocking
                }
                delay(150)
            }
            LSPEditorActions.ensureActionsMenuRegistered(provider)
        }
    }

    // --- 文档生命周期同步 (Text Document Sync) ---

    override fun didOpen(params: DidOpenTextDocumentParams) {
        lastOpenedFile = params.file
        if (!ensureInitialized(resolveInitializationRoot(params.file))) return
        val item = TextDocumentItem(params.file.toUri().toString(), params.languageId, params.version, params.text)
        requireServer().textDocumentService.didOpen(org.eclipse.lsp4j.DidOpenTextDocumentParams(item))
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        lastOpenedFile = params.file
        if (!ensureInitialized(resolveInitializationRoot(params.file))) return
        val changes = params.contentChanges.map { 
            // 简化为全量更新以避免增量 range 计算的潜在偏移问题
            org.eclipse.lsp4j.TextDocumentContentChangeEvent(it.text) 
        }
        val id = VersionedTextDocumentIdentifier(params.file.toUri().toString(), params.version)
        requireServer().textDocumentService.didChange(org.eclipse.lsp4j.DidChangeTextDocumentParams(id, changes))
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        if (!ensureInitialized(resolveInitializationRoot(params.file))) return
        requireServer().textDocumentService.didClose(org.eclipse.lsp4j.DidCloseTextDocumentParams(TextDocumentIdentifier(params.file.toUri().toString())))
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        if (!ensureInitialized(resolveInitializationRoot(params.file))) return
        requireServer().textDocumentService.didSave(org.eclipse.lsp4j.DidSaveTextDocumentParams(TextDocumentIdentifier(params.file.toUri().toString()), params.text))
    }

    // --- 核心特性实现 ---

    override fun complete(params: CompletionParams?): CompletionResult {
        if (params == null || !ensureInitialized(resolveInitializationRoot(params.file))) {
            return CompletionResult.EMPTY
        }
        
        return runBlocking(Dispatchers.IO) {
            try {
                val lspParams = org.eclipse.lsp4j.CompletionParams(
                    TextDocumentIdentifier(params.file.toUri().toString()),
                    params.position.toLsp4j()
                )
                
                val response = requireServer().textDocumentService.completion(lspParams).await()
                val items = if (response.isLeft) response.left else response.right.items
                
                // 将 LSP4J 的对象转换为 JsonArray，以复用我们强大的 KotlinCompletionConverter 增强逻辑
                val itemsJsonArray = gson.toJsonTree(items).asJsonArray
                
                val contentStr = params.content?.toString() ?: ""
                val prefix = extractPrefix(contentStr, params.position)
                
                val enhancedItems = completionConverter.convertWithClasspathEnhancement(items, contentStr, prefix)

                enhancedItems.forEach { item ->
                    item.completionKind = item.completionKind ?: CompletionItemKind.NONE
                    applyCompletionRanking(item, prefix)
                }

                CompletionResult(enhancedItems)
            } catch (e: Exception) {
                log.error("Completion resolution failed", e)
                CompletionResult.EMPTY
            }
        }
    }

    private fun extractPrefix(contentStr: String, pos: Position): String {
        val lines = contentStr.split("\n")
        if (pos.line >= lines.size) return ""
        val line = lines[pos.line]
        val col = pos.column.coerceAtMost(line.length)
        var start = col
        while (start > 0 && (line[start - 1].isLetterOrDigit() || line[start - 1] == '_')) {
            start--
        }
        return line.substring(start, col)
    }

    private fun applyCompletionRanking(
        item: com.itsaky.androidide.lsp.models.CompletionItem,
        prefix: String
    ) {
        if (prefix.isBlank()) {
            item.matchLevel = MatchLevel.PARTIAL_MATCH
            return
        }

        val normalizedPrefix = prefix.lowercase()
        val label = item.ideLabel
        val detail = item.detail
        val lowerLabel = label.lowercase()
        val lowerDetail = detail.lowercase()

        val labelMatch = com.itsaky.androidide.lsp.models.CompletionItem.matchLevel(label, prefix)
        val detailMatch = com.itsaky.androidide.lsp.models.CompletionItem.matchLevel(detail, prefix)
        item.matchLevel = if (labelMatch != MatchLevel.NO_MATCH) labelMatch else detailMatch

        val labelStarts = lowerLabel.startsWith(normalizedPrefix)
        val labelContains = !labelStarts && lowerLabel.contains(normalizedPrefix)
        val detailStarts = !labelStarts && lowerDetail.startsWith(normalizedPrefix)
        val detailContains = !labelStarts && !detailStarts && lowerDetail.contains(normalizedPrefix)

        val kindPenalty =
            when (item.completionKind) {
                CompletionItemKind.METHOD,
                CompletionItemKind.FUNCTION,
                CompletionItemKind.PROPERTY,
                CompletionItemKind.FIELD,
                CompletionItemKind.VARIABLE -> 0
                CompletionItemKind.CLASS, CompletionItemKind.INTERFACE, CompletionItemKind.ENUM -> 2
                else -> 1
            }

        val matchBucket =
            when {
                labelStarts -> 0
                labelContains -> 1
                detailStarts -> 2
                detailContains -> 3
                else -> 4
            }

        val existingSort = item.ideSortText ?: item.ideLabel
        item.ideSortText = "%d%d_%s".format(matchBucket, kindPenalty, existingSort)
    }

    override suspend fun findDefinition(params: DefinitionParams): DefinitionResult = withContext(Dispatchers.IO) {
        if (!ensureInitialized(resolveInitializationRoot(params.file))) return@withContext DefinitionResult(emptyList())
        try {
            val req = org.eclipse.lsp4j.DefinitionParams(
                TextDocumentIdentifier(params.file.toUri().toString()),
                params.position.toLsp4j()
            )
            val result = requireServer().textDocumentService.definition(req).await()
            val locations = result.left?.map { 
                Location(File(java.net.URI(it.uri)).toPath(), it.range.toIde())
            } ?: emptyList()
            DefinitionResult(locations)
        } catch (e: Exception) {
            DefinitionResult(emptyList())
        }
    }

    override suspend fun findReferences(params: ReferenceParams): ReferenceResult = withContext(Dispatchers.IO) {
        if (!ensureInitialized(resolveInitializationRoot(params.file))) return@withContext ReferenceResult(emptyList())
        try {
            val req = org.eclipse.lsp4j.ReferenceParams(
                ReferenceContext(params.includeDeclaration)
            ).apply {
                this.textDocument = TextDocumentIdentifier(params.file.toUri().toString())
                this.position = params.position.toLsp4j()
            }
            val result = requireServer().textDocumentService.references(req).await()
            val locations = result?.map { 
                Location(File(java.net.URI(it.uri)).toPath(), it.range.toIde())
            } ?: emptyList()
            ReferenceResult(locations)
        } catch (e: Exception) {
            ReferenceResult(emptyList())
        }
    }

    override suspend fun hover(params: DefinitionParams): MarkupContent = withContext(Dispatchers.IO) {
        if (!ensureInitialized(resolveInitializationRoot(params.file))) return@withContext MarkupContent("", MarkupKind.PLAIN)
        try {
            val req = org.eclipse.lsp4j.HoverParams(
                TextDocumentIdentifier(params.file.toUri().toString()),
                params.position.toLsp4j()
            )
            val result = requireServer().textDocumentService.hover(req).await()
            val content = result?.contents?.left?.firstOrNull()?.left ?: result?.contents?.right?.value ?: ""
            MarkupContent(content, MarkupKind.MARKDOWN)
        } catch (e: Exception) {
            MarkupContent("", MarkupKind.PLAIN)
        }
    }

    override suspend fun analyze(file: Path): DiagnosticResult = withContext(Dispatchers.IO) {
        // LSP4J 协议下，诊断信息是由 Server 主动通过 publishDiagnostics 推送的，所以这里直接返回 NO_UPDATE
        DiagnosticResult.NO_UPDATE
    }

    override fun formatCode(params: FormatCodeParams?): CodeFormatResult {
        if (params == null || !ensureInitialized()) return CodeFormatResult.NONE
        return runBlocking(Dispatchers.IO) {
            try {
                // Formatting 的 Uri 理论上需要真实的 Uri，这里为了兼容现有行为先这样处理
                val req = DocumentFormattingParams(
                    TextDocumentIdentifier("file://dummy_for_format"),
                    FormattingOptions(EditorPreferences.tabSize, !EditorPreferences.useSoftTab)
                )
                val edits = requireServer().textDocumentService.formatting(req).await()
                val ideEdits = edits?.map { it.toIde() }?.toMutableList() ?: mutableListOf()
                CodeFormatResult(false, ideEdits, mutableListOf())
            } catch (e: Exception) {
                CodeFormatResult.NONE
            }
        }
    }

    override suspend fun expandSelection(params: ExpandSelectionParams): Range = params.selection

    override suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp = withContext(Dispatchers.IO) {
        if (!ensureInitialized(resolveInitializationRoot(params.file))) return@withContext SignatureHelp(emptyList(), 0, 0)
        try {
            val req = LspSignatureHelpParams(
                TextDocumentIdentifier(params.file.toUri().toString()),
                params.position.toLsp4j()
            )
            val result = requireServer().textDocumentService.signatureHelp(req).await()
            if (result == null) {
                return@withContext SignatureHelp(emptyList(), 0, 0)
            }

            val signatures = result.signatures?.map { sig ->
                com.itsaky.androidide.lsp.models.SignatureInformation(
                    label = sig.label ?: "",
                    documentation = sig.documentation.toIdeMarkup(),
                    parameters = sig.parameters?.map { param ->
                        val label = param.label ?: ""
                        com.itsaky.androidide.lsp.models.ParameterInformation(
                            label = label,
                            documentation = param.documentation.toIdeMarkup(),
                        )
                    } ?: emptyList()
                )
            } ?: emptyList()

            SignatureHelp(
                signatures = signatures,
                activeSignature = result.activeSignature ?: 0,
                activeParameter = result.activeParameter ?: 0,
            )
        } catch (e: Exception) {
            log.error("SignatureHelp request failed", e)
            SignatureHelp(emptyList(), 0, 0)
        }
    }

    override suspend fun rename(params: RenameParams): WorkspaceEdit = withContext(Dispatchers.IO) {
        if (!ensureInitialized(resolveInitializationRoot(params.file))) return@withContext WorkspaceEdit()
        try {
            val req = org.eclipse.lsp4j.RenameParams(
                TextDocumentIdentifier(params.file.toUri().toString()),
                params.position.toLsp4j(),
                params.newName
            )
            val result = requireServer().textDocumentService.rename(req).await()
            result.toIdeWorkspaceEdit()
        } catch (e: Exception) {
            WorkspaceEdit()
        }
    }

    override fun executeCommandOptions(): ExecuteCommandOptions {
        return remoteExecuteCommandOptions.copy(commands = remoteExecuteCommandOptions.commands.distinct())
    }

    override fun canExecuteCommand(command: String): Boolean {
        return executeCommandOptions().commands.contains(command)
    }

    override suspend fun executeCommand(params: IdeExecuteCommandParams): Any? = withContext(Dispatchers.IO) {
        val result = executeRemoteWorkspaceCommand(
            commandName = params.command,
            arguments = params.arguments.orEmpty(),
            workDoneToken = params.workDoneToken,
        )
        result.toIdeWorkspaceEditOrNull() ?: result
    }

    fun executeWorkspaceCommand(commandName: String, arguments: List<Any?>): JsonElement? {
        return try {
            val result =
                runBlocking(Dispatchers.IO) {
                    executeRemoteWorkspaceCommand(commandName, arguments, workDoneToken = null)
                }
            applyWorkspaceEditResult(result)
            gson.toJsonTree(result)
        } catch (e: Exception) {
            log.error("Failed to execute workspace command: $commandName", e)
            null
        }
    }

    private suspend fun executeRemoteWorkspaceCommand(
        commandName: String,
        arguments: List<Any?>,
        workDoneToken: Any?,
    ): Any? {
        if (!ensureInitialized()) {
            throw IllegalStateException("Kotlin LSP is not initialized. Cannot execute command '$commandName'.")
        }
        if (!canExecuteCommand(commandName)) {
            log.warn("Executing Kotlin LSP command '$commandName' even though it was not advertised by the server.")
        }

        val params = LspExecuteCommandParams(commandName, toLspArguments(arguments))
        setWorkDoneTokenIfSupported(params, workDoneToken)
        return requireServer().workspaceService.executeCommand(params).await()
    }


    private fun toLspArguments(arguments: List<Any?>): List<Any?> {
        return arguments.map { argument ->
            when (argument) {
                null -> null
                is JsonElement -> normalizeLspArgumentTree(argument)
                else -> normalizeLspArgumentTree(gson.toJsonTree(argument))
            }
        }
    }

    private fun normalizeLspArgumentTree(element: JsonElement): JsonElement {
        return when {
            element.isJsonArray ->
                JsonArray().also { array ->
                    element.asJsonArray.forEach { item -> array.add(normalizeLspArgumentTree(item)) }
                }
            element.isJsonObject -> normalizeLspArgumentObject(element.asJsonObject)
            else -> element.deepCopy()
        }
    }

    private fun normalizeLspArgumentObject(source: JsonObject): JsonObject {
        val result = JsonObject()
        val isIdePosition = source.has("line") && source.has("column") && !source.has("character")
        source.entrySet().forEach { (name, value) ->
            if (!(isIdePosition && name == "column")) {
                result.add(name, normalizeLspArgumentTree(value))
            }
        }

        if (isIdePosition) {
            result.add("character", source.get("column").deepCopy())
        }
        return result
    }

    private fun applyWorkspaceEditResult(result: Any?): WorkspaceEdit? {
        val edit = result.toIdeWorkspaceEditOrNull() ?: return null
        if (edit.documentChanges.isNotEmpty()) {
            client?.applyWorkspaceEdit(edit)
        }
        return edit
    }


    private fun org.eclipse.lsp4j.ExecuteCommandOptions?.toIdeExecuteCommandOptions(): ExecuteCommandOptions {
        val advertisedCommands = this?.commands.orEmpty()
        return ExecuteCommandOptions(
            commands = (advertisedCommands + KNOWN_WORKSPACE_COMMANDS).distinct(),
            workDoneProgress = this?.workDoneProgress == true,
        )
    }

    private fun org.eclipse.lsp4j.WorkspaceEdit?.toIdeWorkspaceEdit(): WorkspaceEdit {
        return gson.toJsonTree(this).toIdeWorkspaceEditOrNull() ?: WorkspaceEdit()
    }

    private fun Any?.toIdeWorkspaceEditOrNull(): WorkspaceEdit? {
        val json =
            when (this) {
                null -> return null
                is JsonElement -> this
                else -> gson.toJsonTree(this)
            }
        return json.toIdeWorkspaceEditOrNull()
    }

    private fun JsonElement?.toIdeWorkspaceEditOrNull(): WorkspaceEdit? {
        val editObject = this.asObjectOrNull() ?: return null
        val documentChanges = mutableListOf<DocumentChange>()
        var hasWorkspaceEditShape = false

        editObject.objectOrNull("changes")?.let { changes ->
            hasWorkspaceEditShape = true
            changes.entrySet().forEach { (uri, editsElement) ->
                val file = uri.toPathOrNull() ?: return@forEach
                val edits = editsElement.asArrayOrNull()?.mapNotNull { it.toIdeTextEditOrNull() }.orEmpty()
                documentChanges.add(DocumentChange(file, edits))
            }
        }

        editObject.arrayOrNull("documentChanges")?.forEach { changeElement ->
            hasWorkspaceEditShape = true
            val changeObject = changeElement.asObjectOrNull() ?: return@forEach
            val textDocument = changeObject.objectOrNull("textDocument") ?: return@forEach
            val uri = textDocument.stringOrNull("uri") ?: return@forEach
            val file = uri.toPathOrNull() ?: return@forEach
            val edits = changeObject.arrayOrNull("edits")?.mapNotNull { it.toIdeTextEditOrNull() }.orEmpty()
            documentChanges.add(DocumentChange(file, edits))
        }

        return if (hasWorkspaceEditShape) WorkspaceEdit(documentChanges) else null
    }

    private fun JsonElement.toIdeTextEditOrNull(): TextEdit? {
        val editObject = asObjectOrNull() ?: return null
        val range = editObject.objectOrNull("range")?.toIdeRangeOrNull() ?: return null
        return TextEdit(range, editObject.stringOrNull("newText").orEmpty())
    }

    private fun JsonObject.toIdeRangeOrNull(): Range? {
        val start = objectOrNull("start")?.toIdePositionOrNull() ?: return null
        val end = objectOrNull("end")?.toIdePositionOrNull() ?: return null
        return Range(start, end)
    }

    private fun JsonObject.toIdePositionOrNull(): Position? {
        val line = intOrNull("line") ?: return null
        val column = intOrNull("character") ?: intOrNull("column") ?: return null
        return Position(line, column)
    }

    private fun setWorkDoneTokenIfSupported(params: LspExecuteCommandParams, workDoneToken: Any?) {
        if (workDoneToken == null) {
            return
        }
        runCatching {
            params.javaClass.methods
                .firstOrNull { method ->
                    method.name == "setWorkDoneToken" && method.parameterTypes.size == 1
                }
                ?.invoke(params, workDoneToken)
        }
    }

    private fun String.toPathOrNull(): Path? {
        return runCatching { File(URI(this)).toPath() }
            .getOrElse { runCatching { java.nio.file.Paths.get(this) }.getOrNull() }
    }

    private fun JsonElement?.asObjectOrNull(): JsonObject? =
        if (this != null && isJsonObject) asJsonObject else null

    private fun JsonElement?.asArrayOrNull(): JsonArray? =
        if (this != null && isJsonArray) asJsonArray else null

    private fun JsonObject.objectOrNull(name: String): JsonObject? = get(name).asObjectOrNull()

    private fun JsonObject.arrayOrNull(name: String): JsonArray? = get(name).asArrayOrNull()

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        return if (value.isJsonNull) null else value.asString
    }

    private fun JsonObject.intOrNull(name: String): Int? {
        val value = get(name) ?: return null
        return if (value.isJsonNull) null else value.asInt
    }

    override fun shutdown() {
        log.info("Shutting down LSP4J Kotlin Server...")
        try {
            runBlocking {
                requireServer().shutdown().await()
                requireServer().exit()
            }
        } catch (e: Exception) {
            log.warn("Error during LSP shutdown: ${e.message}")
        } finally {
            try {
                process.destroy()
            } catch (_: Exception) {}
        }
    }

    // --- 扩展：CompletableFuture 协程桥接 & 模型双向映射 ---

    private suspend fun <T> CompletableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
        this.whenComplete { result, exception ->
            if (exception != null) {
                cont.resumeWithException(exception)
            } else {
                cont.resume(result)
            }
        }
        cont.invokeOnCancellation { this.cancel(true) }
    }

    private fun Position.toLsp4j() = org.eclipse.lsp4j.Position(this.line, this.column)
    private fun org.eclipse.lsp4j.Position.toIde() = Position(this.line, this.character)
    private fun Range.toLsp4j() = org.eclipse.lsp4j.Range(this.start.toLsp4j(), this.end.toLsp4j())
    private fun org.eclipse.lsp4j.Range.toIde() = Range(this.start.toIde(), this.end.toIde())
    private fun org.eclipse.lsp4j.TextEdit.toIde() = TextEdit(this.range.toIde(), this.newText)
    
    private fun org.eclipse.lsp4j.Diagnostic.toIde(uri: String): DiagnosticItem {
        val mappedSeverity = when (this.severity) {
            org.eclipse.lsp4j.DiagnosticSeverity.Error -> DiagnosticSeverity.ERROR
            org.eclipse.lsp4j.DiagnosticSeverity.Warning -> DiagnosticSeverity.WARNING
            org.eclipse.lsp4j.DiagnosticSeverity.Information -> DiagnosticSeverity.INFO
            org.eclipse.lsp4j.DiagnosticSeverity.Hint -> DiagnosticSeverity.HINT
            else -> DiagnosticSeverity.INFO
        }
        return DiagnosticItem(
            message = this.message,
            code = this.code?.left ?: "unknown",
            range = this.range.toIde(),
            source = this.source ?: "kotlin-lsp",
            severity = mappedSeverity,
            tags = emptyList()
        )
    }

    private fun org.eclipse.lsp4j.jsonrpc.messages.Either<String, org.eclipse.lsp4j.MarkupContent>?.toIdeMarkup(): MarkupContent {
        if (this == null) return MarkupContent("", MarkupKind.PLAIN)
        return if (this.isLeft) {
            MarkupContent(this.left ?: "", MarkupKind.PLAIN)
        } else {
            val markup = this.right
            val kind = if (markup?.kind == "markdown") MarkupKind.MARKDOWN else MarkupKind.PLAIN
            MarkupContent(markup?.value ?: "", kind)
        }
    }
}
