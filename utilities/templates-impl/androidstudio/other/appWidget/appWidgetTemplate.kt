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

package com.itsaky.androidide.templates.impl.androidstudio.other.appWidget

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.EnumWidget
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.enumParameter
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

enum class Placement {
  Both,
  Homescreen,
  Keyguard,
}

// suffix is used to generate file name for thumb loading
enum class Resizeable(val suffix: String) {
  Both("vh"),
  Horizontal("h"),
  Vertical("v"),
  None(""),
}

@Suppress("EnumEntryName")
enum class MinimumCells {
  `1`,
  `2`,
  `3`,
  `4`,
}

val appWidgetTemplate
  get() = template {
    name = "App Widget"
    description = "Creates a new App Widget"
    minApi = MIN_API
    formFactor = FormFactor.Mobile
    category = Category.Widget
    screens = listOf(WizardUiContext.MenuEntry)

    val className = stringParameter {
      name = "Class Name"
      default = "NewAppWidget"
      help = "The name of the App Widget to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val placement =
      enumParameter<Placement> {
        name = "Placement"
        default = Placement.Homescreen
        help =
          "Make the widget available on the Home-screen and/or on the Keyguard. Keyguard placement is only supported in Android 4.2 and above; this setting is ignored on earlier versions and defaults to Home-screen.>"
      }

    val resizable =
      enumParameter<Resizeable> {
        name = "Resizable"
        default = Resizeable.Both
        help = "Allow the user to resize the widget. Feature only available on Android 3.1 and above.>"
      }

    val minWidth =
      enumParameter<MinimumCells> {
        name = "Minimum Width (cells)"
        default = MinimumCells.`1`
      }

    val minHeight =
      enumParameter<MinimumCells> {
        name = "Minimum Height (cells)"
        default = MinimumCells.`1`
      }

    val configurable = booleanParameter {
      name = "Configuration Screen"
      default = false
      help = "Generates a widget configuration activity"
    }

    thumb {
      File("app-widget")
        .resolve("template_widget_" + minWidth.value.name + "x" + minHeight.value.name + "_" + resizable.value.suffix + ".png")
    }

    widgets(
      TextFieldWidget(className),
      EnumWidget(placement),
      EnumWidget(resizable),
      EnumWidget(minWidth),
      EnumWidget(minHeight),
      CheckBoxWidget(configurable),
      LanguageWidget(),
    )

    recipe = { data: TemplateData ->
      appWidgetRecipe(
        data as ModuleTemplateData,
        className.value,
        placement.value,
        resizable.value,
        minWidth.value,
        minHeight.value,
        configurable.value,
      )
    }
  }
