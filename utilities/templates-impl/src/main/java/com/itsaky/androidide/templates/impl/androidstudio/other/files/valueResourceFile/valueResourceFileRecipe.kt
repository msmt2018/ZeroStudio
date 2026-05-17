package com.itsaky.androidide.templates.impl.androidstudio.other.files.valueResourceFile

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.other.files.valueResourceFile.res.valuesXml
import java.io.File

fun RecipeExecutor.valueResourceFileRecipe(moduleData: ModuleTemplateData, fileName: String) {
  val out = File(moduleData.projectDir, "src/main/res/values").also { it.mkdirs() }
  save(valuesXml(), File(out, "${fileName}.xml"))
}
