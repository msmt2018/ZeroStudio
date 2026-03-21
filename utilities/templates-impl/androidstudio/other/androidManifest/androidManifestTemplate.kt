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
package com.itsaky.androidide.templates.impl.androidstudio.other.androidManifest

import com.android.tools.idea.wizard.template.BooleanParameter
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.itsaky.androidide.templates.impl.androidstudio.invisibleSourceProviderNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val androidManifestTemplate
  get() = template {
    name = "Android Manifest File"
    minApi = MIN_API
    description = "Creates an Android Manifest XML File"

    category = Category.Other
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.MenuEntry)

    val remapFolder: BooleanParameter = booleanParameter {
      name = "Change File Location"
      default = false
      help = "Change the file location to another destination within the module"
    }

    val newLocation: StringParameter = stringParameter {
      name = "New File Location"
      constraints = listOf(Constraint.NONEMPTY, Constraint.SOURCE_SET_FOLDER, Constraint.UNIQUE)
      default = ""
      suggest = { "src/${sourceProviderName}/AndroidManifest.xml" }
      help = "The location for the new file"
      enabled = { remapFolder.value }
      loggable = true
    }

    // This is an invisible parameter to pass data from [WizardTemplateData] to the recipe.
    val sourceProviderName = invisibleSourceProviderNameParameter

    widgets(CheckBoxWidget(remapFolder), TextFieldWidget(newLocation), TextFieldWidget(sourceProviderName))

    thumb {
      // TODO(b/147126989)
      File("no_activity.png")
    }

    recipe = { data: TemplateData ->
      androidManifestRecipe(data as ModuleTemplateData, remapFolder.value, newLocation.value) { sourceProviderName.suggest()!! }
    }
  }
