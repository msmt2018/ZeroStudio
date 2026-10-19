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
    alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
    alias(libs.plugins.com.google.devtools.ksp)
}

android { 
    namespace = "${BuildConfig.packageName}.actions" 

    sourceSets {
        getByName("main") {
            // 让打包工具能够看到 KSP 生成的 META-INF 服务清单文件
            resources.srcDir("build/generated/ksp/main/resources")
        }
    }
  buildFeatures { compose = true }
}

dependencies {
  
  ksp(libs.zacsweers.auto.service.ksp) 

  api(libs.androidx.nav.fragment)
  api(libs.androidx.nav.ui)
  
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.runtime)

  implementation(projects.core.common)
  implementation(projects.core.resources)
  implementation(libs.common.editor)
  implementation(libs.common.kotlin)
  implementation(libs.common.kotlin.coroutines.android)
  implementation(libs.common.utilcode)
  implementation(libs.google.auto.service.annotations)

  implementation(libs.androidx.core.ktx)
  implementation(libs.google.material)
}
