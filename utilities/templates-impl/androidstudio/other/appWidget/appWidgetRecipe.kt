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

package com.itsaky.androidide.templates.impl.androidstudio.other.appWidget

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.camelCaseToUnderlines
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addViewBindingSupport
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.drawable_v21.appWidgetBackgroundXml
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.drawable_v21.appWidgetInnerViewBackgroundXml
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.layout.appwidgetConfigureXml
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.layout.appwidgetXml
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.values.attrsXml
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.values.colorsXml
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.values.dimensXml
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.values.stylesXml
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.values.themesXml
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.values_v21.stylesXml as stylesXmlV21
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.values_v31.stylesXml as stylesXmlV31
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.values_v31.themesXml as themesXmlV31
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.xml.appwidgetInfoXml
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.src.app_package.appWidgetConfigureActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.src.app_package.appWidgetConfigureActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.src.app_package.appWidgetJava
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.src.app_package.appWidgetKt
import java.io.File

fun RecipeExecutor.appWidgetRecipe(
  moduleData: ModuleTemplateData,
  className: String,
  placement: Placement,
  resizable: Resizeable,
  minWidth: MinimumCells,
  minHeight: MinimumCells,
  configurable: Boolean,
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val ktOrJavaExt = projectData.language.extension
  val packageName = moduleData.packageName
  val layoutName = camelCaseToUnderlines(className)
  addAllKotlinDependencies(moduleData)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  mergeXml(androidManifestXml(className, configurable, layoutName, packageName), manifestOut.resolve("AndroidManifest.xml"))

  copy(File("app-widget").resolve("drawable-nodpi"), resOut.resolve("drawable-nodpi"))
  save(appWidgetBackgroundXml(), resOut.resolve("drawable-v21/app_widget_background.xml"))
  save(appWidgetInnerViewBackgroundXml(), resOut.resolve("drawable-v21/app_widget_inner_view_background.xml"))
  save(appwidgetXml(moduleData.themesData), resOut.resolve("layout/${layoutName}.xml"))

  if (configurable) {
    save(appwidgetConfigureXml(), resOut.resolve("layout/${layoutName}_configure.xml"))
  }

  val minHeightCells = minHeight.name.toInt()
  val minWidthCells = minWidth.name.toInt()
  val minWidthDp = -30 + 70 * minWidthCells
  val minHeightDp = -30 + 70 * minHeightCells
  save(
    appwidgetInfoXml(
      minHeightDp = minHeightDp,
      minWidthDp = minWidthDp,
      minHeightCells = minHeightCells,
      minWidthCells = minWidthCells,
      className = className,
      configurable = configurable,
      layoutName = layoutName,
      packageName = packageName,
      placement = placement,
      resizeable = resizable,
      withSFeatures = moduleData.apis.targetApi.apiLevel >= 31,
    ),
    resOut.resolve("xml/${layoutName}_info.xml"),
  )
  mergeXml(stringsXml(configurable), resOut.resolve("values/strings.xml"))
  mergeXml(attrsXml(), resOut.resolve("values/attrs.xml"))
  mergeXml(colorsXml(), resOut.resolve("values/colors.xml"))
  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
  mergeXml(themesXml(moduleData.themesData), resOut.resolve("values/themes.xml"))
  mergeXml(stylesXml(moduleData.themesData), resOut.resolve("values/styles.xml"))
  mergeXml(stylesXmlV21(moduleData.themesData), resOut.resolve("values-v21/styles.xml"))
  if (moduleData.apis.targetApi.apiLevel >= 31) {
    // android:clipToOutline is only available with S SDK
    mergeXml(stylesXmlV31(moduleData.themesData), resOut.resolve("values-v31/styles.xml"))
    // Restrict to generate the themes for v31 because
    // @android:dimen/system_app_widget_background_radius and
    // @android:dimen/system_app_widget_internal_padding are only available
    // with S SDK
    mergeXml(themesXmlV31(moduleData.themesData, forDarkMode = false), resOut.resolve("values-v31/themes.xml"))
    mergeXml(themesXmlV31(moduleData.themesData, forDarkMode = true), resOut.resolve("values-night-v31/themes.xml"))
  }

  val appWidget =
    when (projectData.language) {
      Language.Java -> appWidgetJava(projectData.applicationPackage, className, configurable, layoutName, packageName)
      Language.Kotlin -> appWidgetKt(projectData.applicationPackage, className, configurable, layoutName, packageName)
    }
  save(appWidget, srcOut.resolve("${className}.${ktOrJavaExt}"))

  if (configurable) {
    val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
    val appWidgetConfigureActivity =
      when (projectData.language) {
        Language.Java ->
          appWidgetConfigureActivityJava(
            applicationPackage = projectData.applicationPackage,
            className = className,
            layoutName = layoutName,
            packageName = packageName,
            isViewBindingSupported = isViewBindingSupported,
          )
        Language.Kotlin ->
          appWidgetConfigureActivityKt(
            applicationPackage = projectData.applicationPackage,
            className = className,
            layoutName = layoutName,
            packageName = packageName,
            isViewBindingSupported = isViewBindingSupported,
          )
      }
    save(appWidgetConfigureActivity, srcOut.resolve("${className}ConfigureActivity.${ktOrJavaExt}"))
  }

  open(srcOut.resolve("${className}.${ktOrJavaExt}"))
}
