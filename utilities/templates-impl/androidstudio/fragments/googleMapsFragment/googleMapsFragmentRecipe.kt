/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.googleMapsFragment

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addSecretsGradlePlugin
import com.itsaky.androidide.templates.impl.androidstudio.fragments.googleMapsFragment.res.layout.fragmentMapXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.googleMapsFragment.src.app_package.mapFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.googleMapsFragment.src.app_package.mapFragmentKt

fun RecipeExecutor.googleMapsFragmentRecipe(
  moduleData: ModuleTemplateData,
  fragmentClass: String,
  layoutName: String,
  packageName: String,
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val appCompatVersion = moduleData.apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)
  addSecretsGradlePlugin()

  addDependency("com.google.android.gms:play-services-maps:+", toBase = moduleData.isDynamic)
  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")

  mergeXml(androidManifestXml(), manifestOut.resolve("AndroidManifest.xml"))

  save(fragmentMapXml(fragmentClass, packageName), resOut.resolve("layout/${layoutName}.xml"))
  val mapFragment =
    when (projectData.language) {
      Language.Java -> mapFragmentJava(fragmentClass, layoutName, packageName, useAndroidX)
      Language.Kotlin -> mapFragmentKt(fragmentClass, layoutName, packageName, useAndroidX)
    }
  save(mapFragment, srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))
  open(resOut.resolve("layout/${layoutName}.xml"))

  /* Display the API key instructions. */
  open(manifestOut.resolve("AndroidManifest.xml"))
}
