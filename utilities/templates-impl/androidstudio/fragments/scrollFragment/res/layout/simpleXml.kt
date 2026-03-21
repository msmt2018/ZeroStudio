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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.scrollFragment.res.layout

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun simpleXml(fragmentClass: String, packageName: String, useAndroidX: Boolean) =
  """
<?xml version="1.0" encoding="utf-8"?>
<${getMaterialComponentName("android.support.v4.widget.NestedScrollView", useAndroidX)}
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="${packageName}.${fragmentClass}">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_margin="@dimen/text_margin"
        android:text="@string/large_text" />
</${getMaterialComponentName("android.support.v4.widget.NestedScrollView", useAndroidX)}>
"""
