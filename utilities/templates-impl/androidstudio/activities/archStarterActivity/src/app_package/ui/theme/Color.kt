/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.ui.theme

import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.ArchStarterActivityTemplateVariables
import com.itsaky.androidide.templates.impl.utils.MaterialColor

/**
 * Provides basic Jetpack Compose colors for the architecture starter theme.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun ArchStarterActivityTemplateVariables.colorKt() =
  """
package $themePackage

import androidx.compose.ui.graphics.Color

${MaterialColor.PURPLE_80.kotlinComposeVal()}
${MaterialColor.PURPLE_GREY_80.kotlinComposeVal()}
${MaterialColor.PINK_80.kotlinComposeVal()}

${MaterialColor.PURPLE_40.kotlinComposeVal()}
${MaterialColor.PURPLE_GREY_40.kotlinComposeVal()}
${MaterialColor.PINK_40.kotlinComposeVal()}
"""