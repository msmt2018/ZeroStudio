package com.itsaky.androidide.templates.impl.androidstudio.other.sliceProvider

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.SrcSet
import com.itsaky.androidide.templates.impl.androidstudio.other.sliceProvider.src.app_package.sliceProviderJava
import com.itsaky.androidide.templates.impl.androidstudio.other.sliceProvider.src.app_package.sliceProviderKt
import java.io.File

fun RecipeExecutor.sliceProviderRecipe(moduleData: ModuleTemplateData, className: String, authorities: String, hostUrl: String, pathPrefix: String) {
  val srcOut = moduleData.srcFolder(SrcSet.Main).resolve("java").also { it.mkdirs() }
  val manifestOut = File(moduleData.projectDir, "src/main")
  val packageName = moduleData.packageName
  val ktOrJavaExt = moduleData.language.extension

  save(androidManifestXml(authorities, className, hostUrl, packageName, pathPrefix), manifestOut.resolve("AndroidManifest.xml"))
  val sliceProvider = when (moduleData.language) {
    Language.Java -> sliceProviderJava(className, packageName, pathPrefix)
    Language.Kotlin -> sliceProviderKt(className, packageName, pathPrefix)
  }
  save(sliceProvider, srcOut.resolve("${className}.${ktOrJavaExt}"))
}
