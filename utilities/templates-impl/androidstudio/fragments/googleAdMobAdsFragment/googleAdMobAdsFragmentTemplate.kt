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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment

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
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.enumParameter
import com.android.tools.idea.wizard.template.fragmentToLayout
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File
import java.util.Locale

/** Possible values for the AdFormat. Values are in camel case intentionally to be shown in the combo box. */
enum class AdFormat {
  Interstitial,
  Banner,
}

val googleAdMobAdsFragmentTemplate
  get() = template {
    name = "Google AdMob Ads Fragment"
    constraints = listOf(TemplateConstraint.AndroidX)
    minApi = MIN_API
    description = "Creates an fragment with AdMob Ad fragment"

    category = Category.Fragment
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.FragmentGallery, WizardUiContext.MenuEntry)

    val fragmentClass = stringParameter {
      name = "Fragment Name"
      default = "AdMobFragment"
      help = "The name of the AdMob fragment class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val layoutName = stringParameter {
      name = "Layout Name"
      default = "fragment_admob"
      help = "The name of the layout to create for the fragment"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { fragmentToLayout(fragmentClass.value) }
      loggable = true
    }

    val adFormat =
      enumParameter<AdFormat> {
        name = "Ad Format"
        default = AdFormat.Interstitial
        help = "Select Interstitial Ad or Banner Ad"
      }

    val packageName = defaultPackageNameParameter

    widgets(
      TextFieldWidget(fragmentClass),
      TextFieldWidget(layoutName),
      EnumWidget(adFormat),
      PackageNameWidget(packageName),
      LanguageWidget(),
    )

    thumb { File("google-admob-ads-fragment").resolve("template_admob_fragment_" + adFormat.value.name.lowercase(Locale.US) + ".png") }

    recipe = { data: TemplateData ->
      googleAdMobAdsFragmentRecipe(data as ModuleTemplateData, fragmentClass.value, layoutName.value, adFormat.value, packageName.value)
    }
  }
