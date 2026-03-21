/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.itsaky.androidide.templates.impl.androidstudio.activities.aiStarter

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
 * Declares the AI Starter project template.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun aiStarterTemplate(): ProjectTemplate {
    val activityClass = stringParameter {
        name = R.string.activity_name
        default = "MainActivity"
        constraints = listOf(ParameterConstraint.CLASS, ParameterConstraint.UNIQUE, ParameterConstraint.NONEMPTY)
    }

    val isLauncher = booleanParameter {
        name = R.string.is_launcher_activity
        default = true
    }

    return baseProjectImpl {
        templateName = R.string.template_basic
        thumb = R.drawable.template_basic_activity
        
        widgets(TextFieldWidget(activityClass), CheckBoxWidget(isLauncher))

        defaultAppModule {
            aiStarterRecipe(activityClass.value, data.packageName, isLauncher.value)
        }
    }
}