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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.fullscreenFragment.res.layout

import com.android.tools.idea.wizard.template.ThemesData
import com.itsaky.androidide.templates.impl.androidstudio.activities.fullscreenActivity.res.values.getFullscreenButtonBarStyle
import com.itsaky.androidide.templates.impl.androidstudio.activities.fullscreenActivity.res.values.getFullscreenContainerThemeOverlay

fun fragmentFullscreenXml(fragmentClass: String, packageName: String, themesData: ThemesData) =
  """
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:theme="@style/${getFullscreenContainerThemeOverlay(themesData.overlay.name)}"
    android:background="?attr/fullscreenBackgroundColor"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="${packageName}.${fragmentClass}">

    <!-- The primary full-screen view. This can be replaced with whatever view
         is needed to present your content, e.g. VideoView, SurfaceView,
         TextureView, etc. -->
    <TextView android:id="@+id/fullscreen_content"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:keepScreenOn="true"
        android:textStyle="bold"
        android:textSize="50sp"
        android:textColor="?attr/fullscreenTextColor"
        android:gravity="center"
        android:text="@string/dummy_content" />

    <!-- This FrameLayout insets its children based on system windows using
         android:fitsSystemWindows. -->
    <FrameLayout android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fitsSystemWindows="true">

        <LinearLayout android:id="@+id/fullscreen_content_controls"
            style="@style/${getFullscreenButtonBarStyle(themesData.main.name)}"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|center_horizontal"
            android:orientation="horizontal"
            tools:ignore="UselessParent">

            <Button android:id="@+id/dummy_button"
                style="?android:attr/buttonBarButtonStyle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/dummy_button" />

        </LinearLayout>
    </FrameLayout>

</FrameLayout>
"""
