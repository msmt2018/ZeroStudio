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
package com.itsaky.androidide.templates.impl.androidstudio.fragments.fullscreenFragment.res.values

import com.android.tools.idea.wizard.template.ThemesData
import com.itsaky.androidide.templates.impl.androidstudio.activities.fullscreenActivity.res.values.getFullscreenButtonBarStyle

fun fullscreenStyles(themesData: ThemesData) =
  """<resources>
    <style name="${getFullscreenButtonBarStyle(themesData.main.name)}" parent="">
        <item name="android:background">@color/black_overlay</item>
        <item name="android:buttonBarStyle">?android:attr/buttonBarStyle</item>
    </style>
</resources>
"""
