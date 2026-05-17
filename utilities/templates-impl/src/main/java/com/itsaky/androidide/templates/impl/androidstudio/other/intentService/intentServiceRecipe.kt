package com.itsaky.androidide.templates.impl.androidstudio.other.intentService

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.SrcSet
import com.itsaky.androidide.templates.impl.androidstudio.other.intentService.src.app_package.intentServiceJava
import com.itsaky.androidide.templates.impl.androidstudio.other.intentService.src.app_package.intentServiceKt
import java.io.File

fun RecipeExecutor.intentServiceRecipe(moduleData: ModuleTemplateData, className: String, includeHelper: Boolean) {
  val srcOut = moduleData.srcFolder(SrcSet.Main).resolve("java").also { it.mkdirs() }
  val manifestOut = File(moduleData.projectDir, "src/main")
  val packageName = moduleData.packageName
  val ktOrJavaExt = moduleData.language.extension

  save(androidManifestXml(className, packageName), manifestOut.resolve("AndroidManifest.xml"))
  val intentService = when (moduleData.language) {
    Language.Java -> intentServiceJava(className, includeHelper, packageName)
    Language.Kotlin -> intentServiceKt(className, includeHelper, packageName)
  }
  save(intentService, srcOut.resolve("${className}.${ktOrJavaExt}"))
}
