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

package com.itsaky.androidide.templates.impl.androidstudio.other.files.aidlFile

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val aidlFileTemplate
  get() = template {
    name = "AIDL File"
    constraints = listOf(TemplateConstraint.Aidl)
    description = "Creates a new Android Interface Description Language file"
    minApi = MIN_API
    category = Category.AIDL
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.MenuEntry)

    val interfaceName = stringParameter {
      name = "Interface Name"
      default = "IMyAidlInterface"
      help = "Name of the Interface"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    thumb {
      // TODO(b/147126989)
      File("no_activity.png")
    }

    widgets(TextFieldWidget(interfaceName))

    recipe = { data: TemplateData -> aidlFileRecipe(data as ModuleTemplateData, interfaceName.value) }
  }
