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

package com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMediaService

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMediaService.res.values.themesXml
import com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMediaService.res.xml.automotiveAppDescXml
import com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMediaService.src.app_package.musicServiceJava
import com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMediaService.src.app_package.musicServiceKt
import java.io.File

fun RecipeExecutor.automotiveMediaServiceRecipe(
  moduleData: ModuleTemplateData,
  mediaBrowserServiceName: String,
  packageName: String,
  useCustomTheme: Boolean,
  customThemeName: String,
) {
  val projectData = moduleData.projectTemplateData
  val appCompatVersion = moduleData.apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
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
    serviceManifestOut = projectData.rootDir.resolve(sharedModule).resolve(relativeManifestDir)
    serviceSrcOut = projectData.rootDir.resolve(sharedModule).resolve(relativeSrcDir).resolve(sharedModule)
    serviceResOut = projectData.rootDir.resolve(sharedModule).resolve(relativeResDir)
    sharedPackageName = "$packageName.$sharedModule"

    save(
      // TODO(b/419624430): This should be created through a gradle build model instead of
      // creating from text,
      // creating this way given that this is the only place to create a build.gradle for anther
      // module.
      source =
        buildGradle(
          projectData = projectData,
          packageName = sharedPackageName,
          buildApi = apis.buildApi,
          minApi = apis.minApi,
          targetApi = apis.targetApi,
        ),
      to = projectData.rootDir.resolve(sharedModule).resolve("build.gradle"),
    )
    setJavaKotlinCompileOptions(projectData.language == Language.Kotlin, projectData.rootDir.resolve(sharedModule))
    addDependency(
      mavenCoordinate = "com.android.support:support-media-compat:${appCompatVersion}.+",
      moduleDir = projectData.rootDir.resolve(sharedModule),
    )
    // TODO: It may be better to not rely on the hard-coded module name
    addModuleDependency("implementation", sharedModule, projectData.rootDir.resolve("mobile"))
    addModuleDependency("implementation", sharedModule, projectData.rootDir.resolve("automotive"))
    addModuleDependency("implementation", sharedModule, projectData.rootDir.resolve("app"))
  } else {
    serviceManifestOut = moduleData.manifestDir
    serviceSrcOut = moduleData.srcDir
    serviceResOut = moduleData.resDir
    sharedPackageName = packageName
    addDependency("com.android.support:support-media-compat:${appCompatVersion}.+")
  }
  /* Create Media Service */
  mergeXml(
    androidManifestXml(customThemeName, mediaBrowserServiceName, sharedPackageName, useCustomTheme),
    serviceManifestOut.resolve("AndroidManifest.xml"),
  )

  if (useCustomTheme) {
    mergeXml(themesXml(customThemeName), serviceResOut.resolve("values/themes.xml"))
  }
  mergeXml(automotiveAppDescXml(), serviceResOut.resolve("xml/automotive_app_desc.xml"))

  val musicService =
    when (projectData.language) {
      Language.Java -> musicServiceJava(mediaBrowserServiceName, sharedPackageName, useAndroidX)
      Language.Kotlin -> musicServiceKt(mediaBrowserServiceName, sharedPackageName, useAndroidX)
    }
  save(musicService, serviceSrcOut.resolve("${mediaBrowserServiceName}.${ktOrJavaExt}"))
  open(serviceSrcOut.resolve("${mediaBrowserServiceName}.${ktOrJavaExt}"))
  if (useCustomTheme) {
    open(serviceResOut.resolve("values/themes.xml"))
  }
}
