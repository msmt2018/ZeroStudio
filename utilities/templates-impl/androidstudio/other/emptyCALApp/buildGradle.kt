/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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
package com.itsaky.androidide.templates.impl.androidstudio.other.emptyCalApp

import com.android.sdklib.AndroidMajorVersion
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.TemplateKotlinSupport
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.itsaky.androidide.templates.impl.androidstudio.compileSdk
import com.itsaky.androidide.templates.impl.androidstudio.minSdk
import com.itsaky.androidide.templates.impl.androidstudio.targetSdk
import com.android.tools.idea.wizard.template.renderIf

fun buildGradle(
  projectData: ProjectTemplateData,
  packageName: String,
  buildApi: AndroidVersion,
  minApi: AndroidMajorVersion,
  targetApi: AndroidMajorVersion,
): String {
  val agpVersion = projectData.agpVersion
  val useAndroidX = projectData.androidXSupport
  return """
plugins {
    id 'com.android.library'
    ${renderIf(projectData.language == Language.Kotlin && projectData.kotlinSupport == TemplateKotlinSupport.LEGACY_KOTLIN_GRADLE_PLUGIN_BEFORE_AGP9) {"    id 'org.jetbrains.kotlin.android'"}}
    ${renderIf(projectData.language == Language.Kotlin && projectData.kotlinSupport == TemplateKotlinSupport.EXPLICIT_BUILT_IN_KOTLIN) { "    id 'com.android.built-in-kotlin'" }}
}
android {
    namespace '$packageName'
    ${compileSdk(buildApi, agpVersion)}

    defaultConfig {
        ${minSdk(minApi, agpVersion)}
        ${targetSdk(targetApi, agpVersion)}

        testInstrumentationRunner "${getMaterialComponentName("android.support.test.runner.AndroidJUnitRunner", useAndroidX)}"
    }
}
"""
}
