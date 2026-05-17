package com.itsaky.androidide.templates.impl.androidstudio.other.androidManifest

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor
import java.io.File

fun RecipeExecutor.androidManifestRecipe(moduleData: ModuleTemplateData, sourceProviderName: String) {
  val base = File(moduleData.projectDir, "src/${sourceProviderName}").also { it.mkdirs() }
  save(androidManifestXml(), File(base, "AndroidManifest.xml"))
}
