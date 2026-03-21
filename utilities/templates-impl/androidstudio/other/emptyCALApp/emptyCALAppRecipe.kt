/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

package com.itsaky.androidide.templates.impl.androidstudio.other.emptyCalApp

import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.other.emptyCalApp.manifests.automotiveManifestXml
import com.itsaky.androidide.templates.impl.androidstudio.other.emptyCalApp.manifests.mobileManifestXml
import com.itsaky.androidide.templates.impl.androidstudio.other.emptyCalApp.res.xml.automotiveAppDescXml
import com.itsaky.androidide.templates.impl.androidstudio.other.emptyCalApp.src.app_package.carAppScreenKt
import com.itsaky.androidide.templates.impl.androidstudio.other.emptyCalApp.src.app_package.carAppServiceKt
import com.itsaky.androidide.templates.impl.androidstudio.other.emptyCalApp.src.app_package.carAppSessionKt
import java.io.File

fun RecipeExecutor.emptyCalAppRecipe(
  moduleData: ModuleTemplateData,
  carAppServiceName: String,
  sessionName: String,
  screenName: String,
  packageName: String,
) {
  val projectData = moduleData.projectTemplateData
  val useAndroidX = projectData.androidXSupport
  val ktOrJavaExt = projectData.language.extension

  // The template is dependent upon `androidx` -> `useAndroidX` should always be true
  if (!useAndroidX) {
    throw IllegalStateException("The Car App Library template requires AndroidX support.")
  }

  addAllKotlinDependencies(moduleData)

  val sharedModule = "shared"
  val moduleRootDirString = "${moduleData.rootDir}${File.separatorChar}"
  val relativeSrcDir = moduleData.srcDir.toString().split(moduleRootDirString)[1]
  val relativeManifestDir = moduleData.manifestDir.toString().split(moduleRootDirString)[1]
  val relativeResDir = moduleData.resDir.toString().split(moduleRootDirString)[1]
  val apis = moduleData.apis
  lateinit var serviceManifestOut: File
  lateinit var serviceSrcOut: File
  lateinit var serviceResOut: File
  lateinit var sharedPackageName: String

  if (projectData.isNewProject) {
    addIncludeToSettings(sharedModule)
    val sharedModuleDir = projectData.rootDir.resolve(sharedModule)
    serviceManifestOut = projectData.rootDir.resolve(sharedModule).resolve(relativeManifestDir)
    serviceSrcOut = projectData.rootDir.resolve(sharedModule).resolve(relativeSrcDir).resolve(sharedModule)
    sharedPackageName = "$packageName.$sharedModule"
    serviceResOut = projectData.rootDir.resolve(sharedModule).resolve(relativeResDir)

    save(
      source =
        buildGradle(
          projectData = projectData,
          packageName = sharedPackageName,
          buildApi = apis.buildApi,
          minApi = apis.minApi,
          targetApi = apis.targetApi,
        ),
      to = sharedModuleDir.resolve("build.gradle"),
    )
    setJavaKotlinCompileOptions(projectData.language == Language.Kotlin, sharedModuleDir)

    projectData.includedFormFactorNames[FormFactor.Mobile]?.forEach { moduleName ->
      addModuleDependency("implementation", sharedModule, projectData.rootDir.resolve(moduleName))
      addDependency("androidx.car.app:app-projected:1.4.0", moduleDir = projectData.rootDir.resolve(moduleName))
      mergeXml(mobileManifestXml(), projectData.rootDir.resolve(moduleName).resolve(relativeManifestDir).resolve("AndroidManifest.xml"))
    }

    projectData.includedFormFactorNames[FormFactor.Car]?.forEach { moduleName ->
      addModuleDependency("implementation", sharedModule, projectData.rootDir.resolve(moduleName))
      addDependency("androidx.car.app:app-automotive:1.7.0", moduleDir = projectData.rootDir.resolve(moduleName))
      mergeXml(automotiveManifestXml(), projectData.rootDir.resolve(moduleName).resolve(relativeManifestDir).resolve("AndroidManifest.xml"))
    }

    addDependency("androidx.car.app:app:1.4.0", moduleDir = sharedModuleDir)
  } else {
    serviceManifestOut = moduleData.manifestDir
    serviceSrcOut = moduleData.srcDir
    serviceResOut = moduleData.resDir
    sharedPackageName = packageName
    addDependency("androidx.car.app:app:1.4.0")
  }

  mergeXml(androidManifestXml(carAppServiceName, sharedPackageName), serviceManifestOut.resolve("AndroidManifest.xml"))

  mergeXml(automotiveAppDescXml(), serviceResOut.resolve("xml/automotive_app_desc.xml"))

  val serviceFile = serviceSrcOut.resolve("${carAppServiceName}.${ktOrJavaExt}")
  val sessionFile = serviceSrcOut.resolve("${sessionName}.${ktOrJavaExt}")
  val screenFile = serviceSrcOut.resolve("${screenName}.${ktOrJavaExt}")

  save(carAppServiceKt(carAppServiceName, sessionName, sharedPackageName), serviceFile)
  save(carAppSessionKt(sessionName, screenName, sharedPackageName), sessionFile)
  save(carAppScreenKt(screenName, sharedPackageName), screenFile)

  open(serviceFile)
  open(sessionFile)
  open(screenFile)
}
