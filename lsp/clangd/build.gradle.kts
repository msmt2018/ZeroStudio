/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.itsaky.androidide.build.config.BuildConfig

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "${BuildConfig.packageName}.lsp.clangd"
    compileSdk = 35
    
    defaultConfig {
        minSdk = 21
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    // AndroidIDE 核心 LSP 接口与模型
    implementation(projects.core.lspApi)
    implementation(projects.core.lspModels)
    
    // 项目管理与公共工具
    implementation(projects.core.projects)
    implementation(projects.core.common)
    implementation(projects.editor.api)
    implementation(projects.utilities.preferences)

    // 基础库支持
    implementation(libs.common.kotlin)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference)
    
    implementation(libs.kotlinx.serialization.json)
}