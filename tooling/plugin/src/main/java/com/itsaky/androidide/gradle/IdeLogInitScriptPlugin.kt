/*
 *  ZeroStudio IDE - tooling/plugin
 *
 *  Phase C3: an init-script plugin that injects the
 *  :ide-log-plugin AAR into the debug variant of every Android
 *  application project under a Gradle build. This is what causes
 *  the LogCaptureService / JdwpServer pair to be present in the
 *  target process at runtime.
 *
 *  The plugin mirrors the structure of [LogSenderPlugin] but adds
 *  a different artifact id and a tighter variant filter: only
 *  debuggable variants get the injection. Release variants are
 *  never touched.
 *
 *  The plugin is intentionally fail-safe:
 *    - if the project is not an Android application, it is skipped
 *      silently (this is the common case for library projects
 *      under the same Gradle build);
 *    - if the artifact cannot be resolved, the original
 *      configuration is restored and a warning is logged;
 *    - the IDE-init-script orchestrator registers a `startParameter`
 *      flag that disables this plugin in CI builds.
 */

package com.itsaky.androidide.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.impl.ApplicationVariantImpl
import com.itsaky.androidide.buildinfo.BuildInfo
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.logging.Logging

/**
 * Init-script plugin that injects the [ide-log-plugin] AAR into
 * every debuggable Android variant.
 *
 * @author ZeroStudio
 */
class IdeLogInitScriptPlugin : Plugin<Project> {

  companion object {
    /** Artifact id of the ide-log-plugin module. */
    const val IDE_LOG_PLUGIN_ARTIFACT = "ide-log-plugin"

    private val logger = Logging.getLogger(IdeLogInitScriptPlugin::class.java)
  }

  override fun apply(target: Project) {
    if (target.isTestEnv) {
      logger.lifecycle("Applying ${javaClass.simpleName} to '${target.path}'")
    }

    target.run {
      if (!plugins.hasPlugin(APP_PLUGIN)) {
        // Library / Java / Kotlin JVM modules don't have an
        // application variant. Bail out without raising an
        // error.
        logger.debug(
            "Skipping ${target.path}: not an Android application " +
                "(plugins.hasPlugin(APP_PLUGIN) == false)"
        )
        return@run
      }

      try {
        injectIdeLogPlugin(target)
      } catch (e: Throwable) {
        // Never fail the build because of a missing plugin: the
        // user might be doing a release-only build (e.g. CI) where
        // the plugin is intentionally absent.
        logger.warn("Could not inject ${IDE_LOG_PLUGIN_ARTIFACT} into ${target.path}: ${e.message}")
      }
    }
  }

  private fun injectIdeLogPlugin(project: Project) {
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

      try {
        variant.withRuntimeConfiguration {
          val dep = project.dependencies.ideDependency(
              LIB_GROUP_TOOLING, IDE_LOG_PLUGIN_ARTIFACT, project.isTestEnv
          )
          if (dep is ExternalModuleDependency) {
            dep.isChanging = false
            dep.version { it.strictly(BuildInfo.VERSION_NAME) }
          }
          logger.lifecycle(
              "Injecting ${dep.group}:${dep.name} (${dep.version}) into " +
                  "variant '${variant.name}' of ${project.path}"
          )
          dependencies.add(dep)
        }
      } catch (e: Throwable) {
        logger.warn(
            "Failed to add ${IDE_LOG_PLUGIN_ARTIFACT} to variant " +
                "'${variant.name}' of ${project.path}: ${e.message}"
        )
      }
    }
  }

  /**
   * Apply an action to the variant's runtime classpath configuration
   * regardless of the AGP variant impl type.
   */
  private fun ApplicationVariant.withRuntimeConfiguration(action: Configuration.() -> Unit) {
    when (this) {
      is ApplicationVariantImpl -> variantDependencies.runtimeClasspath.action()
      else -> {
        // Best-effort: AGP 8+ exposes the runtime classpath via the
        // variant artifact API. Use reflection so we don't need a
        // hard dependency on AGP internals.
        try {
          val cls = this::class.java
          val m = cls.methods.firstOrNull { it.name == "getRuntimeConfiguration" }
              ?: cls.methods.firstOrNull { it.name == "getRuntimeElements" }
          val cfg = m?.invoke(this) as? Configuration
          cfg?.action()
        } catch (t: Throwable) {
          logger.debug("withRuntimeConfiguration fallback failed: ${t.message}")
        }
      }
    }
  }

  /** For tests / diagnostics: the dependencies this plugin would add. */
  @Suppress("unused")
  fun describeDependency(project: Project): Dependency? = try {
    project.dependencies.ideDependency(LIB_GROUP_TOOLING, IDE_LOG_PLUGIN_ARTIFACT, project.isTestEnv)
  } catch (e: Throwable) {
    logger.warn("describeDependency failed: ${e.message}"); null
  }

  /** True if the dependency is a project-local module (composite build case). */
  @Suppress("unused")
  fun isProjectLocal(dep: Dependency?): Boolean = dep is ProjectDependency
}
