package com.itsaky.androidide.templates.impl.androidstudio.other.files.layoutResourceFile

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.other.files.layoutResourceFile.res.layoutXml
import java.io.File

fun RecipeExecutor.layoutResourceFileRecipe(moduleData: ModuleTemplateData, layoutName: String, rootTag: String) {
  val resOut = File(moduleData.projectDir, "src/main/res/layout").also { it.mkdirs() }
  save(layoutXml(rootTag), File(resOut, "${layoutName}.xml"))
}
