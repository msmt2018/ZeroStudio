package com.itsaky.androidide.editor.lsp

import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.models.CodeAction
import com.itsaky.androidide.lsp.models.CodeActionParams
import com.itsaky.androidide.lsp.models.Command
import com.itsaky.androidide.lsp.models.DefinitionParams
import com.itsaky.androidide.lsp.models.Either
import com.itsaky.androidide.lsp.models.Hover
import com.itsaky.androidide.lsp.models.HoverParams
import com.itsaky.androidide.lsp.models.InlayHint
import com.itsaky.androidide.lsp.models.InlayHintParams
import com.itsaky.androidide.lsp.models.MarkupContent
import com.itsaky.androidide.lsp.models.SemanticTokens
import com.itsaky.androidide.lsp.models.SemanticTokensParams
import com.itsaky.androidide.lsp.models.SignatureHelp
import com.itsaky.androidide.lsp.models.SignatureHelpParams
import com.itsaky.androidide.lsp.rpc.UriConverter
import com.itsaky.androidide.progress.ICancelChecker
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.runBlocking

fun ILanguageServer.codeAction(
    @Suppress("UNUSED_PARAMETER") params: CodeActionParams
): CompletableFuture<List<Either<Command, CodeAction>>> {
  return CompletableFuture.completedFuture(emptyList())
}

fun ILanguageServer.hover(params: HoverParams): CompletableFuture<Hover?> {
  return CompletableFuture.supplyAsync {
    runBlocking {
      val path = UriConverter.uriToPath(params.textDocument.uri)
      val content =
          hover(
              DefinitionParams(
                  file = path,
                  position =
                      com.itsaky.androidide.models.Position(
                          params.position.line,
                          params.position.character,
                      ),
                  cancelChecker = ICancelChecker.NOOP,
              ))
      Hover(Either.forLeft(content), null)
    }
  }
}

fun ILanguageServer.inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> {
  return CompletableFuture.supplyAsync { runBlocking { inlayHints(params) } }
}

fun ILanguageServer.semanticTokensFullAsync(
    params: SemanticTokensParams
): CompletableFuture<SemanticTokens> {
  return CompletableFuture.supplyAsync { runBlocking { semanticTokensFull(params) } }
}

fun ILanguageServer.signatureHelpAsync(
    params: SignatureHelpParams
): CompletableFuture<SignatureHelp> {
  return CompletableFuture.supplyAsync { runBlocking { signatureHelp(params) } }
}

