/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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
package com.itsaky.androidide.templates.impl.androidstudio.other.emptyCalApp

fun androidManifestXml(carAppServiceName: String, packageName: String): String {
  // Note: The service name is relative to the package name, so we don't need to provide the full path.
  return """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <!-- Minimum Car API level -->
        <meta-data
            android:name="androidx.car.app.minCarApiLevel"
            android:value="1" />

        <service
            android:name="${packageName}.${carAppServiceName}"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.car.app.CarAppService" />
                <!-- Other categories: NAVIGATION, CHARGING, IOT, ... -->
                <category android:name="androidx.car.app.category.POI" />
            </intent-filter>
        </service>
    </application>
</manifest>
"""
}
