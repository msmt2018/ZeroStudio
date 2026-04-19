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
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.ActionsRegistry
import com.itsaky.androidide.actions.locations.CodeActionsMenu
import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.api.IServerSettings
import com.itsaky.androidide.lsp.kotlin.actions.KotlinLspActionsProvider
import com.itsaky.androidide.lsp.kotlin.events.KotlinTextDocumentSyncHandler
import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.lsp.util.LSPEditorActions
import com.itsaky.androidide.models.Location
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.preferences.internal.EditorPreferences
import com.itsaky.androidide.projects.IWorkspace
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.Logger
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.future.future
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Kotlin Language Server IDE 对接层。
 *
 * @author android_zero
 */
class KotlinLanguageServerImpl(
    private val process: Process,
    inStream: InputStream,
    outStream: OutputStream,
) : ILanguageServer {

  override val serverId: String
    get() = SERVER_ID

  override var client: ILanguageClient? = null

  private val gson = Gson()
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val rpcClient = KotlinRpcClient(inStream, outStream) { msg -> handleServerMessage(msg) }
  @Volatile private var isInitialized = false

  // 二次补全与加工处理器
  private val completionConverter = KotlinCompletionConverter()

  companion object {
    const val SERVER_ID = "kotlin-lsp"
    private val log = Logger.instance("KotlinLanguageServerImpl")
  }

  init {
    rpcClient.startListening()
  }

  override fun connectClient(client: ILanguageClient?) {
    this.client = client
  }

  override fun handleFailure(failure: LSPFailure?): Boolean {
    log.warn("Kotlin LSP encountered a failure: ${failure?.type} - ${failure?.error?.message}")
    return true
  }

  override fun applySettings(settings: IServerSettings?) {
    if (settings != null) {
      val configUpdate = createConfigPayload(settings)
      rpcClient.sendNotification(
          "workspace/didChangeConfiguration",
          mapOf("settings" to configUpdate),
      )
    }
  }

  override fun setupWorkspace(workspace: IWorkspace) {
    ensureActionsMenuRegisteredWithRetry()

    val bridge = KotlinJavaCompilerBridge(workspace)
    completionConverter.setJavaCompilerBridge(bridge)

    val initParams = createInitializeParams(workspace)
    runBlocking {
      runCatching {
            val initResult = rpcClient.sendRequest("initialize", initParams)
            if (initResult != null) {
              rpcClient.sendNotification("initialized", JsonObject())
              isInitialized = true
              KotlinTextDocumentSyncHandler.onServerReady()
            } else {
              log.warn("Kotlin LSP initialize returned null result.")
            }
          }
          .onFailure { err ->
            isInitialized = false
            log.error("Failed to initialize Kotlin LSP workspace", err)
          }
    }
  }

  private fun createInitializeParams(workspace: IWorkspace): JsonObject {
    val projectDir = workspace.getProjectDir()
    val rootUri = projectDir.toURI().toString()
    val cacheDir = Environment.getProjectCacheDir(projectDir)

    val initOptions =
        JsonObject().apply {
          addProperty("storagePath", cacheDir.absolutePath)
          addProperty("lazyCompilation", false)
          add("jvmConfiguration", JsonObject().apply { addProperty("target", "17") })
          add("formattingConfiguration", createFormatConfig())
        }

    return JsonObject().apply {
      addProperty("processId", android.os.Process.myPid())
      addProperty("rootUri", rootUri)
      addProperty("rootPath", projectDir.absolutePath)
      add(
          "workspaceFolders",
          gson.toJsonTree(
              listOf(mapOf("uri" to rootUri, "name" to projectDir.name)),
          ),
      )
      add("initializationOptions", initOptions)
      add(
          "capabilities",
          JsonObject().apply {
            add(
                "workspace",
                JsonObject().apply {
                  addProperty("workspaceFolders", true)
                  add(
                      "executeCommand",
                      JsonObject().apply { addProperty("dynamicRegistration", true) },
                  )
                },
            )
            add(
                "textDocument",
                JsonObject().apply {
                  add(
                      "completion",
                      JsonObject().apply {
                        add(
                            "completionItem",
                            JsonObject().apply {
                              addProperty("snippetSupport", true)
                              add(
                                  "resolveSupport",
                                  JsonObject().apply {
                                    add(
                                        "properties",
                                        gson.toJsonTree(
                                            listOf(
                                                "documentation",
                                                "detail",
                                                "additionalTextEdits",
                                            ),
                                        ),
                                    )
                                  },
                              )
                            },
                        )
                      },
                  )
                },
            )
          },
      )
    }
  }

  private fun createConfigPayload(settings: IServerSettings?): JsonObject {
    return JsonObject().apply {
      add(
          "kotlin",
          JsonObject().apply {
            add("formatting", createFormatConfig())
            add(
                "diagnostics",
                JsonObject().apply {
                  addProperty("enabled", settings?.codeAnalysisEnabled() ?: true)
                  addProperty("debounceTime", 250)
                },
            )
          },
      )
    }
  }

  private fun createFormatConfig(): JsonObject {
    return JsonObject().apply {
      addProperty("formatter", "ktfmt")
      add(
          "ktfmt",
          JsonObject().apply {
            addProperty("style", "google")
            addProperty("indent", EditorPreferences.tabSize)
            addProperty("maxWidth", 100)
            addProperty("removeUnusedImports", true)
          },
      )
    }
  }

  private fun ensureActionsMenuRegisteredWithRetry() {
    val provider = KotlinLspActionsProvider()
    runBlocking {
      repeat(20) { index ->
        val codeActionsMenu =
            ActionsRegistry.getInstance().findAction(ActionItem.Location.EDITOR_TEXT_ACTIONS, CodeActionsMenu.ID)
        if (codeActionsMenu != null) {
          LSPEditorActions.ensureActionsMenuRegistered(provider)
          return@runBlocking
        }
        if (index < 19) {
          delay(150)
        }
      }
      LSPEditorActions.ensureActionsMenuRegistered(provider)
    }
  }

  private fun handleServerMessage(msg: JsonObject) {
    if (!msg.has("method")) return
    val method = msg.get("method").asString
    val params = msg.get("params")

    when (method) {
      "textDocument/publishDiagnostics" -> {
        try {
          val uri = params.asJsonObject.get("uri").asString
          val diagsJson = params.asJsonObject.get("diagnostics").asJsonArray
          val typeType = object : TypeToken<List<DiagnosticItem>>() {}.type
          val diagnosticsList: List<DiagnosticItem> = gson.fromJson(diagsJson, typeType)

          val path = File(java.net.URI(uri)).toPath()
          client?.publishDiagnostics(DiagnosticResult(path, diagnosticsList))
        } catch (e: Exception) {
          log.error("Error parsing diagnostics", e)
        }
      }
      "window/showMessage" -> {
        val type = msg.getAsJsonObject("params").get("type").asInt
        val text = msg.getAsJsonObject("params").get("message").asString
        val msgType =
            MessageType.values().firstOrNull { it.ordinal == (type - 1) } ?: MessageType.Info
        client?.showMessage(ShowMessageParams(msgType, text))
      }
      "workspace/applyEdit" -> {
        val editJson = params.asJsonObject.get("edit")
        val workspaceEdit: WorkspaceEdit = gson.fromJson(editJson, WorkspaceEdit::class.java)
        client?.applyWorkspaceEdit(workspaceEdit)
      }
    }
  }

  fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
    return scope.future {
        val resultElement = rpcClient.sendRequest("initialize", params)
        gson.fromJson(resultElement, InitializeResult::class.java)
    }
  }

  fun initialized(params: InitializedParams) {
    rpcClient.sendNotification("initialized", params)
  }

  override fun shutdown() {
    log.info("Shutting down Kotlin LSP Server...")
    runBlocking {
      try {
        rpcClient.sendRequest("shutdown", JsonObject())
        rpcClient.sendNotification("exit", JsonObject())
      } catch (e: Exception) {} finally {
        rpcClient.stop()
        try {
          process.destroy()
        } catch (_: Exception) {}
      }
    }
  }

  fun exit() {
    rpcClient.sendNotification("exit", Any())
    rpcClient.stop()
    try { process.destroy() } catch (_: Exception) {}
  }

  override fun didOpen(params: DidOpenTextDocumentParams) {
    val textDocument = params.textDocument ?: return
    val payload =
        mapOf(
            "textDocument" to
                mapOf(
                    "uri" to textDocument.uri,
                    "languageId" to textDocument.languageId,
                    "version" to textDocument.version,
                    "text" to textDocument.text,
                )
        )
    rpcClient.sendNotification("textDocument/didOpen", payload)
  }

  override fun didChange(params: DidChangeTextDocumentParams) {
    val textDocument = params.textDocument ?: return
    val changes = params.contentChanges.map { 
        if (it.range != null) mapOf("range" to it.range, "text" to it.text) 
        else mapOf("text" to it.text) 
    }
    val payload =
        mapOf(
            "textDocument" to
                mapOf("uri" to textDocument.uri, "version" to textDocument.version),
            "contentChanges" to changes,
        )
    rpcClient.sendNotification("textDocument/didChange", payload)
  }

  override fun didClose(params: DidCloseTextDocumentParams) {
    val textDocument = params.textDocument ?: return
    rpcClient.sendNotification(
        "textDocument/didClose",
        mapOf("textDocument" to mapOf("uri" to textDocument.uri)),
    )
  }

  override fun didSave(params: DidSaveTextDocumentParams) {
    val textDocument = params.textDocument ?: return
    rpcClient.sendNotification(
        "textDocument/didSave",
        mapOf(
            "textDocument" to mapOf("uri" to textDocument.uri),
            "text" to params.text,
        ),
    )
  }

  override fun complete(params: CompletionParams?): CompletionResult {
    if (params == null) return CompletionResult.EMPTY
    if (!isInitialized) return CompletionResult.EMPTY

    return runBlocking(Dispatchers.IO) {
      val req =
          mapOf(
              "textDocument" to mapOf("uri" to com.itsaky.androidide.lsp.rpc.UriConverter.pathToUri(params.file)),
              "position" to mapOf("line" to params.position.line, "character" to params.position.column),
          )
      try {
        val response =
            rpcClient.sendRequest("textDocument/completion", req)
                ?: return@runBlocking CompletionResult.EMPTY

        val itemsJson =
            if (response.isJsonObject) {
              response.asJsonObject.get("items").asJsonArray
            } else {
              response.asJsonArray
            }

        // Prefix提取
        val contentStr = params.content?.toString() ?: ""
        val lines = contentStr.split("\n")
        val prefix =
            if (params.position.line < lines.size) {
              val line = lines[params.position.line]
              val col = params.position.column.coerceAtMost(line.length)
              var start = col
              while (
                  start > 0 && (line[start - 1].isLetterOrDigit() || line[start - 1] == '_')
              ) start--
              line.substring(start, col)
            } else ""

        // 交由转换器进行 Java/Android 二次识别注入及无用占位符清理
        val enhancedItems =
            completionConverter.convertWithClasspathEnhancement(itemsJson, contentStr, prefix)

        enhancedItems.forEach {
          it.completionKind = it.completionKind ?: CompletionItemKind.NONE
          it.matchLevel = MatchLevel.PARTIAL_MATCH
        }

        CompletionResult(enhancedItems)
      } catch (e: Exception) {
        log.error("Completion error", e)
        CompletionResult.EMPTY
      }
    }
  }

  override suspend fun findReferences(params: ReferenceParams): ReferenceResult =
      withContext(Dispatchers.IO) {
        val req =
            mapOf(
                "textDocument" to mapOf("uri" to com.itsaky.androidide.lsp.rpc.UriConverter.pathToUri(params.file)),
                "position" to mapOf("line" to params.position.line, "character" to params.position.column),
                "context" to mapOf("includeDeclaration" to params.includeDeclaration),
            )
        val res = rpcClient.sendRequest("textDocument/references", req)
        val type = object : TypeToken<List<Location>>() {}.type
        val locations: List<Location>? = gson.fromJson(res, type)
        
        ReferenceResult(locations ?: emptyList())
      }

  override suspend fun findDefinition(params: DefinitionParams): DefinitionResult =
      withContext(Dispatchers.IO) {
        val req =
            mapOf(
                "textDocument" to mapOf("uri" to com.itsaky.androidide.lsp.rpc.UriConverter.pathToUri(params.file)),
                "position" to mapOf("line" to params.position.line, "character" to params.position.column),
            )
        val res = rpcClient.sendRequest("textDocument/definition", req)
        val locations =
            if (res == null || res.isJsonNull) {
              emptyList()
            } else if (res.isJsonArray) {
              gson.fromJson<List<Location>>(res, object : TypeToken<List<Location>>() {}.type) ?: emptyList()
            } else {
              listOfNotNull(gson.fromJson(res, Location::class.java))
            }

        DefinitionResult(locations)
      }

  override suspend fun expandSelection(params: ExpandSelectionParams): Range =
      withContext(Dispatchers.IO) {
        val req =
            mapOf(
                "textDocument" to mapOf("uri" to com.itsaky.androidide.lsp.rpc.UriConverter.fileToUri(params.file.toFile())),
                "positions" to listOf(params.selection.start),
            )
        val res = rpcClient.sendRequest("textDocument/selectionRange", req)
        val type = object : TypeToken<List<SelectionRange>>() {}.type
        val ranges: List<SelectionRange> = gson.fromJson(res, type) ?: emptyList()
        ranges.firstOrNull()?.range ?: params.selection
      }

  override suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp =
      withContext(Dispatchers.IO) {
        val file = params.file ?: return@withContext SignatureHelp()
        val req =
            mapOf(
                "textDocument" to mapOf("uri" to com.itsaky.androidide.lsp.rpc.UriConverter.pathToUri(file)),
                "position" to mapOf("line" to params.position.line, "character" to params.position.column),
                "context" to params.context
            )
        val res = rpcClient.sendRequest("textDocument/signatureHelp", req)
        gson.fromJson(res, SignatureHelp::class.java) ?: SignatureHelp()
      }

  override suspend fun hover(params: DefinitionParams): MarkupContent =
      withContext(Dispatchers.IO) {
        val req =
            mapOf(
                "textDocument" to mapOf("uri" to com.itsaky.androidide.lsp.rpc.UriConverter.pathToUri(params.file)),
                "position" to mapOf("line" to params.position.line, "character" to params.position.column),
            )
        val res = rpcClient.sendRequest("textDocument/hover", req)
        val hover = gson.fromJson(res, Hover::class.java)
        
        hover?.contents?.map(
            { it },
            { list -> MarkupContent("markdown", list.joinToString("\n") { m -> m.map({ s -> s }, { ms -> ms.value }) }) }
        ) ?: MarkupContent("plaintext", "")
      }

  override suspend fun analyze(file: Path): DiagnosticResult =
      withContext(Dispatchers.IO) { DiagnosticResult.NO_UPDATE } // LSP uses push diagnostics

  override fun formatCode(params: FormatCodeParams?): CodeFormatResult {
    if (params == null) return CodeFormatResult.NONE
    return runBlocking(Dispatchers.IO) {
      val req =
          mapOf(
              "textDocument" to mapOf("uri" to "file://dummy_for_format"), // 事实上这应为真实 URI,但受限于旧接口我们采用占位符
              "options" to
                  mapOf(
                      "tabSize" to EditorPreferences.tabSize,
                      "insertSpaces" to EditorPreferences.useSoftTab,
                  ),
          )
      val res = rpcClient.sendRequest("textDocument/formatting", req)
      val type = object : TypeToken<List<TextEdit>>() {}.type
      val edits: MutableList<TextEdit> = gson.fromJson(res, type) ?: mutableListOf()
      CodeFormatResult(false, edits, mutableListOf())
    }
  }

  override suspend fun rename(params: RenameParams): WorkspaceEdit =
      withContext(Dispatchers.IO) {
        val req =
            mapOf(
                "textDocument" to mapOf("uri" to com.itsaky.androidide.lsp.rpc.UriConverter.fileToUri(params.file.toFile())),
                "position" to params.position,
                "newName" to params.newName,
            )
        val res = rpcClient.sendRequest("textDocument/rename", req)
        gson.fromJson(res, WorkspaceEdit::class.java) ?: WorkspaceEdit()
      }

  override suspend fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens =
      withContext(Dispatchers.IO) {
        val element = rpcClient.sendRequest("textDocument/semanticTokens/full", params)
        gson.fromJson(element, SemanticTokens::class.java) ?: SemanticTokens(data = emptyList())
      }

  override suspend fun documentSymbols(file: Path): DocumentSymbolsResult =
      withContext(Dispatchers.IO) {
        val params = DocumentSymbolParams(TextDocumentIdentifier(com.itsaky.androidide.lsp.rpc.UriConverter.fileToUri(file.toFile())))
        val element = rpcClient.sendRequest("textDocument/documentSymbol", params)
        
        val type = object : TypeToken<Either<List<SymbolInformation>, List<DocumentSymbol>>>() {}.type
        val either: Either<List<SymbolInformation>, List<DocumentSymbol>>? = gson.fromJson(element, type)
        
        either?.map(
            { infos -> DocumentSymbolsResult(flatSymbols = infos) },
            { symbols -> DocumentSymbolsResult(symbols = symbols) }
        ) ?: DocumentSymbolsResult()
      }

  override suspend fun workspaceSymbols(query: String): WorkspaceSymbolsResult =
      withContext(Dispatchers.IO) {
        val params = WorkspaceSymbolParams(query)
        val element = rpcClient.sendRequest("workspace/symbol", params)
        val type = object : TypeToken<List<SymbolInformation>>() {}.type
        val result: List<SymbolInformation>? = gson.fromJson(element, type)
        
        WorkspaceSymbolsResult(result?.map { 
            WorkspaceSymbol(it.name, it.kindValue, it.tagsValue, Either.forLeft(it.lspLocation), it.containerName) 
        } ?: emptyList())
      }

  override suspend fun inlayHints(params: InlayHintParams): List<InlayHint> =
      withContext(Dispatchers.IO) {
        val element = rpcClient.sendRequest("textDocument/inlayHint", params)
        val type = object : TypeToken<List<InlayHint>>() {}.type
        gson.fromJson(element, type) ?: emptyList()
      }

  override fun executeCommand(command: Command) {
      scope.launch {
          val params = mapOf("command" to command.command, "arguments" to emptyList<Any>())
          rpcClient.sendRequest("workspace/executeCommand", params)
      }
  }

  fun executeWorkspaceCommand(command: String, arguments: List<Any?> = emptyList()): JsonElement? {
    return runBlocking(Dispatchers.IO) {
      rpcClient.sendRequest(
          "workspace/executeCommand",
          mapOf("command" to command, "arguments" to arguments),
      )
    }
  }
}
