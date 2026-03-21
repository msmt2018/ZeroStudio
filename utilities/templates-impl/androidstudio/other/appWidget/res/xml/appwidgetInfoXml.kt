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

package com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.xml

import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.Placement
import com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.Resizeable
import com.android.tools.idea.wizard.template.renderIf

fun appwidgetInfoXml(
  minHeightDp: Int,
  minWidthDp: Int,
  minHeightCells: Int,
  minWidthCells: Int,
  className: String,
  configurable: Boolean,
  layoutName: String,
  packageName: String,
  placement: Placement,
  resizeable: Resizeable,
  withSFeatures: Boolean = false,
): String {
  val resizeableBlock =
    when (resizeable) {
      Resizeable.Both -> "android:resizeMode=\"horizontal|vertical\""
      Resizeable.Horizontal -> "android:resizeMode=\"horizontal\""
      Resizeable.Vertical -> "android:resizeMode=\"vertical\""
      Resizeable.None -> ""
    }
  val placementBlock =
    when (placement) {
      Placement.Both -> "android:widgetCategory=\"home_screen|keyguard\""
      Placement.Homescreen -> "android:widgetCategory=\"home_screen\""
      Placement.Keyguard -> "android:widgetCategory=\"keyguard\""
    }

  val previewLayoutBlock = renderIf(withSFeatures) { """android:previewLayout="@layout/${layoutName}"""" }
  val targetCellBlock =
    renderIf(withSFeatures) {
      """android:targetCellHeight="$minHeightCells"
       android:targetCellWidth="$minWidthCells""""
    }

  return """
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="${minWidthDp}dp"
    android:minHeight="${minHeightDp}dp"
    android:updatePeriodMillis="86400000"
    android:previewImage="@drawable/example_appwidget_preview"
    android:initialLayout="@layout/${layoutName}"
    android:description="@string/app_widget_description"
${renderIf(configurable) {
    """
    android:configure="${packageName}.${className}ConfigureActivity"
"""
  }}
    $resizeableBlock
    $placementBlock
    $previewLayoutBlock
    $targetCellBlock
    android:initialKeyguardLayout="@layout/${layoutName}"
/>"""
}
