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
package com.itsaky.androidide.templates.impl.androidstudio.other.wearDeclarativeWatchFace

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TemplateFlag
import com.android.tools.idea.wizard.template.WizardUiContext
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val wearDeclarativeWatchFaceTemplate
  get() = template {
    name = "Basic Watch Face"
    minApi = 33
    description = "Creates a basic Watch Face for Wear OS"

    constraints = listOf()
    category = Category.Other
    flags = listOf(TemplateFlag.WatchFace)
    formFactor = FormFactor.Wear
    screens = listOf(WizardUiContext.NewProject)
    val packageName = defaultPackageNameParameter
    widgets(PackageNameWidget(packageName))

    thumb { File("wear-watchface").resolve("template_watch_face.png") }

    recipe = { data: TemplateData -> wearDeclarativeWatchFaceRecipe(data as ModuleTemplateData) }
  }
