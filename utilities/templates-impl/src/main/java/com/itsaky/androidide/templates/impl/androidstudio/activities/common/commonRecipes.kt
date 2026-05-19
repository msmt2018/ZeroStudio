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
package com.itsaky.androidide.templates.impl.androidstudio.activities.common

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.PackageName
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.ThemeData
import com.itsaky.androidide.templates.ThemesData
import com.itsaky.androidide.templates.ViewBindingSupport
import com.itsaky.androidide.templates.getMaterialComponentName
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.res.layout.simpleLayoutXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.res.menu.simpleMenu
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.res.values.manifestStrings
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.res.values.themeStyles
import com.itsaky.androidide.templates.renderIf
import java.io.File

fun RecipeExecutor.generateSimpleMenu(
    packageName: PackageName,
    activityClass: String,
    resDir: File,
    menuName: String,
) {
  val simpleMenuStrings =
      """
      <resources>
          <string name="action_settings">Settings</string>
      </resources>
      """
          .trimIndent()

  save(simpleMenu(packageName, activityClass), resDir.resolve("menu/$menuName.xml"))
  mergeXml(simpleMenuStrings, resDir.resolve("values/strings.xml"))
}

fun RecipeExecutor.generateThemeStyles(themeData: ThemeData, useAndroidX: Boolean, resOut: File) {
  addMaterialDependency(useAndroidX)
  if (!themeData.exists) {
    mergeXml(themeStyles(themeData.name, useAndroidX), resOut.resolve("values/themes.xml"))
  }
}

fun RecipeExecutor.generateManifest(
    moduleData: ModuleTemplateData,
    activityClass: String,
    packageName: String,
    isLauncher: Boolean,
    hasNoActionBar: Boolean,
    activityThemeName: String = moduleData.themesData.noActionBar.name,
    isNewModule: Boolean = moduleData.isNewModule,
    isLibrary: Boolean = moduleData.isLibrary,
    manifestOut: File = moduleData.manifestDir,
    baseFeatureResOut: File = moduleData.baseFeature?.resDir ?: moduleData.resDir,
    generateActivityTitle: Boolean,
    isResizeable: Boolean = false,
    libraryName: String = "",
    taskAffinity: String? = null,
) {
  generateManifestStrings(activityClass, baseFeatureResOut, isNewModule, generateActivityTitle)

  val manifest =
      androidManifestXml(
          isNewModule = isNewModule,
          hasNoActionBar = hasNoActionBar,
          packageName = packageName,
          activityClass = activityClass,
          isLauncher = isLauncher,
          isLibraryProject = isLibrary,
          activityThemeName = activityThemeName,
          generateActivityTitle = generateActivityTitle,
          isResizeable = isResizeable,
          libraryName = libraryName,
          taskAffinity = taskAffinity,
      )

  mergeXml(manifest, manifestOut.resolve("AndroidManifest.xml"))
}

fun RecipeExecutor.generateSimpleLayout(
    moduleData: ModuleTemplateData,
    activityClass: String,
    simpleLayoutName: String,
    containerId: String?,
    openLayout: Boolean = true,
    includeCppSupport: Boolean = false,
) {
  val projectData = moduleData.projectTemplateData
  val appCompatVersion = moduleData.apis.appCompatVersion
  val resOut = moduleData.resDir

  addDependency("com.android.support:appcompat-v7:$appCompatVersion.+")
  addDependency("com.android.support.constraint:constraint-layout:+")

  // TODO(qumeric): check if we sometimes need to pass appBarLayoutName
  val simpleLayout =
      simpleLayoutXml(
          moduleData.isNewModule,
          includeCppSupport,
          projectData.androidXSupport,
          moduleData.packageName,
          activityClass,
          null,
          containerId,
      )

  val layoutFile = resOut.resolve("layout/$simpleLayoutName.xml")
  save(simpleLayout, layoutFile)

  if (openLayout) {
    open(layoutFile)
  }
}

fun RecipeExecutor.generateNoActionBarStyles(
    baseFeatureResOut: File?,
    resDir: File,
    themesData: ThemesData,
) {
  val implicitParentTheme = true // TODO(qumeric)
  val parentBlock = renderIf(!implicitParentTheme) { """parent="${themesData.main.name}"""" }

  val noActionBarBlock =
      renderIf(!themesData.noActionBar.exists) {
        """
    <style name="${themesData.noActionBar.name}" $parentBlock>
      <item name="windowActionBar">false</item>
      <item name="windowNoTitle">true</item>
    </style>
    """
      }

  val appBarOverlayBlock =
      renderIf(!themesData.appBarOverlay.exists) {
        """<style name="${themesData.appBarOverlay.name}" parent="ThemeOverlay.AppCompat.Dark.ActionBar" />"""
      }

  val popupOverlayBlock =
      renderIf(!themesData.popupOverlay.exists) {
        """<style name="${themesData.popupOverlay.name}" parent="ThemeOverlay.AppCompat.Light" />"""
      }

  val noActionBarStylesContent =
      """
    <resources>
      $noActionBarBlock
      $appBarOverlayBlock
      $popupOverlayBlock
    </resources>
  """
          .trimIndent()

  if (baseFeatureResOut != null) {
    mergeXml(noActionBarStylesContent, baseFeatureResOut.resolve("values/themes.xml"))
  } else {
    mergeXml(noActionBarStylesContent, resDir.resolve("values/themes.xml"))
  }
}

fun RecipeExecutor.generateManifestStrings(
    activityClass: String,
    baseFeatureResOut: File,
    isNewModule: Boolean,
    generateActivityTitle: Boolean,
) {
  mergeXml(
      manifestStrings(activityClass, isNewModule, generateActivityTitle),
      baseFeatureResOut.resolve("values/strings.xml"),
  )
}

fun RecipeExecutor.generateAppBar(
    moduleData: ModuleTemplateData,
    activityClass: String,
    packageName: String,
    simpleLayoutName: String,
    appBarLayoutName: String,
    appCompatVersion: Int = moduleData.apis.appCompatVersion,
    resDir: File = moduleData.resDir,
    baseFeatureResOut: File? = moduleData.baseFeature?.resDir,
    themesData: ThemesData = moduleData.themesData,
    useAndroidX: Boolean,
    isMaterial3: Boolean,
) {
  val coordinatorLayout =
      getMaterialComponentName("android.support.design.widget.CoordinatorLayout", useAndroidX)
  val layoutTag =
      getMaterialComponentName("android.support.design.widget.AppBarLayout", useAndroidX)

  val appBarLayout =
      """
    <?xml version="1.0" encoding="utf-8"?>
    <$coordinatorLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="http://schemas.android.com/apk/res-auto"
        xmlns:tools="http://schemas.android.com/tools"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        ${renderIf(isMaterial3) { "android:fitsSystemWindows=\"true\"" }}
        tools:context="$packageName.$activityClass">

        <$layoutTag
            android:layout_height="wrap_content"
            android:layout_width="match_parent"
            ${renderIf(isMaterial3) { "android:fitsSystemWindows=\"true\"" }}
            ${renderIf(!isMaterial3) { "android:theme=\"@style/${themesData.appBarOverlay.name}\"" }}>

            <${if (!isMaterial3)
                getMaterialComponentName("android.support.v7.widget.Toolbar", useAndroidX)
               else
                "com.google.android.material.appbar.MaterialToolbar"}
                android:id="@+id/toolbar"
                android:layout_width = "match_parent"
                ${renderIf(!isMaterial3) {"""
                  android:background="?attr/colorPrimary"
                  app:popupTheme="@style/${themesData.popupOverlay.name}"
                """ }}
                android:layout_height = "?attr/actionBarSize" />

      </$layoutTag>

      <include layout="@layout/$simpleLayoutName"/>

      <${getMaterialComponentName("android.support.design.widget.FloatingActionButton", useAndroidX)}
      android:id="@+id/fab"
      android:layout_width="wrap_content"
      android:layout_height="wrap_content"
      android:layout_gravity="bottom|end"
      android:layout_marginEnd="@dimen/fab_margin"
      android:layout_marginBottom="16dp"
      app:srcCompat="@android:drawable/ic_dialog_email" />

      </$coordinatorLayout>
      """
          .trimIndent()

  if (!isMaterial3) {
    addDependency("com.android.support:appcompat-v7:$appCompatVersion.+")
    addDependency("com.android.support:design:$appCompatVersion.+")
  }

  save(appBarLayout, resDir.resolve("layout/$appBarLayoutName.xml"))

  mergeXml(appBarDimens(16), resDir.resolve("values/dimens.xml"))
  mergeXml(appBarDimens(48), resDir.resolve("values-land/dimens.xml"))
  mergeXml(appBarDimens(48), resDir.resolve("values-w600dp/dimens.xml"))
  mergeXml(appBarDimens(200), resDir.resolve("values-w1240dp/dimens.xml"))

  if (!isMaterial3) generateNoActionBarStyles(baseFeatureResOut, resDir, themesData)
}

private fun appBarDimens(fabMargin: Int) =
    """<resources>
      <dimen name="fab_margin">${fabMargin}dp</dimen>
   </resources>
"""

fun RecipeExecutor.addLifecycleDependencies(useAndroidX: Boolean) {
  if (useAndroidX) {
    addDependency("androidx.lifecycle:lifecycle-livedata-ktx:+")
    addDependency("androidx.lifecycle:lifecycle-viewmodel-ktx:+")
  } else {
    addDependency("android.arch.lifecycle:livedata:+")
    addDependency("android.arch.lifecycle:viewmodel:+")
  }
}

fun RecipeExecutor.addViewBindingSupport(viewBindingSupport: ViewBindingSupport, value: Boolean) {
  when (viewBindingSupport) {
    ViewBindingSupport.SUPPORTED_3_6 -> {
      setViewBinding(value)
    }
    ViewBindingSupport.SUPPORTED_4_0_MORE -> {
      setBuildFeature("viewBinding", value)
    }
    else -> {}
  }
}

fun RecipeExecutor.addSecretsGradlePlugin() {
  addClasspathDependency(
      "com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:+",
      minRev = "2.0.0",
  )
  applyPlugin(
      "com.google.android.libraries.mapsplatform.secrets-gradle-plugin",
      "+",
      minRev = "2.0.0",
  )
}

fun RecipeExecutor.generateMaterial3Themes(themeName: String, resOut: File) {
  // The contents of the themes are common with following files to have appropriate themes when
  // an Activity is created under a flavor
  // com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleThemesMaterial3
  // com.android.tools.idea.npw.module.recipes.androidModule.res.values_night.androidModuleThemesMaterial3
  mergeXml(
      """<resources xmlns:tools="http://schemas.android.com/tools">
  <!-- Base application theme. -->
  <style name="Base.${themeName}" parent="Theme.Material3.DayNight.NoActionBar">
    <!-- Customize your light theme here. -->
    <!-- <item name="colorPrimary">@color/my_light_primary</item> -->
  </style>

  <style name="$themeName" parent="Base.${themeName}" />
</resources>""",
      resOut.resolve("values/themes.xml"),
  )

  mergeXml(
      """<resources xmlns:tools="http://schemas.android.com/tools">
  <!-- Base application theme. -->
  <style name="Base.${themeName}" parent="Theme.Material3.DayNight.NoActionBar">
    <!-- Customize your dark theme here. -->
    <!-- <item name="colorPrimary">@color/my_dark_primary</item> -->
  </style>
</resources>""",
      resOut.resolve("values-night/themes.xml"),
  )
}
