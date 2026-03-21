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

import com.android.tools.idea.wizard.template.renderIf

fun androidManifestXml(
  customThemeName: String,
  mediaBrowserServiceName: String,
  sharedPackageName: String,
  useCustomTheme: Boolean,
): String {
  val customThemeBlock =
    renderIf(useCustomTheme) {
      """
        <!--
             Use this meta data to override the theme from which Android Auto will
             look for colors. If you don"t set this, Android Auto will look
             for color attributes in your application theme.
        -->
        <meta-data
            android:name="com.google.android.gms.car.application.theme"
            android:resource="@style/${customThemeName}" />
  """
    }

  return """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application android:appCategory="audio">

        <meta-data
                android:name="com.google.android.gms.car.application"
                android:resource="@xml/automotive_app_desc" />

$customThemeBlock

        <!-- Main music service, provides media browsing and media playback services to
         consumers through MediaBrowserService and MediaSession. Consumers connect to it through
         MediaBrowser (for browsing) and MediaController (for playback control) -->
        <service
            android:name="${sharedPackageName}.${mediaBrowserServiceName}"
            android:exported="true">
            <intent-filter>
                <action android:name="android.media.browse.MediaBrowserService" />
            </intent-filter>
        </service>

    </application>

</manifest>
"""
}
