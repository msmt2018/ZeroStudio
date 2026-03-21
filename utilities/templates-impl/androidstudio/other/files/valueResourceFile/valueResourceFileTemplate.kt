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

package com.itsaky.androidide.templates.impl.androidstudio.other.files.valueResourceFile

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.Constraint.VALUES
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val valueResourceFileTemplate
  get() = template {
    name = "Values XML File"
    description = "Creates a new XML values file"
    minApi = MIN_API
    category = Category.XML
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.MenuEntry)

    val fileName = stringParameter {
      name = "Values File Name"
      default = "values"
      help = "Name of the values XML file"
      constraints = listOf(UNIQUE, NONEMPTY, VALUES)
      loggable = true
    }

    thumb {
      // TODO(b/147126989)
      File("no_activity.png")
    }

    widgets(TextFieldWidget(fileName))

    recipe = { data: TemplateData -> valueResourceFileRecipe(data as ModuleTemplateData, fileName.value) }
  }
