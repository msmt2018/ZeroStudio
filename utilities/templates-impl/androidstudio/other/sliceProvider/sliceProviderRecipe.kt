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

package com.itsaky.androidide.templates.impl.androidstudio.other.sliceProvider

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.other.sliceProvider.src.app_package.sliceProviderJava
import com.itsaky.androidide.templates.impl.androidstudio.other.sliceProvider.src.app_package.sliceProviderKt

fun RecipeExecutor.sliceProviderRecipe(
  moduleData: ModuleTemplateData,
  className: String,
  authorities: String,
  hostUrl: String,
  pathPrefix: String,
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val ktOrJavaExt = projectData.language.extension
  val packageName = moduleData.packageName
  addAllKotlinDependencies(moduleData)

  addDependency("androidx.annotation:annotation:+")
  addDependency("androidx.slice:slice-builders:+")
  mergeXml(androidManifestXml(authorities, className, hostUrl, packageName, pathPrefix), manifestOut.resolve("AndroidManifest.xml"))
  val sliceProvider =
    when (projectData.language) {
      Language.Java -> sliceProviderJava(className, packageName, pathPrefix)
      Language.Kotlin -> sliceProviderKt(className, packageName, pathPrefix)
    }
  save(sliceProvider, srcOut.resolve("${className}.${ktOrJavaExt}"))

  open(srcOut.resolve("${className}.${ktOrJavaExt}"))
}
