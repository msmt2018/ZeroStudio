package com.itsaky.androidide.tooling.api.bsp

import ch.epfl.scala.bsp4j.BuildClientCapabilities
import ch.epfl.scala.bsp4j.BuildServer
import ch.epfl.scala.bsp4j.InitializeBuildParams
import ch.epfl.scala.bsp4j.InitializeBuildResult
import com.itsaky.androidide.tooling.api.util.ToolingApiLauncher
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** BSP connection facade used by AndroidIDE to hold a [BuildServer] proxy and lifecycle calls. */
class BspServerConnection(
  localClient: Any,
  input: InputStream,
  output: OutputStream,
) {
  private val launcher =
    ToolingApiLauncher.createIOLauncher(localClient, BuildServer::class.java, input, output)

  val server: BuildServer
    get() = launcher.remoteProxy

  fun startListening() = launcher.startListening()

  fun initialize(rootUri: String): CompletableFuture<InitializeBuildResult> {
    val params = InitializeBuildParams()
    params.displayName = BspProtocol.CLIENT_NAME
    params.version = "1.0.0"
    params.bspVersion = BspProtocol.PROTOCOL_VERSION
    params.rootUri = rootUri
    params.capabilities = BuildClientCapabilities(listOf("java", "kotlin"))
    params.data = UUID.randomUUID().toString()
    return server.buildInitialize(params)
  }

  fun shutdown(): CompletableFuture<Any> = server.buildShutdown()
}
