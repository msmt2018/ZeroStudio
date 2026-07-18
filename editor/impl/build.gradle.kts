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
  id("kotlin-kapt")
  id("com.google.devtools.ksp") version libs.versions.ksp
}

android { namespace = "${BuildConfig.packageName}.editor" }

kapt { arguments { arg("eventBusIndex", "${BuildConfig.packageName}.events.EditorEventsIndex") } }

dependencies {
  ksp(projects.annotation.processorsKsp)
  kapt(projects.annotation.processors)

  api(projects.editor.treeSitterNdk.androidTreeSitter)
  api(libs.androidide.ts.java)
  api(libs.androidide.ts.json)
  api(libs.androidide.ts.kotlin)
  api(libs.androidide.ts.log)
  api(libs.androidide.ts.xml)
  api(libs.androidide.ts.c)
  // api(libs.androidide.ts.cpp)
  api(libs.androidide.ts.python)

  api(libs.androidx.collection)
  api(libs.common.editor)

  api(projects.editor.api)
  api(projects.editor.treesitter)

  implementation(libs.common.editor)

  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.squareup.okio:okio:3.9.0")

  implementation(libs.common.org.eclipse.lsp4j)
  implementation(libs.common.lsp4j.jsonrpc)
  implementation(projects.editor.editorLsp)

  implementation(libs.androidx.annotation)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.tracing)
  implementation(libs.androidx.tracing.ktx)

  implementation(libs.common.utilcode)
  implementation(libs.composite.jdt)

  implementation(libs.google.material)

  implementation(projects.annotation.annotations)
  implementation(projects.core.actions)
  implementation(projects.core.common)
  implementation(projects.core.lspApi)
  implementation(projects.core.resources)
  implementation(projects.editor.lexers)
  implementation(projects.event.eventbusAndroid)
  implementation(projects.event.eventbusEvents)
  implementation(projects.java.lsp)
  implementation(projects.utilities.shared)
  implementation(projects.xml.lsp)
  implementation(projects.lsp.kotlin)
  implementation(projects.lsp.toml)
  // implementation(projects.lsp.clangd)

  // tree sitter
  api(projects.editor.treeSitterNdk.toml)
  api(projects.editor.treeSitterNdk.cmake)
  // api(projects.editor.treeSitterNdk.reStructuredText)
  // api(projects.editor.treeSitterNdk.markdown)
  api(projects.editor.treeSitterNdk.yaml)
  // api(projects.editor.treeSitterNdk.sqlite)
  // api(projects.editor.treeSitterNdk.sql)
  // api(projects.editor.treeSitterNdk.plsql)
  // api(projects.editor.treeSitterNdk.googleSqlBigquery)
  // api(projects.editor.treeSitterNdk.bash)
  api(projects.editor.treeSitterNdk.aidl)
  // api(projects.editor.treeSitterNdk.proto)
  // api(projects.editor.treeSitterNdk.smali)
  // api(projects.editor.treeSitterNdk.php)
  // api(projects.editor.treeSitterNdk.typeScript)
  api(projects.editor.treeSitterNdk.cpp)
  api(projects.editor.treeSitterNdk.html)
  // api(projects.editor.treeSitterNdk.css)
  // api(projects.editor.treeSitterNdk.go)
  // api(projects.editor.treeSitterNdk.javascript)
  // api(projects.editor.treeSitterNdk.lua)
  // api(projects.editor.treeSitterNdk.objectiveC)
  // api(projects.editor.treeSitterNdk.objectiveCpp)
  // api(projects.editor.treeSitterNdk.rust)
  // api(projects.editor.treeSitterNdk.swift)
  // api(projects.editor.treeSitterNdk.vue)
  // api(projects.editor.treeSitterNdk.groovy)
  // api(projects.editor.treeSitterNdk.dart)
  // api(projects.editor.treeSitterNdk.assemblyLanguage.arm)
  // api(projects.editor.treeSitterNdk.assemblyLanguage.arm64)
  // api(projects.editor.treeSitterNdk.assemblyLanguage.asm)
  // api(projects.editor.treeSitterNdk.assemblyLanguage.masm)
  // api(projects.editor.treeSitterNdk.assemblyLanguage.nasm)

  implementation(projects.editor.treeSitterNdk.treeSitterJnilibs)
  implementation(files("libs/tree-sitter-xxx.jar"))

  testImplementation(libs.tests.junit)
  testImplementation(libs.tests.google.truth)
  testImplementation(libs.tests.robolectric)
}
