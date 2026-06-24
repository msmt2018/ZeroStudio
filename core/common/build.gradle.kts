/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.itsaky.androidide.build.config.BuildConfig

plugins {
  id("com.android.library")
  id("kotlin-android")
}

android { namespace = "${BuildConfig.packageName}.common" }

dependencies {
  api(libs.common.editor)
  // compileOnly(projects.editor.editor)
  api(libs.common.lang3)
  api(libs.common.utilcode)
  api(libs.google.guava)
  api(libs.google.material)

  api(libs.androidx.appcompat)
  api(libs.androidx.collection)
  api(libs.androidx.preference)
  api(libs.androidx.documentfile)
  api(libs.androidx.vectors)
  api(libs.androidx.animated.vectors)
  api(libs.androidx.nav.ui)
  api(libs.androidx.nav.fragment)

  api(libs.androidx.core.ktx)
  api(libs.common.kotlin)

  // Java & dex/smali decompilers (declared in libs.versions.toml).
  // Exposed as `api` so the decompilers can be reached from the app module without
  // making every consumer declare them again.
  api(libs.com.jetbrains.intellij.java.decompiler)
  api(libs.google.baksmali)
  api(libs.google.smali)
  api(libs.google.smali.dexlib2)
  api(libs.google.smali.util)
  api(libs.org.jetbrains.fernflower)
  api(libs.org.bitbucket.mstrobel.procyon.compilertools)

  api(projects.core.resources)
  api(projects.editor.lexers)
  api(projects.event.eventbusAndroid)
  api(projects.event.eventbusEvents)
  api(projects.logging.logger)
  api(projects.utilities.buildInfo)
  api(projects.utilities.flashbar)
  api(projects.utilities.shared)

  val composeBom = platform("androidx.compose:compose-bom:2025.11.00")

  implementation("androidx.core:core-ktx:1.16.0")
  implementation("androidx.activity:activity-compose:1.10.1")
  implementation(composeBom)
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")

  testImplementation(libs.tests.junit)
  testImplementation(libs.tests.google.truth)
  testImplementation(libs.tests.robolectric)
}
