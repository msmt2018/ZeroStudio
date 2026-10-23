package com.itsaky.androidide.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * 根工程专属插件，确保仅在 rootProject 上生效
 */
class AndroidIDERootProjectPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    require(target == target.rootProject) { 
      "This plugin must only be applied to the root project." 
    }
  }
}