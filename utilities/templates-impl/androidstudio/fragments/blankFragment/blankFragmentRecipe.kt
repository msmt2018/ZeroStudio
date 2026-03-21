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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.blankFragment

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.fragments.blankFragment.res.layout.fragmentBlankXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.blankFragment.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.blankFragment.src.app_package.blankFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.blankFragment.src.app_package.blankFragmentKt

fun RecipeExecutor.blankFragmentRecipe(moduleData: ModuleTemplateData, className: String, layoutName: String) {
  val (projectData, srcOut, resOut, _) = moduleData
  val packageName = moduleData.packageName
  val applicationPackage = moduleData.projectTemplateData.applicationPackage
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  mergeXml(stringsXml(), resOut.resolve("values/strings.xml"))
  save(fragmentBlankXml(className, packageName), resOut.resolve("layout/${layoutName}.xml"))
  open(resOut.resolve("layout/${layoutName}.xml"))
  val blankFragment =
    when (projectData.language) {
      Language.Java -> blankFragmentJava(applicationPackage, className, layoutName, packageName, useAndroidX)
      Language.Kotlin -> blankFragmentKt(applicationPackage, className, layoutName, packageName, useAndroidX)
    }
  save(blankFragment, srcOut.resolve("${className}.${ktOrJavaExt}"))
  open(srcOut.resolve("${className}.${ktOrJavaExt}"))
}
