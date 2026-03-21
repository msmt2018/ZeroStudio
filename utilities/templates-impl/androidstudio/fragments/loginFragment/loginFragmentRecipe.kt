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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addLifecycleDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addViewBindingSupport
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.res.layout.fragmentLoginXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.res.values.dimensXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.data.loginDataSourceJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.data.loginDataSourceKt
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.data.loginRepositoryJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.data.loginRepositoryKt
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.data.model.loggedInUserJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.data.model.loggedInUserKt
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.data.resultJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.data.resultKt
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.ui.login.loggedInUserViewJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.ui.login.loggedInUserViewKt
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.ui.login.loginFormStateJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.ui.login.loginFormStateKt
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.ui.login.loginFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.ui.login.loginFragmentKt
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.ui.login.loginResultJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.ui.login.loginResultKt
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.ui.login.loginViewModelFactoryJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.ui.login.loginViewModelFactoryKt
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.ui.login.loginViewModelJava
import com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.ui.login.loginViewModelKt

fun RecipeExecutor.loginFragmentRecipe(moduleData: ModuleTemplateData, fragmentClass: String, layoutName: String, packageName: String) {

  val (projectData, srcOut, resOut, _) = moduleData
  val appCompatVersion = moduleData.apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")
  addDependency("com.android.support:design:${appCompatVersion}.+")
  addDependency("com.android.support:support-annotations:${appCompatVersion}.+")
  addDependency("com.android.support.constraint:constraint-layout:+")
  addLifecycleDependencies(useAndroidX)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
  mergeXml(stringsXml(), resOut.resolve("values/strings.xml"))
  save(
    fragmentLoginXml(fragmentClass, moduleData.apis.minApi.apiLevel, packageName, useAndroidX),
    resOut.resolve("layout/${layoutName}.xml"),
  )

  val isViewBinndingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val loginFragment =
    when (projectData.language) {
      Language.Java ->
        loginFragmentJava(
          fragmentClass = fragmentClass,
          layoutName = layoutName,
          packageName = packageName,
          applicationPackage = projectData.applicationPackage,
          useAndroidX = useAndroidX,
          isViewBindingSupported = isViewBinndingSupported,
        )
      Language.Kotlin ->
        loginFragmentKt(
          fragmentClass = fragmentClass,
          layoutName = layoutName,
          packageName = packageName,
          applicationPackage = projectData.applicationPackage,
          useAndroidX = useAndroidX,
          isViewBindingSupported = isViewBinndingSupported,
        )
    }
  save(loginFragment, srcOut.resolve("ui/login/${fragmentClass}.${ktOrJavaExt}"))

  val loginViewModel =
    when (projectData.language) {
      Language.Java -> loginViewModelJava(packageName, useAndroidX)
      Language.Kotlin -> loginViewModelKt(packageName, useAndroidX)
    }
  save(loginViewModel, srcOut.resolve("ui/login/LoginViewModel.${ktOrJavaExt}"))

  val loginViewModelFactory =
    when (projectData.language) {
      Language.Java -> loginViewModelFactoryJava(packageName, useAndroidX)
      Language.Kotlin -> loginViewModelFactoryKt(packageName, useAndroidX)
    }
  save(loginViewModelFactory, srcOut.resolve("ui/login/LoginViewModelFactory.${ktOrJavaExt}"))

  val loggedInUser =
    when (projectData.language) {
      Language.Java -> loggedInUserJava(packageName)
      Language.Kotlin -> loggedInUserKt(packageName)
    }
  save(loggedInUser, srcOut.resolve("data/model/LoggedInUser.${ktOrJavaExt}"))

  val loginDataSource =
    when (projectData.language) {
      Language.Java -> loginDataSourceJava(packageName)
      Language.Kotlin -> loginDataSourceKt(packageName)
    }
  save(loginDataSource, srcOut.resolve("data/LoginDataSource.${ktOrJavaExt}"))

  val loginRepository =
    when (projectData.language) {
      Language.Java -> loginRepositoryJava(packageName)
      Language.Kotlin -> loginRepositoryKt(packageName)
    }
  save(loginRepository, srcOut.resolve("data/LoginRepository.${ktOrJavaExt}"))

  val result =
    when (projectData.language) {
      Language.Java -> resultJava(packageName)
      Language.Kotlin -> resultKt(packageName)
    }
  save(result, srcOut.resolve("data/Result.${ktOrJavaExt}"))

  val loginFormState =
    when (projectData.language) {
      Language.Java -> loginFormStateJava(packageName, useAndroidX)
      Language.Kotlin -> loginFormStateKt(packageName)
    }
  save(loginFormState, srcOut.resolve("ui/login/LoginFormState.${ktOrJavaExt}"))

  val loginResult =
    when (projectData.language) {
      Language.Java -> loginResultJava(packageName, useAndroidX)
      Language.Kotlin -> loginResultKt(packageName)
    }
  save(loginResult, srcOut.resolve("ui/login/LoginResult.${ktOrJavaExt}"))

  val loggedInUserView =
    when (projectData.language) {
      Language.Java -> loggedInUserViewJava(packageName)
      Language.Kotlin -> loggedInUserViewKt(packageName)
    }
  save(loggedInUserView, srcOut.resolve("ui/login/LoggedInUserView.${ktOrJavaExt}"))

  open(srcOut.resolve("ui/login/${fragmentClass}.${ktOrJavaExt}"))
  open(resOut.resolve("layout/${layoutName}.xml"))
}
