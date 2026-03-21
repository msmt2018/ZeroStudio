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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.res.layout

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf

fun fragmentLoginXml(fragmentClass: String, minApiLevel: Int, packageName: String, useAndroidX: Boolean): String {
  val autofillHintsEmail = renderIf(minApiLevel > 25) { """android:autofillHints="@string/prompt_email"""" }
  val autofillHintsPassword = renderIf(minApiLevel > 25) { """android:autofillHints="@string/prompt_password"""" }

  return """<?xml version="1.0" encoding="utf-8"?>
<${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:paddingBottom="@dimen/fragment_vertical_margin"
    android:paddingLeft="@dimen/fragment_horizontal_margin"
    android:paddingRight="@dimen/fragment_horizontal_margin"
    android:paddingTop="@dimen/fragment_vertical_margin"
    tools:context="${packageName}.ui.login.${fragmentClass}">

    <EditText
        android:id="@+id/username"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="24dp"
        android:layout_marginTop="96dp"
        android:layout_marginEnd="24dp"
        $autofillHintsEmail
        android:hint="@string/prompt_email"
        android:inputType="textEmailAddress"
        android:selectAllOnFocus="true"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <EditText
        android:id="@+id/password"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="24dp"
        android:layout_marginTop="8dp"
        android:layout_marginEnd="24dp"
        $autofillHintsPassword
        android:hint="@string/prompt_password"
        android:imeActionLabel="@string/action_sign_in_short"
        android:imeOptions="actionDone"
        android:inputType="textPassword"
        android:selectAllOnFocus="true"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/username" />

    <Button
        android:id="@+id/login"
        android:enabled="false"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="start"
        android:layout_marginStart="48dp"
        android:layout_marginTop="16dp"
        android:layout_marginEnd="48dp"
        android:layout_marginBottom="64dp"
        android:text="@string/action_sign_in"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/password"
        app:layout_constraintVertical_bias="0.2" />

    <ProgressBar
        android:id="@+id/loading"
        android:visibility="gone"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:layout_marginStart="32dp"
        android:layout_marginTop="64dp"
        android:layout_marginEnd="32dp"
        android:layout_marginBottom="64dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="@+id/password"
        app:layout_constraintStart_toStartOf="@+id/password"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintVertical_bias="0.3" />
</${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}>
"""
}
