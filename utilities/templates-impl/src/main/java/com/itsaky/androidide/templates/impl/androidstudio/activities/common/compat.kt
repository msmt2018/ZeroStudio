package com.itsaky.androidide.templates.impl.androidstudio.activities.common

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.base.models.Dependency
import java.io.File

typealias PackageName = String

data class ThemeData(val name: String, val exists: Boolean = false)
data class ThemesData(
  val main: ThemeData = ThemeData("Theme.App"),
  val noActionBar: ThemeData = ThemeData("Theme.App.NoActionBar"),
  val appBarOverlay: ThemeData = ThemeData("ThemeOverlay.AppCompat.Dark.ActionBar"),
  val popupOverlay: ThemeData = ThemeData("ThemeOverlay.AppCompat.Light"),
)

enum class ViewBindingSupport { NONE, SUPPORTED }
enum class TemplateKotlinSupport { REQUIRED, SUPPORTED }

fun renderIf(condition: Boolean, block: () -> String): String = if (condition) block() else ""
fun getMaterialComponentName(legacy: String, useAndroidX: Boolean): String =
  if (!useAndroidX) legacy else legacy.replace("android.support.design.widget", "com.google.android.material.appbar")

fun RecipeExecutor.addDependency(dependency: String) {}
fun RecipeExecutor.addMaterialDependency(useAndroidX: Boolean) {}
fun RecipeExecutor.addAllKotlinDependencies(moduleData: ModuleTemplateData) {}
fun RecipeExecutor.addViewBindingSupport(moduleData: ModuleTemplateData, support: ViewBindingSupport) {}
fun RecipeExecutor.addDependency(dep: Dependency) {}
fun RecipeExecutor.open(file: File) {}
fun RecipeExecutor.mergeXml(xml: String, out: File) {
  out.parentFile?.mkdirs()
  if (!out.exists()) {
    val content = if (xml.trimStart().startsWith("<resources")) xml else "<resources>\n${xml.trim()}\n</resources>"
    save(content, out)
    return
  }
  save(out.readText().replace("</resources>", "\n${xml.trim()}\n</resources>"), out)
}
