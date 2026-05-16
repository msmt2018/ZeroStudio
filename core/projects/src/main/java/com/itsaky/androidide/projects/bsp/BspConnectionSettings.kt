package com.itsaky.androidide.projects.bsp

import com.itsaky.androidide.tooling.api.bsp.BspProtocol

/** Local representation of the BSP endpoint used by project services. */
data class BspConnectionSettings(
  val protocolName: String = BspProtocol.PROTOCOL_NAME,
  val protocolVersion: String = BspProtocol.PROTOCOL_VERSION,
  val serverName: String = "AndroidIDE Gradle BSP",
  val serverVersion: String = "1.0.0",
  val argv: List<String> = listOf("./gradlew", "--quiet", "bsp"),
)
