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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.scrollFragment

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.fragments.scrollFragment.res.layout.simpleXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.scrollFragment.res.values.dimensXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.scrollFragment.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.scrollFragment.src.app_package.scrollFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.scrollFragment.src.app_package.scrollFragmentKt

fun RecipeExecutor.scrollFragmentRecipe(moduleData: ModuleTemplateData, fragmentClass: String, layoutName: String, packageName: String) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  mergeXml(stringsXml(), resOut.resolve("values/strings.xml"))
  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))

  save(simpleXml(fragmentClass, packageName, useAndroidX), resOut.resolve("layout/${layoutName}.xml"))
  open(resOut.resolve("layout/${layoutName}.xml"))

  val scrollFragment =
    when (projectData.language) {
      Language.Java -> scrollFragmentJava(fragmentClass, layoutName, packageName, useAndroidX)
      Language.Kotlin -> scrollFragmentKt(fragmentClass, layoutName, packageName, useAndroidX)
    }
  save(scrollFragment, srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))
}
