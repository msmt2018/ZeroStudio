/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.itsaky.androidide.templates.impl.androidstudio.other.files.shortcutResourceFile.res.xml

fun shortcutXml() =
  """
<?xml version ="1.0" encoding="utf-8"?>
<shortcuts>
    <!-- Add shortcuts that launch your app to a specific screen or task. -->
    <!-- Learn more at https://developer.android.com/guide/topics/ui/shortcuts/creating-shortcuts -->
    <!-- <shortcut android:shortcutId="SHORTCUT_ID" -->
    <!--     android:enabled="true" -->
    <!--     android:shortcutShortLabel="SHORTCUT_SHORT_LABEL" -->
    <!--     android:shortcutLongLabel="SHORTCUT_LONG_LABEL" -->
    <!--     android:shortcutDisabledMessage="SHORTCUT_DISABLED_MESSAGE"> -->
    <!--     <intent -->
    <!--         android:action="android.intent.action.VIEW" -->
    <!--         android:targetClass="REPLACE_IT_WITH_FULL_QUALIFIED_CLASS" -->
    <!--         android:targetPackage="REPLACE_IT_WITH_TARGET_PACKAGE" /> -->
    <!-- </shortcut> -->

    <!-- Integrate with Google Assistant App Actions for launching your app with various voice commands. -->
    <!-- Learn more at: https://developers.google.com/assistant/app/overview -->
    <!-- <capability android:name="actions.intent.OPEN_APP_FEATURE"> -->
    <!--     Provide query fulfillment instructions for this capability, or bind it to a shortcut. -->
    <!--     Learn more at: https://developers.google.com/assistant/app/action-schema -->
    <!-- </capability> -->
</shortcuts>
"""
