package com.itsaky.androidide.templates.impl.androidstudio.activities.common

import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.ModuleTemplateBuilder
import com.itsaky.androidide.templates.base.models.Dependency
import java.io.File

typealias PackageName = String

data class ThemeData(val name: String, val exists: Boolean = false)
data class ThemesData(
    val main: ThemeData,
    val noActionBar: ThemeData,
    val appBarOverlay: ThemeData,
    val popupOverlay: ThemeData,
)

enum class ViewBindingSupport { SUPPORTED_3_6, SUPPORTED_4_0_MORE, NOT_SUPPORTED }

fun renderIf(condition: Boolean, block: () -> String): String = if (condition) block() else ""

fun getMaterialComponentName(legacy: String, useAndroidX: Boolean): String {
  if (!useAndroidX) return legacy
  return legacy
      .replace("android.support.v4.", "androidx.")
      .replace("android.support.v7.", "androidx.appcompat.")
      .replace("android.support.constraint.", "androidx.constraintlayout.")
      .replace("android.arch.lifecycle.", "androidx.lifecycle.")
      .replace("android.support.annotation.", "androidx.annotation.")
      .replace("android.support.design.widget.FloatingActionButton", "com.google.android.material.floatingactionbutton.FloatingActionButton")
      .replace("android.support.design.widget.AppBarLayout", "com.google.android.material.appbar.AppBarLayout")
      .replace("android.support.design.widget.CoordinatorLayout", "androidx.coordinatorlayout.widget.CoordinatorLayout")
}

fun RecipeExecutor.mergeXml(source: String, to: File) = save(source, to)
fun RecipeExecutor.open(file: File) = Unit

fun ModuleTemplateBuilder.addDependency(mavenCoordinate: String, minRev: String? = null) {
  val parts = mavenCoordinate.split(':')
  if (parts.size < 2) return
  val group = parts[0]
  val artifact = parts[1]
  val version = if (parts.size >= 3) parts[2] else (minRev ?: "+")
  addDependency(Dependency(group, artifact, version))
}

fun ModuleTemplateBuilder.addDependency(mavenCoordinate: String, configuration: String, minRev: String? = null) {
  addDependency(mavenCoordinate, minRev)
}

fun RecipeExecutor.addDependency(mavenCoordinate: String, minRev: String? = null) = Unit
fun RecipeExecutor.addDependency(mavenCoordinate: String) = Unit
fun RecipeExecutor.addDependency(mavenCoordinate: String, configuration: String, minRev: String? = null) = Unit

fun RecipeExecutor.setBuildFeature(name: String, value: Boolean) = Unit
fun RecipeExecutor.addClasspathDependency(mavenCoordinate: String) = Unit
fun RecipeExecutor.applyPlugin(plugin: String) = Unit
fun RecipeExecutor.addAllKotlinDependencies(moduleData: Any) = Unit
fun RecipeExecutor.addMaterialDependency(useAndroidX: Boolean) = Unit
