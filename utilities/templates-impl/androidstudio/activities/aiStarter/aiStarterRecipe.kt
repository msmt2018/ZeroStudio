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

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.models.Dependency
import com.itsaky.androidide.templates.base.modules.android.ManifestActivity
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.utils.addComposeDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.aiStarter.src.app_package.mainActivityKt
import com.itsaky.androidide.templates.impl.composeActivity.composeThemesXml
import com.itsaky.androidide.templates.impl.composeActivity.themeColorSrc
import com.itsaky.androidide.templates.impl.composeActivity.themeThemeSrc
import com.itsaky.androidide.templates.impl.composeActivity.themeTypeSrc

/**
 * Generates the AI Starter Activity, initializing its dependencies including Room, Retrofit, CameraX, Moshi, DataStore, and Compose components.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun AndroidModuleTemplateBuilder.aiStarterRecipe(activityClass: String, packageName: String, isLauncher: Boolean) {
    addDependency("androidx.lifecycle", "lifecycle-runtime-ktx", "2.8.7")
    addDependency("androidx.lifecycle", "lifecycle-viewmodel-compose", "2.8.7")
    addDependency("androidx.activity", "activity-compose", "1.10.1")

    // Add Compose dependencies, using the BOM to set versions
    addComposeDependencies()

    // Adding specific ecosystem components:
    addDependency("androidx.navigation", "navigation-compose", "2.8.9")

    addDependency("androidx.room", "room-runtime", "2.7.0")
    addDependency("androidx.room", "room-ktx", "2.7.0")
    // KSP Configuration is excluded here as AndroidIDE doesn't seamlessly support KSP injection via basic dependencies just yet without manual configuration.
    // addDependency("androidx.room", "room-compiler", "2.7.0", configuration = "ksp")

    addDependency("androidx.compose.material3", "material3", "1.3.1")
    addDependency("androidx.compose.material", "material-icons-core", "1.7.5")
    addDependency("androidx.compose.material", "material-icons-extended", "1.7.5")

    addDependency("io.coil-kt", "coil-compose", "2.7.0")

    addDependency("com.squareup.retrofit2", "retrofit", "2.12.0")
    addDependency("com.squareup.retrofit2", "converter-moshi", "2.12.0")

    addDependency("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.10.2")
    addDependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.10.2")

    addDependency("com.google.accompanist", "accompanist-permissions", "0.37.3")
    addDependency("com.google.android.gms", "play-services-location", "21.3.0")

    val cameraVersion = "1.5.0"
    addDependency("androidx.camera", "camera-camera2", cameraVersion)
    addDependency("androidx.camera", "camera-lifecycle", cameraVersion)
    addDependency("androidx.camera", "camera-view", cameraVersion)
    addDependency("androidx.camera", "camera-core", cameraVersion)

    val okHttpVersion = "4.10.0"
    addDependency("com.squareup.okhttp3", "logging-interceptor", okHttpVersion)
    addDependency("com.squareup.okhttp3", "okhttp", okHttpVersion)

    val moshiVersion = "1.15.2"
    addDependency("com.squareup.moshi", "moshi-kotlin", moshiVersion)

    val dataStoreVersion = "1.1.7"
    addDependency("androidx.datastore", "datastore-preferences", dataStoreVersion)

    isComposeModule = true
    val themeName = "${data.appName}Theme"

    manifest {
        addPermission("android.permission.INTERNET")
        addActivity(ManifestActivity(name = activityClass, isExported = true, isLauncher = isLauncher))
    }

    recipe = createRecipe {
        res {
            writeXmlResource("themes", ResourceType.VALUES, source = { composeThemesXml() })
        }
        sources {
            writeKtSrc(packageName, activityClass, source = { mainActivityKt(activityClass, "GreetingPreview", "Greeting", packageName, themeName) })
            
            writeKtSrc("$packageName.ui.theme", "Color", source = { themeColorSrc() })
            writeKtSrc("$packageName.ui.theme", "Theme", source = { themeThemeSrc() })
            writeKtSrc("$packageName.ui.theme", "Type", source = { themeTypeSrc() })
        }
    }
}