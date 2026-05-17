package com.itsaky.androidide.templates.impl.androidstudio.other.files.shortcutResourceFile

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.other.files.shortcutResourceFile.res.xml.shortcutXml
import java.io.File

fun RecipeExecutor.shortcutsResourceFileRecipe(moduleData: ModuleTemplateData, fileName: String) {
  val out = File(moduleData.projectDir, "src/main/res/xml").also { it.mkdirs() }
  save(shortcutXml(), File(out, "${fileName}.xml"))
}
