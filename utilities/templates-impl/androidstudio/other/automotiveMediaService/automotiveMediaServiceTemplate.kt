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

package com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMediaService

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val automotiveMediaServiceTemplate
  get() = template {
    name = "Media Service"
    description = "Create a MediaBrowserService and adds the required metadata for Android Automotive"
    minApi = 21

    category = Category.Car
    formFactor = FormFactor.Car
    screens = listOf(WizardUiContext.NewProject, WizardUiContext.MenuEntry, WizardUiContext.NewModule)

    val mediaBrowserServiceName = stringParameter {
      name = "Class name"
      default = "MyMusicService"
      help = "The name of the service that will extend MediaBrowserService and contain the logic to browse and playback media"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val packageName = defaultPackageNameParameter

    val useCustomTheme = booleanParameter {
      name = "Use a custom theme for Android Auto colors?"
      default = false
      help = "Android Auto apps can define a different set of colors that will be used exclusively when running on Android Auto"
    }

    val customThemeName = stringParameter {
      name = "Android Auto custom theme name"
      default = "CarTheme"
      visible = { useCustomTheme.value }
      constraints = listOf(NONEMPTY)
      loggable = true
    }

    widgets(
      TextFieldWidget(mediaBrowserServiceName),
      PackageNameWidget(packageName),
      CheckBoxWidget(useCustomTheme),
      TextFieldWidget(customThemeName),
      LanguageWidget(),
    )

    thumb { File("automotive-media-service").resolve("automotive-media-service.png") }

    recipe = { data: TemplateData ->
      automotiveMediaServiceRecipe(
        data as ModuleTemplateData,
        mediaBrowserServiceName.value,
        packageName.value,
        useCustomTheme.value,
        customThemeName.value,
      )
    }
  }
