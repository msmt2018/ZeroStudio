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

package com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMessagingService

fun androidManifestXml(packageName: String, readReceiverName: String, replyReceiverName: String, serviceName: String) =
  """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application android:appCategory="audio">
        <meta-data
            android:name="com.google.android.gms.car.application"
            android:resource="@xml/automotive_app_desc" />

        <service
            android:name="${packageName}.${serviceName}"
            android:exported="true" />

        <receiver
            android:name="${packageName}.${readReceiverName}"
            android:exported="true">
            <intent-filter>
                <action android:name="${packageName}.ACTION_MESSAGE_READ"/>
            </intent-filter>
        </receiver>

        <receiver
            android:name="${packageName}.${replyReceiverName}"
            android:exported="true">
            <intent-filter>
                <action android:name="${packageName}.ACTION_MESSAGE_REPLY"/>
            </intent-filter>
        </receiver>
    </application>
</manifest>
"""
