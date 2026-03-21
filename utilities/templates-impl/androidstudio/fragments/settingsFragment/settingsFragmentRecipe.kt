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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.settingsFragment

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.fragments.settingsFragment.res.values.arraysXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.settingsFragment.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.settingsFragment.res.xml.rootPreferencesXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.settingsFragment.src.app_package.singleScreenSettingsFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.settingsFragment.src.app_package.singleScreenSettingsFragmentKt

fun RecipeExecutor.settingsFragmentRecipe(moduleData: ModuleTemplateData, fragmentClass: String, packageName: String) {

  val (projectData, srcOut, resOut, _) = moduleData
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  addDependency(mavenCoordinate = "androidx.preference:preference:+", minRev = "1.1+")
  mergeXml(stringsXml(), resOut.resolve("values/strings.xml"))
  mergeXml(arraysXml(), resOut.resolve("values/arrays.xml"))
  mergeXml(rootPreferencesXml(), resOut.resolve("xml/root_preferences.xml"))

  val singleScreenSettingsFragment =
    when (projectData.language) {
      Language.Java -> singleScreenSettingsFragmentJava(fragmentClass, packageName)
      Language.Kotlin -> singleScreenSettingsFragmentKt(fragmentClass, packageName)
    }
  save(singleScreenSettingsFragment, srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))
}
