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

package com.itsaky.androidide.templates.impl.androidstudio.other.sliceProvider

fun androidManifestXml(authorities: String, className: String, hostUrl: String, packageName: String, pathPrefix: String) =
  """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>
        <provider android:name="${packageName}.${className}"
            android:authorities="$authorities"
            android:exported="true" >
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.app.slice.category.SLICE" />
                <data android:scheme="http"
                    android:host="$hostUrl"
                    android:pathPrefix="$pathPrefix" />
            </intent-filter>
        </provider>
    </application>

</manifest>
"""
