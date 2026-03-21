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

package com.itsaky.androidide.templates.impl.androidstudio.other.intentService

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val intentServiceTemplate
  get() = template {
    name = "Service (IntentService)"
    description = "Creates a new intent service class"
    minApi = MIN_API
    formFactor = FormFactor.Mobile
    category = Category.Service
    screens = listOf(WizardUiContext.MenuEntry)

    val className = stringParameter {
      name = "Class Name"
      default = "MyIntentService"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val includeHelper = booleanParameter {
      name = "Include helper start methods?"
      default = true
      help = "Generate static helper methods to start the service e.g. MyIntentService.startAction()"
    }

    widgets(TextFieldWidget(className), CheckBoxWidget(includeHelper), LanguageWidget())

    thumb {
      // TODO(b/147126989)
      File("no_activity.png")
    }

    recipe = { data: TemplateData -> intentServiceRecipe(data as ModuleTemplateData, className.value, includeHelper.value) }
  }
