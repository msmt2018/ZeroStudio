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

package com.itsaky.androidide.templates.impl.androidstudio.other.customView

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.camelCaseToUnderlines
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.other.customView.res.layout.sampleXml
import com.itsaky.androidide.templates.impl.androidstudio.other.customView.res.values.attrsXml
import com.itsaky.androidide.templates.impl.androidstudio.other.customView.res.values.colorsXml
import com.itsaky.androidide.templates.impl.androidstudio.other.customView.res.values.stylesXml
import com.itsaky.androidide.templates.impl.androidstudio.other.customView.res.values_night.stylesXml as stylesXmlNight
import com.itsaky.androidide.templates.impl.androidstudio.other.customView.src.app_package.customViewJava
import com.itsaky.androidide.templates.impl.androidstudio.other.customView.src.app_package.customViewKt

fun RecipeExecutor.customViewRecipe(moduleData: ModuleTemplateData, packageName: String, viewClass: String) {
  val (projectData, srcOut, resOut) = moduleData
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  val snakeCaseViewClass = camelCaseToUnderlines(viewClass)
  val themeName = moduleData.themesData.main.name
  mergeXml(attrsXml(viewClass), resOut.resolve("values/attrs_${snakeCaseViewClass}.xml"))
  mergeXml(colorsXml(), resOut.resolve("values/colors.xml"))
  mergeXml(stylesXml(themeName), resOut.resolve("values/styles.xml"))
  mergeXml(stylesXmlNight(themeName), resOut.resolve("values-night/styles.xml"))
  save(sampleXml(packageName, themeName, viewClass), resOut.resolve("layout/sample_${snakeCaseViewClass}.xml"))

  val customView =
    when (projectData.language) {
      Language.Java -> customViewJava(projectData.applicationPackage, packageName, viewClass)
      Language.Kotlin -> customViewKt(projectData.applicationPackage, packageName, viewClass)
    }
  save(customView, srcOut.resolve("${viewClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${viewClass}.${ktOrJavaExt}"))
  open(resOut.resolve("layout/sample_${snakeCaseViewClass}.xml"))
}
