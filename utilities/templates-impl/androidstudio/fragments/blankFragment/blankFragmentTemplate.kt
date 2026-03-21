/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.blankFragment

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.fragmentToLayout
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val blankFragmentTemplate
  get() = template {
    name = "Fragment (Blank)"
    description = "Creates a blank fragment that is compatible back to API level $MIN_API"
    minApi = MIN_API
    category = Category.Fragment
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.FragmentGallery, WizardUiContext.MenuEntry)

    val className = stringParameter {
      name = "Fragment Name"
      default = "BlankFragment"
      help = "The name of the fragment class to create"
      constraints = listOf(CLASS, NONEMPTY, UNIQUE)
      loggable = true
    }

    val layoutName = stringParameter {
      name = "Fragment Layout Name"
      default = "fragment_blank"
      help = "The name of the layout to create"
      constraints = listOf(LAYOUT, NONEMPTY, UNIQUE)
      suggest = { fragmentToLayout(className.value) }
      loggable = true
    }

    widgets(TextFieldWidget(className), TextFieldWidget(layoutName), LanguageWidget())

    thumb { File("blank-fragment").resolve("template_blank_fragment.png") }

    recipe = { data: TemplateData -> blankFragmentRecipe(data as ModuleTemplateData, className.value, layoutName.value) }
  }
