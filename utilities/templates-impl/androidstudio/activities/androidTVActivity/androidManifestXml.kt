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

package com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity

/**
 * Creates the AndroidManifest for Android TV Leanback configuration explicitly setting touch and UI requirements.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun androidManifestXml(
  activityClass: String,
  detailsActivity: String,
  isLibrary: Boolean,
  isNewModule: Boolean,
  packageName: String,
  themeName: String,
): String {
  val labelBlock =
    if (isNewModule) "android:label=\"@string/app_name\"" else "android:label=\"@string/title_${activityClass}\""
  val launcher = !isLibrary
  val intentFilterBlock =
    if (launcher) {
      """
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
  """
    } else ""
  return """
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission
        android:name="android.permission.INTERNET" />

    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />

    <uses-feature android:name="android.software.leanback"
        android:required="true" />

    <application android:theme="$themeName">

        <activity android:name="${packageName}.${activityClass}"
            android:exported="$launcher"
            android:icon="@drawable/app_icon_your_company"
            android:logo="@drawable/app_icon_your_company"
            android:banner="@drawable/app_icon_your_company"
            android:screenOrientation="landscape"
            $labelBlock>
            $intentFilterBlock
        </activity>

        <activity android:name="${packageName}.${detailsActivity}" android:exported="false" />
        <activity android:name="${packageName}.PlaybackActivity" android:exported="false" />
        <activity android:name="${packageName}.BrowseErrorActivity" android:exported="false" />

    </application>

</manifest>
"""
}