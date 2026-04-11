package com.itsaky.androidide.lsp.kotlin.lsp

import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.models.LogMessageParams
import com.itsaky.androidide.lsp.models.MessageType
import com.itsaky.androidide.lsp.models.ShowMessageParams
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.services.LanguageClient

internal class KotlinLspClientBridge(
    private val clientProvider: () -> ILanguageClient?,
) : LanguageClient {

  override fun telemetryEvent(`object`: Any?) = Unit

  override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
    clientProvider()?.publishDiagnostics(diagnostics.toIde())
  }

  override fun showMessage(messageParams: MessageParams) {
    clientProvider()?.showMessage(
        ShowMessageParams(messageParams.type.toIdeMessageType(), messageParams.message.orEmpty())
    )
  }

  override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem?> {
    return CompletableFuture.completedFuture(null)
  }

  override fun logMessage(message: MessageParams) {
    clientProvider()?.logMessage(
        LogMessageParams(message.type.toIdeMessageType(), message.message.orEmpty())
    )
  }

  override fun createProgress(params: WorkDoneProgressCreateParams): CompletableFuture<Void> {
    return CompletableFuture.completedFuture(null)
  }

  override fun notifyProgress(params: ProgressParams) = Unit

  override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> {
    val applied = clientProvider()?.applyWorkspaceEdit(params.edit.toIdeWorkspaceEdit()) ?: false
    return CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(applied))
  }

  override fun registerCapability(params: RegistrationParams): CompletableFuture<Void> {
    return CompletableFuture.completedFuture(null)
  }

  override fun unregisterCapability(params: UnregistrationParams): CompletableFuture<Void> {
    return CompletableFuture.completedFuture(null)
  }

  private fun org.eclipse.lsp4j.MessageType?.toIdeMessageType(): MessageType =
      when (this) {
        org.eclipse.lsp4j.MessageType.Error -> MessageType.Error
        org.eclipse.lsp4j.MessageType.Warning -> MessageType.Warning
        org.eclipse.lsp4j.MessageType.Info -> MessageType.Info
        org.eclipse.lsp4j.MessageType.Log -> MessageType.Log
        else -> MessageType.Info
      }
}
