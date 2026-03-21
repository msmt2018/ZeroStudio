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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addViewBindingSupport
import com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment.res.layout.fragmentAdmobXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment.res.values.dimensXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment.src.app_package.adMobBannerAdFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment.src.app_package.adMobBannerAdFragmentKt
import com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment.src.app_package.adMobInterstitialAdFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment.src.app_package.adMobInterstitialAdFragmentKt

fun RecipeExecutor.googleAdMobAdsFragmentRecipe(
  moduleData: ModuleTemplateData,
  fragmentClass: String,
  layoutName: String,
  adFormat: AdFormat,
  packageName: String,
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  val applicationPackage = projectData.applicationPackage
  addAllKotlinDependencies(moduleData)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  addDependency("com.google.android.gms:play-services-ads:+", toBase = moduleData.isDynamic)
  addDependency("com.android.support.constraint:constraint-layout:+")

  mergeXml(androidManifestXml(), manifestOut.resolve("AndroidManifest.xml"))
  mergeXml(stringsXml(adFormat), resOut.resolve("values/strings.xml"))
  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
  save(fragmentAdmobXml(adFormat, fragmentClass, packageName, useAndroidX), resOut.resolve("layout/${layoutName}.xml"))

  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  when (adFormat) {
    AdFormat.Interstitial -> {
      val adMobInterstitialAdFragment =
        when (projectData.language) {
          Language.Java ->
            adMobInterstitialAdFragmentJava(
              applicationPackage = applicationPackage,
              fragmentClass = fragmentClass,
              layoutName = layoutName,
              packageName = packageName,
              useAndroidX = useAndroidX,
              isViewBindingSupported = isViewBindingSupported,
            )
          Language.Kotlin ->
            adMobInterstitialAdFragmentKt(
              applicationPackage = applicationPackage,
              fragmentClass = fragmentClass,
              layoutName = layoutName,
              packageName = packageName,
              useAndroidX = useAndroidX,
              isViewBindingSupported = isViewBindingSupported,
            )
        }
      save(adMobInterstitialAdFragment, srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))
    }
    AdFormat.Banner -> {
      val adMobBannerAdFragment =
        when (projectData.language) {
          Language.Java ->
            adMobBannerAdFragmentJava(
              applicationPackage = applicationPackage,
              fragmentClass = fragmentClass,
              layoutName = layoutName,
              packageName = packageName,
              useAndroidX = useAndroidX,
              isViewBindingSupported = isViewBindingSupported,
            )
          Language.Kotlin ->
            adMobBannerAdFragmentKt(
              applicationPackage = applicationPackage,
              fragmentClass = fragmentClass,
              layoutName = layoutName,
              packageName = packageName,
              useAndroidX = useAndroidX,
              isViewBindingSupported = isViewBindingSupported,
            )
        }
      save(adMobBannerAdFragment, srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))
    }
  }

  open(srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))
  open(resOut.resolve("layout/${layoutName}.xml"))
}
