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

package com.itsaky.androidide.templates.impl.noAndroidXActivity

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder

internal fun baselineProfileKtDsl(packageName: String, activityClass: String): String =
    """
plugins {
  alias(libs.plugins.android.test)
  alias(libs.plugins.baselineprofile)
}

android {
  namespace = "${data.packageName}"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  defaultConfig {
    minSdk = ${data.versions.minSdk.api}
    targetSdk = ${data.versions.targetSdk.api}

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  targetProjectPath = ":app"
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile { useConnectedDevices = true }

dependencies {
  implementation(libs.androidx.benchmark.macro.junit4)
  implementation(libs.androidx.espresso.core)
  implementation(libs.androidx.junit)
  implementation(libs.androidx.uiautomator)
}

androidComponents {
  onVariants { v ->
    val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
    v.instrumentationRunnerArguments.put(
        "targetAppId",
        v.testedApks.map { artifactsLoader.load(it)?.applicationId },
    )
  }
}
"""
        .trim()
