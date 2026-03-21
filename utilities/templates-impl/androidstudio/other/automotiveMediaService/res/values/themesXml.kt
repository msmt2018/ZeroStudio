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

package com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMediaService.res.values

fun themesXml(customThemeName: String) =
  """
<?xml version="1.0" encoding="utf-8"?>
<resources>
  <style name="${customThemeName}" parent="android:Theme.Material.Light">
        <!-- colorPrimaryDark is currently used in Android Auto for:
             - App background
             - Drawer right side ("more" custom actions) background
             - Notification icon badge tinting
             - Overview “now playing” icon tinting
         -->
        <item name="android:colorPrimaryDark">#ffbf360c</item>

        <!-- colorAccent is used in Android Auto for:
             - Spinner
             - progress bar
             - floating action button background (Play/Pause in media apps)
         -->
        <item name="android:colorAccent">#00B8D4</item>
    </style>
</resources>
"""
