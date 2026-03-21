/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.itsaky.androidide.templates.impl.androidstudio.other.emptyCalApp

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Constraint
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val emptyCalAppTemplate
  get() = template {
    name = "Empty Car App Library App"
    description = "Create a new Car App Library based application"
    minApi = 29

    category = Category.Car
    formFactor = FormFactor.Car
    screens = listOf(WizardUiContext.NewProject)
    constraints = listOf(TemplateConstraint.Kotlin)

    val carAppServiceName = stringParameter {
      name = "Car App Service Name"
      default = "MyCarAppService"
      help = "The name of the CarAppService that will be created"
      constraints = listOf(Constraint.CLASS, Constraint.UNIQUE, Constraint.NONEMPTY)
    }

    val sessionName = stringParameter {
      name = "Session Name"
      default = "MyCarAppSession"
      help = "The name of the Session that will be created"
      constraints = listOf(Constraint.CLASS, Constraint.UNIQUE, Constraint.NONEMPTY)
    }

    val screenName = stringParameter {
      name = "Screen Name"
      default = "MyCarAppScreen"
      help = "The name of the main Screen that will be created"
      constraints = listOf(Constraint.CLASS, Constraint.UNIQUE, Constraint.NONEMPTY)
    }

    val packageName = defaultPackageNameParameter

    widgets(
      TextFieldWidget(carAppServiceName),
      TextFieldWidget(sessionName),
      TextFieldWidget(screenName),
      PackageNameWidget(packageName),
      LanguageWidget(),
    )

    thumb { File("empty-cal-app").resolve("empty-cal-app.png") }

    recipe = { data: TemplateData ->
      emptyCalAppRecipe(data as ModuleTemplateData, carAppServiceName.value, sessionName.value, screenName.value, packageName.value)
    }
  }
