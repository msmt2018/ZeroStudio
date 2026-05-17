package com.itsaky.androidide.templates.impl.androidstudio.other.service

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.SrcSet
import com.itsaky.androidide.templates.impl.androidstudio.other.service.src.app_package.serviceJava
import com.itsaky.androidide.templates.impl.androidstudio.other.service.src.app_package.serviceKt
import java.io.File

fun RecipeExecutor.serviceRecipe(moduleData: ModuleTemplateData, className: String, isExported: Boolean, isEnabled: Boolean) {
  val srcOut = moduleData.srcFolder(SrcSet.Main).resolve("java").also { it.mkdirs() }
  val manifestOut = File(moduleData.projectDir, "src/main")
  val packageName = moduleData.packageName
  val ktOrJavaExt = if (moduleData.language == Language.Kotlin) "kt" else "java"

  save(androidManifestXml(className, isEnabled, isExported, packageName), manifestOut.resolve("AndroidManifest.xml"))
  val service = when (moduleData.language) {
    Language.Java -> serviceJava(className, packageName)
    Language.Kotlin -> serviceKt(className, packageName)
  }
  save(service, srcOut.resolve("${className}.${ktOrJavaExt}"))
}
