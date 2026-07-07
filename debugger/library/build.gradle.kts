import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Zip

plugins { `java-library` }

description = "Packages ZeroStudio debugger/logger runtime artifacts into a single distributable archive."

val debuggerLibraryArchiveName = "debugger-library.zip"

data class RuntimeArtifact(
    val projectPath: String,
    val producerTaskName: String,
    val buildOutput: String,
    val packagedName: String,
)

val runtimeArtifacts = listOf(
    RuntimeArtifact(
        ":tooling:plugin",
        "jar",
        "libs/androidide-plugin.jar",
        "androidide-plugin.jar",
    ),
    RuntimeArtifact(
        ":debugger:Breakpoint-debugger:ide-debugger",
        "assembleRelease",
        "outputs/aar/ide-debugger-release.aar",
        "ide-debugger.aar",
    ),
    RuntimeArtifact(
        ":debugger:log-runtime:ide-log-plugin",
        "assembleRelease",
        "outputs/aar/ide-log-plugin-release.aar",
        "ide-log-plugin-1.0.0.aar",
    ),
    RuntimeArtifact(
        ":logging:logger",
        "jar",
        "libs/logger.jar",
        "logger.jar",
    ),
    RuntimeArtifact(
        ":logging:logsender",
        "assembleRelease",
        "outputs/aar/logsender-release.aar",
        "logsender.aar",
    ),
    RuntimeArtifact(
        ":tooling:plugin-config",
        "jar",
        "libs/plugin-config.jar",
        "plugin-config.jar",
    ),
)

val packageDebuggerLibrary by tasks.registering(Zip::class) {
  group = "debugger"
  description = "Bundles debugger/logger runtime jars and AARs into $debuggerLibraryArchiveName."

  archiveFileName.set(debuggerLibraryArchiveName)
  destinationDirectory.set(layout.buildDirectory.dir("distributions"))
  duplicatesStrategy = DuplicatesStrategy.FAIL

  runtimeArtifacts.forEach { artifact ->
    val artifactProject = project(artifact.projectPath)
    dependsOn(artifactProject.tasks.named(artifact.producerTaskName))
    from(artifactProject.layout.buildDirectory.file(artifact.buildOutput)) {
      rename { artifact.packagedName }
    }
  }
}

tasks.named("assemble") { dependsOn(packageDebuggerLibrary) }
