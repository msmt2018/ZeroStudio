/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.itsaky.androidide.templates.impl.androidstudio.activities.basicActivity

import com.itsaky.androidide.resources.R
import com.itsaky.androidide.templates.CheckBoxWidget
import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.ParameterConstraint
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.booleanParameter
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.models.Dependency
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.emptyThemesAndColors
import com.itsaky.androidide.templates.impl.utils.addComposeDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.basicActivity.res.layout.fragmentFirstLayout
import com.itsaky.androidide.templates.impl.androidstudio.activities.basicActivity.res.layout.fragmentSecondLayout
import com.itsaky.androidide.templates.impl.androidstudio.activities.basicActivity.res.layout.fragmentSimpleXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.basicActivity.res.navigation.navGraphXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.basicActivity.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.basicActivity.src.basicActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.basicActivity.src.basicActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.basicActivity.src.firstFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.basicActivity.src.firstFragmentKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.basicActivity.src.secondFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.basicActivity.src.secondFragmentKt
import com.itsaky.androidide.templates.impl.baseProjectImpl
import com.itsaky.androidide.templates.stringParameter

/**
 * Creates a Basic Views Activity project template for AndroidIDE, migrated from Android Studio templates.
 * This template sets up an activity with a floating action button, a content area with fragments managed by a navigation graph,
 * and a toolbar.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun basicActivityTemplate(): ProjectTemplate {
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
        thumb = R.drawable.template_basic_activity_material3
        description = R.string.template_basic_activity_description
        
        widgets(TextFieldWidget(activityClass), CheckBoxWidget(isLauncher))

        defaultAppModule {
            generateBasicActivity(activityClass.value, "activity_main", isLauncher.value)
        }
    }
}

/**
 * The core recipe logic for generating a Basic Views Activity.
 *
 * @param activityClass The name of the main activity class.
 * @param layoutName The name of the main layout file.
 * @param isLauncher Whether this activity should be the launcher activity.
 */
private fun AndroidModuleTemplateBuilder.generateBasicActivity(
    activityClass: String,
    layoutName: String,
    isLauncher: Boolean
) {
    // Define derived resource and class names
    val contentLayoutName = "content_main"
    val firstFragmentLayoutName = "fragment_first"
    val secondFragmentLayoutName = "fragment_second"
    val navGraphName = "nav_graph"
    val firstFragmentClass = "FirstFragment"
    val secondFragmentClass = "SecondFragment"

    // Add required dependencies
    addDependency(Dependency.AndroidX.AppCompat)
    addDependency(Dependency.Google.Material)
    addDependency(Dependency.AndroidX.ConstraintLayout)
    if (data.language == Language.Kotlin) {
        addDependency(Dependency.AndroidX.Navigation_Fragment_Ktx)
        addDependency(Dependency.AndroidX.Navigation_Ui_Ktx)
    } else {
        addDependency(Dependency.AndroidX.Navigation_Fragment)
        addDependency(Dependency.AndroidX.Navigation_Ui)
    }

    // Recipe for file generation
    recipe = createRecipe {
        // Generate themes and colors
        res { emptyThemesAndColors(actionBar = true) }
        
        // Generate layouts
        res {
            writeXmlResource(contentLayoutName, ResourceType.LAYOUT, source = { fragmentSimpleXml(navGraphName, "nav_host_fragment_content_main") })
            writeXmlResource(firstFragmentLayoutName, ResourceType.LAYOUT, source = { fragmentFirstLayout(firstFragmentClass) })
            writeXmlResource(secondFragmentLayoutName, ResourceType.LAYOUT, source = { fragmentSecondLayout(secondFragmentClass) })
            writeXmlResource("main", ResourceType.MENU) {
                """
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <item
        android:id="@+id/action_settings"
        android:orderInCategory="100"
        android:title="@string/action_settings"
        app:showAsAction="never" />
</menu>
"""
            }
        }
        
        // Generate navigation graph
        res {
            writeXmlResource(navGraphName, ResourceType.NAVIGATION, source = { navGraphXml(data.packageName, firstFragmentClass, secondFragmentClass, firstFragmentLayoutName, secondFragmentLayoutName) })
        }

        // Generate string resources
        res {
            writeXmlResource("strings", ResourceType.VALUES, source = { stringsXml() })
            putStringRes("action_settings", "Settings")
        }
        
        // Generate source code
        sources {
            val isViewBindingSupported = true // AndroidIDE templates assume view binding is enabled
            if (data.language == Language.Kotlin) {
                writeKtSrc(data.packageName, activityClass, source = { basicActivityKt(activityClass, layoutName, "main", isViewBindingSupported) })
                writeKtSrc(data.packageName, firstFragmentClass, source = { firstFragmentKt(data.packageName, firstFragmentClass, secondFragmentClass, firstFragmentLayoutName, isViewBindingSupported) })
                writeKtSrc(data.packageName, secondFragmentClass, source = { secondFragmentKt(data.packageName, firstFragmentClass, secondFragmentClass, secondFragmentLayoutName, isViewBindingSupported) })
            } else {
                writeJavaSrc(data.packageName, activityClass, source = { basicActivityJava(activityClass, layoutName, "main", isViewBindingSupported) })
                writeJavaSrc(data.packageName, firstFragmentClass, source = { firstFragmentJava(data.packageName, firstFragmentClass, secondFragmentClass, firstFragmentLayoutName, isViewBindingSupported) })
                writeJavaSrc(data.packageName, secondFragmentClass, source = { secondFragmentJava(data.packageName, firstFragmentClass, secondFragmentClass, secondFragmentLayoutName, isViewBindingSupported) })
            }
        }
    }
}