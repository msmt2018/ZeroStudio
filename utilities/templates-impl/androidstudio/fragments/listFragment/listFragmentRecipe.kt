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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.listFragment

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addViewBindingSupport
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.src.app_package.placeholder.placeholderContentJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.src.app_package.placeholder.placeholderContentKt
import com.itsaky.androidide.templates.impl.androidstudio.fragments.listFragment.res.layout.fragmentListXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.listFragment.res.layout.itemListContentXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.listFragment.res.values.dimensXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.listFragment.src.app_package.listFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.listFragment.src.app_package.listFragmentKt
import com.itsaky.androidide.templates.impl.androidstudio.fragments.listFragment.src.app_package.recyclerViewAdapterJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.listFragment.src.app_package.recyclerViewAdapterKt

fun RecipeExecutor.listFragmentRecipe(
  moduleData: ModuleTemplateData,
  packageName: String,
  fragmentClass: String,
  columnCount: ColumnCount,
  fragmentLayout: String,
  fragmentLayoutList: String,
  adapterClassName: String,
) {
  val (projectData, srcOut, resOut, _) = moduleData
  val appCompatVersion = moduleData.apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  val applicationPackage = projectData.applicationPackage
  addAllKotlinDependencies(moduleData)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  addDependency("com.android.support:support-v4:${appCompatVersion}.+")
  addDependency("com.android.support:recyclerview-v7:${appCompatVersion}.+")

  save(fragmentListXml(fragmentClass, fragmentLayout, packageName, useAndroidX), resOut.resolve("layout/${fragmentLayoutList}.xml"))
  save(itemListContentXml(), resOut.resolve("layout/${fragmentLayout}.xml"))

  val columnCountNumber = columnCount.ordinal + 1
  val listFragment =
    when (projectData.language) {
      Language.Java ->
        listFragmentJava(
          adapterClassName,
          applicationPackage,
          columnCountNumber,
          fragmentClass,
          fragmentLayoutList,
          packageName,
          useAndroidX,
        )
      Language.Kotlin ->
        listFragmentKt(adapterClassName, applicationPackage, columnCountNumber, fragmentClass, fragmentLayoutList, packageName, useAndroidX)
    }
  save(listFragment, srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))

  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val recyclerViewAdapter =
    when (projectData.language) {
      Language.Java ->
        recyclerViewAdapterJava(
          adapterClassName = adapterClassName,
          fragmentLayout = fragmentLayout,
          packageName = packageName,
          applicationPackage = projectData.applicationPackage,
          useAndroidX = useAndroidX,
          isViewBindingSupported = isViewBindingSupported,
        )
      Language.Kotlin ->
        recyclerViewAdapterKt(
          adapterClassName = adapterClassName,
          applicationPackage = applicationPackage,
          fragmentLayout = fragmentLayout,
          packageName = packageName,
          useAndroidX = useAndroidX,
          isViewBindingSupported = isViewBindingSupported,
        )
    }
  save(recyclerViewAdapter, srcOut.resolve("${adapterClassName}.${ktOrJavaExt}"))

  val placeholderContent =
    when (projectData.language) {
      Language.Java -> placeholderContentJava(packageName)
      Language.Kotlin -> placeholderContentKt(packageName)
    }
  save(placeholderContent, srcOut.resolve("placeholder/PlaceholderContent.${ktOrJavaExt}"))

  open(srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))
  open(resOut.resolve("layout/${fragmentLayoutList}.xml"))

  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
}
