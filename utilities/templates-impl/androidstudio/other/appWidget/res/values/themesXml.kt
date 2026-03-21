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
package com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.values

import com.android.tools.idea.wizard.template.ThemesData

fun themesXml(themesData: ThemesData) =
  """<resources>
    <style name="${getParentAppWidgetTheme(themesData.main.name)}" parent="@android:style/Theme.DeviceDefault">
        <!-- Radius of the outer bound of widgets to make the rounded corners -->
        <item name="appWidgetRadius">16dp</item>
        <!--
        Radius of the inner view's bound of widgets to make the rounded corners.
        It needs to be 8dp or less than the value of appWidgetRadius
        -->
        <item name="appWidgetInnerRadius">8dp</item>
    </style>

    <style name="${getAppWidgetTheme(themesData.main.name)}"
        parent="${getParentAppWidgetTheme(themesData.main.name)}">
        <!-- Apply padding to avoid the content of the widget colliding with the rounded corners -->
        <item name="appWidgetPadding">16dp</item>
    </style>
</resources>
"""

fun getAppWidgetTheme(themeName: String) = "${themeName}.AppWidgetContainer"

fun getParentAppWidgetTheme(themeName: String) = "${themeName}.AppWidgetContainerParent"
