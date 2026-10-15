package com.itsaky.androidide.plugins

import com.itsaky.androidide.build.config.isFDroidBuild
import com.itsaky.androidide.plugins.util.isAndroidModule
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Manages plugins applied on the IDE's project modules.
 *
 * @author Akash Yadav
 */
class AndroidIDEPlugin : Plugin<Project> {

  override fun apply(target: Project) = target.run {
    if (project.path == rootProject.path) {
      throw GradleException("Cannot apply ${AndroidIDEPlugin::class.simpleName} to root project")
    }

    if (!project.buildFile.exists() || !project.buildFile.isFile) {
      return@run
    }

    if (isAndroidModule && !isFDroidBuild) {
      // setup signing configuration
      plugins.apply(SigningConfigPlugin::class.java)
    }

    if (isFDroidBuild && project.plugins.hasPlugin("com.itsaky.androidide.core-app")) {
      logger.warn("Building for F-Droid with configuration:")
      
      val androidExt = extensions.findByName("android")
      if (androidExt != null) {
          try {
              val defaultConfig = androidExt.javaClass.getMethod("getDefaultConfig").invoke(androidExt)
              val appId = defaultConfig.javaClass.getMethod("getApplicationId").invoke(defaultConfig)
              val vName = defaultConfig.javaClass.getMethod("getVersionName").invoke(defaultConfig)
              val vCode = defaultConfig.javaClass.getMethod("getVersionCode").invoke(defaultConfig)
              
              logger.warn("applicationId = ${appId ?: ""}")
              logger.warn("versionName = ${vName ?: ""}")
              logger.warn("versionCode = ${vCode ?: ""}")
          } catch (e: Exception) {
              logger.warn("Could not read android configuration dynamically: ${e.message}")
          }
      }
      logger.warn("--- x --- x ---")
    }

    val taskName =
        when {
          isAndroidModule -> "testDebugUnitTest"
          else -> "test"
        }

    logger.info("${project.path} will run task '$taskName' for tests in CI")

    project.tasks.register("runTestsInCI") { dependsOn(taskName) }
  }
}
