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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment

fun androidManifestXml() =
  """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Include required permissions for Google Mobile Ads to run. -->
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>

    <application>
        <!--
            Adding this meta data is required as of Google Mobile Ads SDK version 17.0.0 as explained in
            https://developers.google.com/ad-manager/mobile-ads-sdk/android/quick-start#update_your_androidmanifestxml
        -->
        <meta-data
            android:name="com.google.android.gms.ads.AD_MANAGER_APP"
            android:value="true"/>

        <!--
        This is the sample AdMob App ID. Insert your own AdMob App ID in quotes
        https://googlemobileadssdk.page.link/admob-android-update-manifest
        -->
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-3940256099942544~3347511713"/>
    </application>
</manifest>
"""
