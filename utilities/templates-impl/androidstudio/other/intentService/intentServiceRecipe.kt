/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.itsaky.androidide.templates.impl.androidstudio.other.intentService

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.other.intentService.src.app_package.intentServiceJava
import com.itsaky.androidide.templates.impl.androidstudio.other.intentService.src.app_package.intentServiceKt

fun RecipeExecutor.intentServiceRecipe(moduleData: ModuleTemplateData, className: String, includeHelper: Boolean) {

  val (projectData, srcOut, _, manifestOut) = moduleData
  val ktOrJavaExt = projectData.language.extension
  val packageName = moduleData.packageName
  addAllKotlinDependencies(moduleData)

  mergeXml(androidManifestXml(className, packageName), manifestOut.resolve("AndroidManifest.xml"))
  val intentService =
    when (projectData.language) {
      Language.Java -> intentServiceJava(className, includeHelper, packageName)
      Language.Kotlin -> intentServiceKt(className, includeHelper, escapeKotlinIdentifier(packageName))
    }
  save(intentService, srcOut.resolve("${className}.${ktOrJavaExt}"))

  open(srcOut.resolve("${className}.${ktOrJavaExt}"))
}
