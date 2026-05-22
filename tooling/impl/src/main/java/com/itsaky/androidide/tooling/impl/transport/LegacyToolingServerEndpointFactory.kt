package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.transport.ToolingTransportEndpointFactory

/** Legacy transport factory for LSP4J server proxy objects. */
object LegacyToolingServerEndpointFactory : ToolingTransportEndpointFactory {
  override fun create(serverProxy: Any): LegacyToolingServerEndpoint {
    return LegacyToolingServerEndpoint(serverProxy as IToolingApiServer)
  }
}
