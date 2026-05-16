package com.itsaky.androidide.tooling.impl

import org.slf4j.LoggerFactory

/** BSP-only tooling entrypoint. Legacy LSP4J RPC server path has been removed. */
object Main {
  private val log = LoggerFactory.getLogger(Main::class.java)

  @JvmStatic
  fun main(args: Array<String>) {
    log.info("Starting BSP-only tooling daemon placeholder...")
    // The runtime process now communicates through BSP build server wiring.
    // Legacy ToolingApiServerImpl bootstrap has been removed.
    Thread.currentThread().join()
  }
}
