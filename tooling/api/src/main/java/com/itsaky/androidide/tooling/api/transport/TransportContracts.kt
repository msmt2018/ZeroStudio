package com.itsaky.androidide.tooling.api.transport

import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.TaskExecutionMessage
import com.itsaky.androidide.tooling.api.messages.result.BuildCancellationRequestResult
import com.itsaky.androidide.tooling.api.messages.result.ExecutionResult
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult
import com.itsaky.androidide.tooling.api.models.ToolingServerMetadata
import java.io.InputStream
import java.util.concurrent.CompletableFuture

/** Transport-neutral contract for build-service client->server calls. */
interface ToolingTransportServerEndpoint {
  fun metadata(): CompletableFuture<ToolingServerMetadata>

  fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult>

  fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult>

  fun execute(request: ExecutionRequest): CompletableFuture<ExecutionResult>

  fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult>

  fun shutdown(): CompletableFuture<Void>
}

/** Runtime selector for transport implementations. */
enum class ToolingTransportKind {
  LEGACY_LSP4J,
  AIDL,
  GRPC_UDS,
}

/**
 * Observer boundary used by transport runners to report lifecycle updates without exposing
 * transport-specific server/client concrete types.
 */
interface ToolingTransportObserver {
  fun onServerConnected(endpoint: ToolingTransportServerEndpoint, errorStream: InputStream)

  fun onServerExited(exitCode: Int)
}

/** Factory for creating [ToolingTransportServerEndpoint] instances from a connected server proxy. */
fun interface ToolingTransportEndpointFactory {
  fun create(serverProxy: Any): ToolingTransportServerEndpoint
}
