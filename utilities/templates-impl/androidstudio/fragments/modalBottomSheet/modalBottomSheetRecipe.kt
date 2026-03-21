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
package com.itsaky.androidide.templates.impl.androidstudio.fragments.modalBottomSheet

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addMaterialDependency
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addViewBindingSupport
import com.itsaky.androidide.templates.impl.androidstudio.fragments.listFragment.ColumnCount
import com.itsaky.androidide.templates.impl.androidstudio.fragments.modalBottomSheet.res.layout.fragmentItemListDialogItemXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.modalBottomSheet.res.layout.fragmentItemListDialogXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.modalBottomSheet.res.values.dimensXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.modalBottomSheet.src.app_package.itemListDialogFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.modalBottomSheet.src.app_package.itemListDialogFragmentKt

fun RecipeExecutor.modalBottomSheetRecipe(
  moduleData: ModuleTemplateData,
  packageName: String,
  objectKind: String,
  fragmentClass: String,
  columnCount: ColumnCount,
  itemLayout: String,
  listLayout: String,
) {
  val (projectData, srcOut, resOut, _) = moduleData
  val appCompatVersion = moduleData.apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  val applicationPackage = projectData.applicationPackage
  addAllKotlinDependencies(moduleData)

  addDependency("com.android.support:support-v4:${appCompatVersion}.+")
  addDependency("com.android.support:design:${appCompatVersion}.+")
  addDependency("com.android.support:recyclerview-v7:${appCompatVersion}.+")
  addMaterialDependency(useAndroidX)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  save(fragmentItemListDialogXml(fragmentClass, itemLayout, packageName, useAndroidX), resOut.resolve("layout/${listLayout}.xml"))
  save(fragmentItemListDialogItemXml(), resOut.resolve("layout/${itemLayout}.xml"))

  val columnCountNumber = columnCount.ordinal + 1
  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val itemListDialogFragment =
    when (projectData.language) {
      Language.Java ->
        itemListDialogFragmentJava(
          applicationPackage = applicationPackage,
          columnCount = columnCountNumber,
          fragmentClass = fragmentClass,
          itemLayout = itemLayout,
          listLayout = listLayout,
          objectKind = objectKind,
          packageName = packageName,
          useAndroidX = useAndroidX,
          isViewBindingSupported = isViewBindingSupported,
        )
      Language.Kotlin ->
        itemListDialogFragmentKt(
          applicationPackage = applicationPackage,
          columnCount = columnCountNumber,
          fragmentClass = fragmentClass,
          itemLayout = itemLayout,
          listLayout = listLayout,
          objectKind = objectKind,
          packageName = packageName,
          useAndroidX = useAndroidX,
          isViewBindingSupported = isViewBindingSupported,
        )
    }
  save(itemListDialogFragment, srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))

  open(resOut.resolve("layout/${listLayout}.xml"))
  open(srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))

  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
}
