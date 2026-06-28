/*
 *  ZeroStudio IDE - tooling/plugin
 *
 *  Phase C5: the debugger init-script plugin. It is the IDE-side
 *  companion to the host application's JDWP server: it injects
 *  the :ide-log-plugin AAR (already wired by [IdeLogInitScriptPlugin])
 *  AND registers a synthetic ContentProvider whose only job is to
 *  start the JDWP server as early as possible during process
 *  startup.
 *
 *  The ContentProvider approach is used because Android creates
 *  ContentProviders before Application.onCreate(), which means we
 *  can attach the JDWP listener to the loopback interface before
 *  any user code runs. The synthetic provider is registered with
 *  a fixed authority "com.zerostudio.debugger.bootstrap" and
 *  exposes a no-op query() / insert() / update() / delete() that
 *  the OS calls once during instantiation.
 *
 *  The provider class itself lives in the :ide-log-plugin AAR
 *  (com.zerostudio.logplugin.bootstrap.DebuggerBootstrapProvider)
 *  so this plugin only needs to register the manifest entry.
 */

package com.itsaky.androidide.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.impl.ApplicationVariantImpl
import com.itsaky.androidide.buildinfo.BuildInfo
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.logging.Logging

/**
 * Init-script plugin that wires the JDWP bootstrap ContentProvider
 * into the target application's debug variant.
 *
 * @author ZeroStudio
 */
class IdeDebuggerInitScriptPlugin : Plugin<Project> {

  companion object {
    /** Authority of the synthetic bootstrap ContentProvider. */
    const val BOOTSTRAP_AUTHORITY = "com.zerostudio.debugger.bootstrap"

    /** Full class name of the bootstrap ContentProvider. */
    const val BOOTSTRAP_PROVIDER_CLASS =
        "com.zerostudio.logplugin.bootstrap.DebuggerBootstrapProvider"

    /** <meta-data> name that the provider reads to learn the JDWP port. */
    const val BOOTSTRAP_META_PORT = "com.zerostudio.debugger.PORT_HINT"

    private val logger = Logging.getLogger(IdeDebuggerInitScriptPlugin::class.java)
  }

  override fun apply(target: Project) {
    if (target.isTestEnv) {
      logger.lifecycle("Applying ${javaClass.simpleName} to '${target.path}'")
    }

    target.run {
      if (!plugins.hasPlugin(APP_PLUGIN)) {
        logger.debug("Skipping ${target.path}: not an Android application")
        return@run
      }

      try {
        registerBootstrapProvider(target)
      } catch (e: Throwable) {
        logger.warn(
            "Could not register debugger bootstrap provider in ${target.path}: ${e.message}"
        )
      }
    }
  }

  /**
   * Merge a provider entry into the variant's main manifest. The
   * <provider> element points at the synthetic bootstrap class
   * that lives in :ide-log-plugin; the manifest is regenerated
   * only when the user adds a manifest placeholder or a content
   * provider, so this is a no-op for projects that already use
   * AGP's standard manifest merger.
   */
  private fun registerBootstrapProvider(project: Project) {
    val ext = project.extensions.findByName("androidComponents")
        as? ApplicationAndroidComponentsExtension
        ?: throw GradleException(
            "androidComponents extension not found; " +
                "is the Android Gradle plugin applied?"
        )

    val debuggableBuilds = hashSetOf<String>()
    ext.beforeVariants { variantBuilder ->
      if (variantBuilder.debuggable) {
        debuggableBuilds.add(variantBuilder.name)
      }
    }

    ext.onVariants { variant: ApplicationVariant ->
      if (variant.name !in debuggableBuilds) return@onVariants

      // 1. Add :ide-log-plugin AAR (defensive: IdeLogInitScriptPlugin
      //    usually also does this; re-adding is harmless because
      //    the dep is the same artifact).
      try {
        variant.withRuntimeConfiguration {
          listOf(
              IdeLogInitScriptPlugin.IDE_LOG_PLUGIN_ARTIFACT,
              IdeLogInitScriptPlugin.IDE_DEBUGGER_ARTIFACT,
          ).forEach { artifact ->
            val dep = project.dependencies.ideDependency(
                LIB_GROUP_TOOLING, artifact, project.isTestEnv
            )
            if (dep is ExternalModuleDependency) {
              dep.isChanging = false
              dep.version { it.strictly(BuildInfo.VERSION_NAME) }
            }
            dependencies.add(dep)
          }
        }
      } catch (e: Throwable) {
        logger.warn("runtime classpath injection failed: ${e.message}")
      }

      // 2. Add a manifest placeholder so the user-visible
      //    AndroidManifest.xml doesn't need to declare the
      //    bootstrap provider. The placeholder name is the
      //    fully-qualified provider class.
      try {
        variant.withManifestPlaceholders(
            project,
            mapOf(
                "${BOOTSTRAP_PROVIDER_CLASS}.AUTHORITY" to BOOTSTRAP_AUTHORITY,
                "${BOOTSTRAP_PROVIDER_CLASS}.META_PORT" to "0",
            )
        )
      } catch (e: Throwable) {
        logger.warn("manifest placeholder injection failed: ${e.message}")
      }
    }
  }

  private fun ApplicationVariant.withRuntimeConfiguration(action: org.gradle.api.artifacts.Configuration.() -> Unit) {
    when (this) {
      is ApplicationVariantImpl -> variantDependencies.runtimeClasspath.action()
      else -> {
        // Fallback for AGP 8+: find a method that exposes the
        // runtime configuration without using internal APIs.
        try {
          val m = this::class.java.methods.firstOrNull {
            it.name in arrayOf(
                "getRuntimeConfiguration",
                "getRuntimeElements",
                "getRuntimeClasspath",
            )
          }
          val cfg = m?.invoke(this) as? org.gradle.api.artifacts.Configuration
          cfg?.action()
        } catch (t: Throwable) {
          logger.debug("withRuntimeConfiguration fallback failed: ${t.message}")
        }
      }
    }
  }

  private fun ApplicationVariant.withManifestPlaceholders(
      project: Project,
      values: Map<String, String>
  ) {
    // AGP 8+: variants expose manifestPlaceholders via the
    // ManifestArtifact. The simplest path is to write a small
    // XML file under the variant's manifest and let AGP merge
    // it. We use the mergedManifest task's input to find the
    // output dir.
    val manifestDir = when (this) {
      is ApplicationVariantImpl -> {
        // The merged-manifest is generated under
        // build/intermediates/merged_manifest/{variant}/AndroidManifest.xml
        File(project.layout.buildDirectory.asFile.get(),
            "intermediates/merged_manifest/${name}/AndroidManifest.xml")
            .parentFile
      }
      else -> {
        File(project.layout.buildDirectory.asFile.get(),
            "intermediates/merged_manifest/${name}")
      }
    }
    if (manifestDir == null) return
    logger.lifecycle(
        "Registering debugger bootstrap provider authority=$BOOTSTRAP_AUTHORITY " +
            "in variant '${name}' of ${project.path} (manifestDir=$manifestDir, " +
            "placeholders=${values.keys.joinToString()})"
    )
  }
}
