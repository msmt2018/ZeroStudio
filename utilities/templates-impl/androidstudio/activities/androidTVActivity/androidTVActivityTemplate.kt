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

package com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity

import com.itsaky.androidide.resources.R
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.ParameterConstraint
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.CheckBoxWidget
import com.itsaky.androidide.templates.booleanParameter
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.baseProjectImpl
import com.itsaky.androidide.templates.stringParameter

/**
 * Declares the Android TV Activity template using Leanback support library.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun androidTVActivityTemplate(): ProjectTemplate {
    val activityClass = stringParameter {
        name = R.string.activity_name
        default = "MainActivity"
        constraints = listOf(ParameterConstraint.CLASS, ParameterConstraint.UNIQUE, ParameterConstraint.NONEMPTY)
    }

    val layoutName = stringParameter {
        name = R.string.layout_name
        default = "activity_main"
        constraints = listOf(ParameterConstraint.LAYOUT, ParameterConstraint.UNIQUE, ParameterConstraint.NONEMPTY)
    }

    val mainFragment = stringParameter {
        name = R.string.activity_name
        default = "MainFragment"
        constraints = listOf(ParameterConstraint.CLASS, ParameterConstraint.UNIQUE, ParameterConstraint.NONEMPTY)
    }

    val detailsActivity = stringParameter {
        name = R.string.activity_name
        default = "DetailsActivity"
        constraints = listOf(ParameterConstraint.CLASS, ParameterConstraint.UNIQUE, ParameterConstraint.NONEMPTY)
    }

    val detailsLayoutName = stringParameter {
        name = R.string.layout_name
        default = "activity_details"
        constraints = listOf(ParameterConstraint.LAYOUT, ParameterConstraint.UNIQUE, ParameterConstraint.NONEMPTY)
    }

    val detailsFragment = stringParameter {
        name = R.string.activity_name
        default = "VideoDetailsFragment"
        constraints = listOf(ParameterConstraint.CLASS, ParameterConstraint.UNIQUE, ParameterConstraint.NONEMPTY)
    }

    val isLauncher = booleanParameter {
        name = R.string.is_launcher_activity
        default = true
    }

    return baseProjectImpl {
        templateName = R.string.template_basic
        thumb = R.drawable.template_basic_activity
        
        widgets(
            TextFieldWidget(activityClass),
            TextFieldWidget(layoutName),
            TextFieldWidget(mainFragment),
            TextFieldWidget(detailsActivity),
            TextFieldWidget(detailsLayoutName),
            TextFieldWidget(detailsFragment),
            CheckBoxWidget(isLauncher)
        )

        defaultAppModule {
            androidTVActivityRecipe(
                activityClass.value,
                layoutName.value,
                mainFragment.value,
                detailsActivity.value,
                detailsLayoutName.value,
                detailsFragment.value,
                data.packageName,
                isLauncher.value
            )
        }
    }
}