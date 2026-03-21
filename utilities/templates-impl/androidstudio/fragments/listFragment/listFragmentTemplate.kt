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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.listFragment

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.EnumWidget
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.enumParameter
import com.android.tools.idea.wizard.template.extractLetters
import com.android.tools.idea.wizard.template.fragmentToLayout
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

@Suppress("EnumEntryName", "Unused")
enum class ColumnCount {
  `1 (List)`,
  `2 (Grid)`,
  `3`,
  `4`,
}

val listFragmentTemplate
  get() = template {
    name = "Fragment (List)"
    description = "Creates a new empty fragment containing a list that can be rendered as a grid. Compatible back to API level $MIN_API"
    minApi = MIN_API
    category = Category.Fragment
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.FragmentGallery, WizardUiContext.MenuEntry)

    val packageName = defaultPackageNameParameter
    val objectKind = stringParameter {
      name = "Object Kind"
      default = "Item"
      help = "Other examples are Person, Book, etc."
      constraints = listOf(NONEMPTY)
      loggable = true
    }

    val fragmentClass = stringParameter {
      name = "Fragment class name"
      default = "ItemFragment"
      constraints = listOf(NONEMPTY, CLASS, UNIQUE)
      suggest = { "${extractLetters(objectKind.value)}Fragment" }
      loggable = true
    }

    val columnCount =
      enumParameter<ColumnCount> {
        name = "Column Count"
        default = ColumnCount.`1 (List)`
        help = "The number of columns in the grid"
      }

    val fragmentLayout = stringParameter {
      name = "Object content layout file name"
      default = "fragment_item"
      constraints = listOf(LAYOUT, NONEMPTY, UNIQUE)
      suggest = { fragmentToLayout(fragmentClass.value) }
      loggable = true
    }

    val fragmentLayoutList = stringParameter {
      name = "List layout file name"
      default = "fragment_item_list"
      constraints = listOf(LAYOUT, NONEMPTY, UNIQUE)
      suggest = { "${fragmentToLayout(fragmentClass.value)}_list" }
      loggable = true
    }

    val adapterClassName = stringParameter {
      name = "Adapter class name"
      default = "MyItemRecyclerViewAdapter"
      constraints = listOf(NONEMPTY, CLASS, UNIQUE)
      suggest = { "My${extractLetters(objectKind.value)}RecyclerViewAdapter" }
      loggable = true
    }

    widgets(
      PackageNameWidget(packageName),
      TextFieldWidget(objectKind),
      TextFieldWidget(fragmentClass),
      EnumWidget(columnCount),
      TextFieldWidget(fragmentLayout),
      TextFieldWidget(fragmentLayoutList),
      TextFieldWidget(adapterClassName),
      LanguageWidget(),
    )

    thumb { File("list-fragment").resolve("template_list_fragment.png") }

    recipe = { data: TemplateData ->
      listFragmentRecipe(
        data as ModuleTemplateData,
        packageName.value,
        fragmentClass.value,
        columnCount.value,
        fragmentLayout.value,
        fragmentLayoutList.value,
        adapterClassName.value,
      )
    }
  }
