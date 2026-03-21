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
package com.itsaky.androidide.templates.impl.androidstudio.activities.aiGlassesActivity

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.modules.android.ManifestActivity
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.utils.addComposeDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.aiGlassesActivity.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.aiGlassesActivity.src.app_package.audioInterfaceKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.aiGlassesActivity.src.app_package.glassesActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.aiGlassesActivity.src.app_package.mainActivityKt
import com.itsaky.androidide.templates.impl.composeActivity.composeThemesXml
import com.itsaky.androidide.templates.impl.composeActivity.themeColorSrc
import com.itsaky.androidide.templates.impl.composeActivity.themeThemeSrc
import com.itsaky.androidide.templates.impl.composeActivity.themeTypeSrc

/**
 * Configures the AI Glasses Activity module template logic and dependencies.
 * Applies Jetpack Compose configurations, XR extensions, and injects essential manifest settings.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun AndroidModuleTemplateBuilder.aiGlassesActivityRecipe(activityClass: String, packageName: String) {
    // Inject Jetpack Compose and XR Specific Dependencies
    addDependency("androidx.activity", "activity-compose", "1.11.0")
    addComposeDependencies()
    addDependency("androidx.compose.material3", "material3", "1.3.1")
    addDependency("androidx.xr.glimmer", "glimmer", "1.0.0-alpha02")
    addDependency("androidx.xr.projected", "projected", "1.0.0-alpha03")

    isComposeModule = true
    val themeName = "${data.appName}Theme"
    val glassesActivityClass = "Glasses$activityClass"

    // Manifest configuration using AndroidIDE Builder API
    manifest {
        addActivity(ManifestActivity(name = activityClass, isExported = true, isLauncher = true))
        addActivity(ManifestActivity(name = glassesActivityClass, isExported = true, isLauncher = false))
    }

    recipe = createRecipe {
        // Replace full manifest to include display_category_xr_projected requirements explicitly
        save(aiGlassesActivityManifestXml(glassesActivityClass, packageName), manifestFile())

        res {
            writeXmlResource("themes", ResourceType.VALUES, source = { composeThemesXml() })
            writeXmlResource("strings", ResourceType.VALUES, source = { stringsXml() })
        }
        
        sources {
            writeKtSrc(packageName, activityClass, source = { mainActivityKt(activityClass, glassesActivityClass, packageName, themeName) })
            writeKtSrc(packageName, glassesActivityClass, source = { glassesActivityKt(glassesActivityClass, packageName) })
            writeKtSrc(packageName, "AudioInterface", source = { audioInterfaceKt(packageName) })
            
            writeKtSrc("$packageName.ui.theme", "Color", source = { themeColorSrc() })
            writeKtSrc("$packageName.ui.theme", "Theme", source = { themeThemeSrc() })
            writeKtSrc("$packageName.ui.theme", "Type", source = { themeTypeSrc() })
        }
    }
}