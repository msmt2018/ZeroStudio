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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment.res.layout

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment.AdFormat

fun fragmentAdmobXml(adFormat: AdFormat, fragmentClass: String, packageName: String, useAndroidX: Boolean): String {
  val formatSpecificLayout =
    when (adFormat) {
      AdFormat.Interstitial -> {
        """
    <!-- view for AdMob Interstitial Ad -->
    <TextView
        android:id="@+id/app_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="50dp"
        android:text="@string/interstitial_ad_sample"
        android:textAppearance="?android:attr/textAppearanceLarge"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintBottom_toTopOf="@id/level" />

    <TextView
        android:id="@+id/level"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/start_level"
        android:textAppearance="?android:attr/textAppearanceLarge"
        app:layout_constraintTop_toBottomOf="@id/app_title"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintBottom_toTopOf="@id/next_level_button" />

    <Button
        android:id="@+id/next_level_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/next_level"
        app:layout_constraintTop_toBottomOf="@id/level"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintBottom_toBottomOf="parent" />
    """
      }
      AdFormat.Banner -> {
        """
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/hello_world"
        app:layout_constraintBottom_toTopOf="@id/ad_view"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <!-- view for AdMob Banner Ad -->
    <com.google.android.gms.ads.AdView
        android:id="@+id/ad_view"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_alignParentBottom="true"
        android:layout_centerHorizontal="true"
        app:adSize="BANNER"
        app:adUnitId="@string/banner_ad_unit_id"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />
    """
      }
    }
  return """
<${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="${packageName}.${fragmentClass}">

$formatSpecificLayout
</${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}>
"""
}
