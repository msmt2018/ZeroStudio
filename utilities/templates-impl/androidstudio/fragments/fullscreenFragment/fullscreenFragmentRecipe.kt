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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.fullscreenFragment

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addViewBindingSupport
import com.itsaky.androidide.templates.impl.androidstudio.fragments.fullscreenFragment.res.layout.fragmentFullscreenXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.fullscreenFragment.res.values.fullScreenColorsXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.fullscreenFragment.res.values.fullscreenAttrs
import com.itsaky.androidide.templates.impl.androidstudio.fragments.fullscreenFragment.res.values.fullscreenStyles
import com.itsaky.androidide.templates.impl.androidstudio.fragments.fullscreenFragment.res.values.fullscreenThemes
import com.itsaky.androidide.templates.impl.androidstudio.fragments.fullscreenFragment.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.fullscreenFragment.res.values_night.fullscreenThemes as fullscreenThemesNight
import com.itsaky.androidide.templates.impl.androidstudio.fragments.fullscreenFragment.src.app_package.fullscreenFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.fullscreenFragment.src.app_package.fullscreenFragmentKt

fun RecipeExecutor.fullscreenFragmentRecipe(
  moduleData: ModuleTemplateData,
  fragmentClass: String,
  layoutName: String,
  packageName: String,
) {
  val (projectData, srcOut, resOut) = moduleData
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  mergeXml(stringsXml(), resOut.resolve("values/strings.xml"))
  mergeXml(fullscreenAttrs(), resOut.resolve("values/attrs.xml"))
  mergeXml(fullScreenColorsXml(), resOut.resolve("values/colors.xml"))
  mergeXml(fullscreenStyles(moduleData.themesData), resOut.resolve("values/styles.xml"))
  mergeXml(fullscreenThemes(moduleData.themesData), resOut.resolve("values/themes.xml"))
  mergeXml(fullscreenThemesNight(moduleData.themesData), resOut.resolve("values-night/themes.xml"))
  save(fragmentFullscreenXml(fragmentClass, packageName, moduleData.themesData), resOut.resolve("layout/${layoutName}.xml"))

  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val fullscreenFragment =
    when (projectData.language) {
      Language.Java ->
        fullscreenFragmentJava(
          fragmentClass = fragmentClass,
          layoutName = layoutName,
          packageName = packageName,
          applicationPackage = projectData.applicationPackage,
          useAndroidX = useAndroidX,
          isViewBindingSupported = isViewBindingSupported,
        )
      Language.Kotlin ->
        fullscreenFragmentKt(
          fragmentClass = fragmentClass,
          layoutName = layoutName,
          packageName = packageName,
          applicationPackage = projectData.applicationPackage,
          useAndroidX = useAndroidX,
          isViewBindingSupported = isViewBindingSupported,
        )
    }
  save(fullscreenFragment, srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))

  open(resOut.resolve("layout/${layoutName}.xml"))
  open(srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))
}
