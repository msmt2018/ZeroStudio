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

package com.itsaky.androidide.templates.impl.androidstudio.other.contentProvider

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.Constraint.URI_AUTHORITY
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val contentProviderTemplate
  get() = template {
    name = "Content Provider"
    description = "Creates a new content provider component and adds it to your Android manifest"

    formFactor = FormFactor.Mobile
    category = Category.Other
    screens = listOf(WizardUiContext.MenuEntry)

    val className = stringParameter {
      name = "Class Name"
      default = "MyContentProvider"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val authorities = stringParameter {
      name = "URI Authorities"
      default = ""
      help = "A semicolon separated list of one or more URI authorities that identify data under the purview of the content provider"
      constraints = listOf(NONEMPTY, URI_AUTHORITY)
      loggable = true
    }

    val isExported = booleanParameter {
      name = "Exported"
      default = true
      help = "Whether or not the content provider can be used by components of other applications"
    }

    val isEnabled = booleanParameter {
      name = "Enabled"
      default = true
      help = "Whether or not the content provider can be instantiated by the system"
    }

    widgets(
      TextFieldWidget(className),
      TextFieldWidget(authorities),
      CheckBoxWidget(isExported),
      CheckBoxWidget(isEnabled),
      LanguageWidget(),
    )

    thumb {
      // TODO(b/147126989)
      File("no_activity.png")
    }

    recipe = { data: TemplateData ->
      contentProviderRecipe(data as ModuleTemplateData, className.value, authorities.value, isExported.value, isEnabled.value)
    }
  }
