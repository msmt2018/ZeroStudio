/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.itsaky.androidide.templates.impl.androidstudio.activities.aiGlassesActivity

/**
 * Generates the AndroidManifest.xml configuration required for the AI Glasses Activity.
 * This configures the activity as an exported entry point and enforces the required 
 * display category for XR projection.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 * 
 * Modifications:
 * - Migrated to AndroidIDE template system.
 * - Updated package namespace to align with AndroidIDE template hierarchy.
 */

/**
 * Returns the XML string representation of the AndroidManifest.xml for AI Glasses Activity.
 * 
 * @param activityClass The class name of the target activity.
 * @param packageName The application's base package name.
 * @return Formatted XML string of the manifest.
 */
fun aiGlassesActivityManifestXml(activityClass: String, packageName: String) =
  // language=xml
  """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name="$packageName.$activityClass"
            android:exported="true"
            android:requiredDisplayCategory="@string/display_category_xr_projected"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""