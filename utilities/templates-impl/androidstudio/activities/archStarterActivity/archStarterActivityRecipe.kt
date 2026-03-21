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
package com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.modules.android.ManifestActivity
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.utils.addComposeDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.application
import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.data.di.dataModule
import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.data.local.database.appDatabase
import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.data.local.database.dataModel
import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.data.local.di.databaseModule
import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.data.repository
import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.ui.mainActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.ui.mymodel.modelScreen
import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.ui.mymodel.viewModel
import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.ui.navigation
import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.ui.theme.colorKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.ui.theme.themeKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.ui.theme.typeKt

/**
 * Provides the recipe configuration for generating the Architecture Starter Activity project.
 * Automatically injects dependencies such as Hilt, Room, Compose, Retrofit, and Coroutines.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun AndroidModuleTemplateBuilder.archStarterActivityRecipe(
    activityClass: String,
    packageName: String,
    isLauncher: Boolean,
) {
    addDependency("androidx.lifecycle", "lifecycle-runtime-ktx", "2.8.7")
    addDependency("androidx.lifecycle", "lifecycle-viewmodel-compose", "2.8.7")
    addDependency("androidx.lifecycle", "lifecycle-runtime-compose", "2.8.7")
    addDependency("androidx.activity", "activity-compose", "1.11.0")
    
    // Inject core Compose dependencies
    addComposeDependencies()

    addDependency("androidx.hilt", "hilt-navigation-compose", "1.2.0")
    addDependency("androidx.navigation", "navigation-compose", "2.8.9")
    
    addDependency("androidx.room", "room-runtime", "2.7.0")
    addDependency("androidx.room", "room-ktx", "2.7.0")
    
    addDependency("com.google.dagger", "hilt-android", "2.57.2")
    
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
    
    addDependency("androidx.camera", "camera-camera2", "1.5.0")
    addDependency("androidx.camera", "camera-lifecycle", "1.5.0")
    addDependency("androidx.camera", "camera-view", "1.5.0")
    addDependency("androidx.camera", "camera-core", "1.5.0")
    
    addDependency("com.squareup.okhttp3", "logging-interceptor", "4.10.0")
    addDependency("com.squareup.okhttp3", "okhttp", "4.10.0")
    addDependency("com.squareup.moshi", "moshi-kotlin", "1.15.2")
    addDependency("androidx.datastore", "datastore-preferences", "1.1.7")

    isComposeModule = true

    val themeName = "${data.appName ?: "App"}Theme"
    val modelName = "${data.appName ?: "App"}Model"
    val appNameClass = "${data.appName?.replace(" ", "") ?: "App"}"

    manifest {
        addPermission("android.permission.INTERNET")
        addActivity(ManifestActivity(name = activityClass, isExported = true, isLauncher = isLauncher))
    }

    val recipeVariables = ArchStarterActivityTemplateVariables(
        basePackage = packageName,
        appName = appNameClass,
        activityName = activityClass,
        modelName = modelName,
        themeName = themeName,
    )

    recipe = createRecipe {
        sources {
            with(recipeVariables) {
                writeKtSrc(packageName, appName, source = { application() })

                writeKtSrc("$packageName.ui", activityClass, source = { mainActivityKt() })
                writeKtSrc("$packageName.ui", "Navigation", source = { navigation() })

                writeKtSrc("$packageName.ui.theme", "Color", source = { colorKt() })
                writeKtSrc("$packageName.ui.theme", "Theme", source = { themeKt() })
                writeKtSrc("$packageName.ui.theme", "Type", source = { typeKt() })

                writeKtSrc("$packageName.ui.${modelName.lowercase()}", modelScreen, source = { modelScreen() })
                writeKtSrc("$packageName.ui.${modelName.lowercase()}", viewModelName, source = { viewModel() })

                writeKtSrc("$packageName.data.di", "DataModule", source = { dataModule() })

                writeKtSrc("$packageName.data.local.database", "AppDatabase", source = { appDatabase() })
                writeKtSrc("$packageName.data.local.database", modelName, source = { dataModel() })

                writeKtSrc("$packageName.data.local.di", "DatabaseModule", source = { databaseModule() })

                writeKtSrc("$packageName.data", repositoryName, source = { repository() })
            }
        }
    }
}

class ArchStarterActivityTemplateVariables(
  val basePackage: String,
  val appName: String,
  val activityName: String,
  val modelName: String,
  val themeName: String,
) {
  fun packageName(vararg subpackages: String) = listOf(basePackage, *subpackages).joinToString(".")

  fun packageDeclaration(vararg subpackages: String) = "package ${packageName(*subpackages)}"

  val dataPackage get() = packageName("data")
  val dataDiPackage get() = packageName("data", "di")
  val databasePackage get() = packageName("data", "local", "database")
  val dataLocalDiPackage get() = packageName("data", "local", "di")

  val repositoryName get() = "${modelName}Repository"
  val repositoryVarName get() = "${modelName.lowercaseFirst()}Repository"
  val repositoryNameQualified get() = "${dataPackage}.${repositoryName}"

  val themePackage get() = packageName("ui", "theme")
  val modelPackage get() = packageName("ui", modelName.lowercase())

  val viewModelName get() = "${modelName}ViewModel"
  val modelDao get() = "${modelName}Dao"
  val modelDaoVar get() = "${modelName.lowercaseFirst()}Dao"

  val dataModelQualified get() = "${databasePackage}.${modelName}"
  val modelDaoQualified get() = "${databasePackage}.${modelDao}"

  val modelScreen get() = "${modelName}Screen"
  val modelScreenQualified get() = "${modelPackage}.${modelScreen}"

  val modelUiState get() = "${modelName}UiState"
  val modelUiStateQualified get() = "${modelPackage}.${modelName}UiState"

  val themeNameQualified get() = "${themePackage}.${themeName}"
}

private fun String.lowercaseFirst() = if (isEmpty()) "" else first().lowercase() + substring(1)