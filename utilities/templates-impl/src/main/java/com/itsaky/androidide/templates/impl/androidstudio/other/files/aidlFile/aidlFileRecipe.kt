package com.itsaky.androidide.templates.impl.androidstudio.other.files.aidlFile

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.other.files.aidlFile.src.app_package.interfaceAidl
import java.io.File

fun RecipeExecutor.aidlFileRecipe(moduleData: ModuleTemplateData, interfaceName: String) {
  val aidlOut = File(moduleData.projectDir, "src/main/aidl").also { it.mkdirs() }
  save(interfaceAidl(interfaceName, moduleData.packageName), File(aidlOut, "${interfaceName}.aidl"))
}
