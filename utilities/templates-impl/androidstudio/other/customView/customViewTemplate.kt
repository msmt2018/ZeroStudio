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

package com.itsaky.androidide.templates.impl.androidstudio.other.customView

import com.android.tools.idea.wizard.template.Category
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
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val customViewTemplate
  get() = template {
    name = "Custom View"
    description = "Creates a new custom view that extends android.view.View and exposes custom attributes"

    formFactor = FormFactor.Mobile
    category = Category.UiComponent
    screens = listOf(WizardUiContext.MenuEntry)

    val packageName = defaultPackageNameParameter

    val viewClass = stringParameter {
      name = "View Class"
      default = "MyView"
      help = "By convention, should end in View"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    thumb {
      // TODO(b/147126989)
      File("no_activity.png")
    }

    widgets(PackageNameWidget(packageName), TextFieldWidget(viewClass), LanguageWidget())

    recipe = { data: TemplateData -> customViewRecipe(data as ModuleTemplateData, packageName.value, viewClass.value) }
  }
