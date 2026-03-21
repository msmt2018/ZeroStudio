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

package com.itsaky.androidide.templates.impl.androidstudio.other.broadcastReceiver

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
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val broadcastReceiverTemplate
  get() = template {
    name = "Broadcast Receiver"
    description = "Creates a new broadcast receiver component and adds it to your Android manifest"

    formFactor = FormFactor.Mobile
    category = Category.Other
    screens = listOf(WizardUiContext.MenuEntry)

    val className = stringParameter {
      name = "Class Name"
      default = "MyReceiver"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val isExported = booleanParameter {
      name = "Exported"
      default = true
      help = "Whether or not the broadcast receiver can receive messages from sources outside its application"
    }

    val isEnabled = booleanParameter {
      name = "Enabled"
      default = true
      help = "Whether or not the broadcast receiver can be instantiated by the system"
    }

    widgets(TextFieldWidget(className), CheckBoxWidget(isExported), CheckBoxWidget(isEnabled), LanguageWidget())

    thumb {
      // TODO(b/147126989)
      File("no_activity.png")
    }

    recipe = { data: TemplateData ->
      broadcastReceiverRecipe(data as ModuleTemplateData, className.value, isExported.value, isEnabled.value)
    }
  }
