package com.itsaky.androidide.lsp.kotlin.lsp

import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.api.IServerSettings
import com.itsaky.androidide.lsp.models.CallHierarchyItem
import com.itsaky.androidide.lsp.models.CodeFormatResult
import com.itsaky.androidide.lsp.models.CodeLens
import com.itsaky.androidide.lsp.models.CompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult
import com.itsaky.androidide.lsp.models.DefinitionParams
import com.itsaky.androidide.lsp.models.DefinitionResult
import com.itsaky.androidide.lsp.models.DiagnosticResult
import com.itsaky.androidide.lsp.models.DidChangeTextDocumentParams
import com.itsaky.androidide.lsp.models.DidCloseTextDocumentParams
import com.itsaky.androidide.lsp.models.DidOpenTextDocumentParams
import com.itsaky.androidide.lsp.models.DidSaveTextDocumentParams
import com.itsaky.androidide.lsp.models.DocumentLink
import com.itsaky.androidide.lsp.models.DocumentSymbolsResult
import com.itsaky.androidide.lsp.models.ExpandSelectionParams
import com.itsaky.androidide.lsp.models.FoldingRange
import com.itsaky.androidide.lsp.models.FormatCodeParams
import com.itsaky.androidide.lsp.models.InlayHint
import com.itsaky.androidide.lsp.models.InlayHintParams
import com.itsaky.androidide.lsp.models.MarkupContent
import com.itsaky.androidide.lsp.models.PrepareRenameResult
import com.itsaky.androidide.lsp.models.ReferenceParams
import com.itsaky.androidide.lsp.models.ReferenceResult
import com.itsaky.androidide.lsp.models.RenameParams
import com.itsaky.androidide.lsp.models.SelectionRange
import com.itsaky.androidide.lsp.models.SelectionRangesParams
import com.itsaky.androidide.lsp.models.SemanticTokens
import com.itsaky.androidide.lsp.models.SemanticTokensDelta
import com.itsaky.androidide.lsp.models.SemanticTokensParams
import com.itsaky.androidide.lsp.models.SignatureHelp
import com.itsaky.androidide.lsp.models.SignatureHelpParams
import com.itsaky.androidide.lsp.models.TypeHierarchyItem
import com.itsaky.androidide.lsp.models.WorkspaceEdit
import com.itsaky.androidide.lsp.models.WorkspaceSymbolsResult
import com.itsaky.androidide.lsp.util.LSPEditorActions
import com.itsaky.androidide.lsp.kotlin.lsp.actions.KotlinCodeActionsMenu
import com.itsaky.androidide.lsp.kotlin.lsp.classpath.KotlinClasspathLibraryService
import com.itsaky.androidide.lsp.kotlin.lsp.context.KotlinWorkspaceContextService
import com.itsaky.androidide.lsp.kotlin.lsp.index.KotlinSearchIndexService
import com.itsaky.androidide.lsp.kotlin.lsp.ops.KotlinLspOperationService
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.projects.IWorkspace
import java.nio.file.Files
import java.nio.file.Path
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceFolder
import org.javacs.kt.KotlinLanguageServer

/** Bridges AndroidIDE ILanguageServer API to org.javacs.kt Kotlin language server. */
class KotlinLspServer(
    private val features: KotlinLspFeatureMatrix = KotlinLspFeatureMatrix(),
) : ILanguageServer {

  companion object {
    const val SERVER_ID = "ide.lsp.kotlin.next"
  }

  private val delegate = KotlinLanguageServer()
  private val bridgeClient = KotlinLspClientBridge { client }
  private val workspaceContextService = KotlinWorkspaceContextService()
  private val classpathService = KotlinClasspathLibraryService()
  private val searchIndexService = KotlinSearchIndexService()
  private val operationService = KotlinLspOperationService()

  private var initialized = false
  private var workspaceFolders: List<WorkspaceFolder> = emptyList()
  private var workspaceContext: KotlinWorkspaceContextService.WorkspaceContext? = null
  private var libraryIndex: KotlinClasspathLibraryService.LibraryIndex? = null

  override val serverId: String = SERVER_ID

  override var client: ILanguageClient? = null
    private set

  init {
    delegate.connect(bridgeClient)
  }

  override fun shutdown() {
    delegate.shutdown().get()
    initialized = false
  }

  override fun connectClient(client: ILanguageClient?) {
    this.client = client
  }

  override fun applySettings(settings: IServerSettings?) = Unit

  override fun setupWorkspace(workspace: IWorkspace) {
    LSPEditorActions.ensureActionsMenuRegistered(KotlinCodeActionsMenu)
    workspaceFolders =
        workspace.getSubProjects().map { WorkspaceFolder(it.projectDir.toURI().toString(), it.name) }
    workspaceContext = workspaceContextService.build(workspace)
    libraryIndex = classpathService.resolve(workspaceContext?.modules.orEmpty().map { it.modulePath })

    val params =
        InitializeParams().apply {
          rootUri = workspace.getProjectDir().toURI().toString()
          this.workspaceFolders = this@KotlinLspServer.workspaceFolders
        }

    delegate.initialize(params).get()
    initialized = true
  }

  override fun didOpen(params: DidOpenTextDocumentParams) {
    ensureInitialized()
    delegate.textDocumentService.didOpen(
        org.eclipse.lsp4j.DidOpenTextDocumentParams(
            TextDocumentItem(
                params.file.toUri().toString(),
                params.languageId,
                params.version,
                params.text,
            )
        )
    )
  }

  override fun didChange(params: DidChangeTextDocumentParams) {
    ensureInitialized()
    delegate.textDocumentService.didChange(
        org.eclipse.lsp4j.DidChangeTextDocumentParams(
            VersionedTextDocumentIdentifier(params.file.toUri().toString(), params.version),
            params.contentChanges.map { it.toLsp() },
        )
    )
  }

  override fun didClose(params: DidCloseTextDocumentParams) {
    ensureInitialized()
    delegate.textDocumentService.didClose(
        org.eclipse.lsp4j.DidCloseTextDocumentParams(
            org.eclipse.lsp4j.TextDocumentIdentifier(params.file.toUri().toString())
        )
    )
  }

  override fun didSave(params: DidSaveTextDocumentParams) {
    ensureInitialized()
    delegate.textDocumentService.didSave(
        org.eclipse.lsp4j.DidSaveTextDocumentParams(
            org.eclipse.lsp4j.TextDocumentIdentifier(params.file.toUri().toString()),
            params.text,
        )
    )
  }

  override fun complete(params: CompletionParams?): CompletionResult {
    if (params == null || !features.completion) return CompletionResult.EMPTY
    ensureInitialized()
    val result = delegate.textDocumentService.completion(params.toLsp()).get()
    return result.toIdeCompletionResult()
  }

  override suspend fun findReferences(params: ReferenceParams): ReferenceResult {
    if (!features.references) return ReferenceResult(emptyList())
    ensureInitialized()
    val result = delegate.textDocumentService.references(params.toLspReference()).get() ?: emptyList()
    return ReferenceResult(result.map { it.toIde() })
  }

  override suspend fun findDefinition(params: DefinitionParams): DefinitionResult {
    if (!features.definition) return DefinitionResult(emptyList())
    ensureInitialized()
    val result = delegate.textDocumentService.definition(params.toLspDefinition()).get()
    val locations = result.left ?: emptyList()
    return DefinitionResult(locations.map { it.toIde() })
  }

  override suspend fun expandSelection(params: ExpandSelectionParams): Range = params.selection

  override suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp {
    ensureInitialized()
    return delegate.textDocumentService.signatureHelp(params.toLspSignature()).get().toIde()
  }

  override suspend fun hover(params: DefinitionParams): MarkupContent {
    ensureInitialized()
    return delegate.textDocumentService.hover(params.toLspHover()).get().toIde()
  }

  override suspend fun analyze(file: Path): DiagnosticResult {
    if (!Files.exists(file)) return DiagnosticResult.NO_UPDATE
    val text = runCatching { file.toFile().readText() }.getOrDefault("")
    val diagnostics = mutableListOf<com.itsaky.androidide.lsp.models.DiagnosticItem>()
    if (text.contains("TODO(", ignoreCase = true)) {
      diagnostics +=
          com.itsaky.androidide.lsp.models.DiagnosticItem(
              message = "TODO marker found in source.",
              code = "KOTLIN_TODO",
              range = Range.NONE,
              source = SERVER_ID,
              severity = com.itsaky.androidide.lsp.models.DiagnosticSeverity.HINT,
          )
    }
    return DiagnosticResult(file, diagnostics)
  }

  override fun formatCode(params: FormatCodeParams?): CodeFormatResult = CodeFormatResult.NONE

  override suspend fun documentSymbols(file: Path): DocumentSymbolsResult {
    if (!features.symbols) return DocumentSymbolsResult()
    ensureInitialized()
    val result = delegate.textDocumentService.documentSymbol(file.toLspDocumentSymbol()).get()
    val ide = result.toIdeDocumentSymbols()
    if (ide.symbols.isNotEmpty() || ide.flatSymbols.isNotEmpty()) return ide
    return DocumentSymbolsResult(symbols = searchIndexService.documentSymbols(file))
  }

  override suspend fun workspaceSymbols(query: String): WorkspaceSymbolsResult {
    if (!features.symbols) return WorkspaceSymbolsResult()
    ensureInitialized()
    val delegateSymbols =
        delegate.workspaceService
            .symbol(org.eclipse.lsp4j.WorkspaceSymbolParams(query))
            .get()
            .toIdeWorkspaceSymbols()
    if (delegateSymbols.symbols.isNotEmpty()) return delegateSymbols
    return WorkspaceSymbolsResult(searchIndexService.workspaceSymbols(query, workspaceContext, libraryIndex))
  }

  override suspend fun prepareRename(params: DefinitionParams): PrepareRenameResult? = null

  override suspend fun rename(params: RenameParams): WorkspaceEdit {
    ensureInitialized()
    val result = delegate.textDocumentService.rename(params.toLspRename()).get() ?: return WorkspaceEdit()
    return result.toIdeWorkspaceEdit()
  }

  override suspend fun foldingRanges(file: Path): List<FoldingRange> = emptyList()

  override suspend fun selectionRanges(params: SelectionRangesParams): List<SelectionRange> = emptyList()

  override suspend fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens {
    ensureInitialized()
    if (!features.semanticTokens) return SemanticTokens()
    return delegate.textDocumentService.semanticTokensFull(params.toLspSemanticTokens()).get().toIde()
  }

  override suspend fun semanticTokensRange(params: SemanticTokensParams): SemanticTokens {
    ensureInitialized()
    if (!features.semanticTokens) return SemanticTokens()
    return delegate.textDocumentService.semanticTokensRange(params.toLspSemanticTokensRange()).get().toIde()
  }

  override suspend fun semanticTokensDelta(params: SemanticTokensParams): SemanticTokensDelta {
    return SemanticTokensDelta()
  }

  override suspend fun inlayHints(params: InlayHintParams): List<InlayHint> {
    if (!features.inlayHints) return emptyList()
    ensureInitialized()
    return delegate.textDocumentService.inlayHint(params.toLspInlayHint()).get().map { it.toIde() }
  }

  override suspend fun documentLinks(file: Path): List<DocumentLink> {
    if (!Files.exists(file)) return emptyList()
    val text = runCatching { file.toFile().readText() }.getOrDefault("")
    val links = mutableListOf<DocumentLink>()
    Regex("""https?://[^\s"']+""").findAll(text).forEach {
      links += DocumentLink(Range.NONE, it.value, "URL")
    }
    return links
  }

  override suspend fun codeLens(file: Path): List<CodeLens> = emptyList()

  override suspend fun callHierarchy(params: DefinitionParams): List<CallHierarchyItem> = emptyList()

  override suspend fun typeHierarchy(params: DefinitionParams): List<TypeHierarchyItem> = emptyList()

  fun executeWorkspaceCommand(command: String, arguments: List<Any> = emptyList()): Any? {
    ensureInitialized()
    val params = operationService.execute(command, arguments)
    return delegate.workspaceService.executeCommand(params).get()
  }

  fun decompileClassFile(classFile: Path): Path = operationService.decompileClassFile(classFile)

  fun decompileLibraryJar(jar: Path): Path = operationService.decompileLibraryJar(jar)

  private fun ensureInitialized() {
    if (!initialized) {
      delegate.initialize(InitializeParams().apply { workspaceFolders = this@KotlinLspServer.workspaceFolders }).get()
      initialized = true
    }
  }
}
