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

package com.itsaky.androidide.templates.impl.androidstudio.other.customView.res.layout

import com.itsaky.androidide.templates.impl.androidstudio.other.customView.res.values.getCustomViewStyle

fun sampleXml(packageName: String, themeName: String, viewClass: String) =
  """
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <${packageName}.${viewClass}
        style="@style/${getCustomViewStyle(themeName)}"
        android:layout_width="300dp"
        android:layout_height="300dp"
        android:paddingLeft="20dp"
        android:paddingBottom="40dp"
        app:exampleDimension="24sp"
        app:exampleString="Hello, ${viewClass}"
        app:exampleDrawable="@android:drawable/ic_menu_add" />

</FrameLayout>
"""
