/*
 *  This file is part of AndroidIDE.
 */

import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.plugins.NoDesugarPlugin

plugins { id("com.android.library") }

apply { plugin(NoDesugarPlugin::class.java) }

description = "LogSender is used to read logs from applications built with AndroidIDE"

android {
  namespace = "${BuildConfig.packageName}.logsender"

  defaultConfig {
    minSdk = 16
    vectorDrawables.useSupportLibrary = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    aidl = true
    viewBinding = false
  }
}

dependencies {
  // your dependencies here
}

// PR-1: the legacy `logger-runtime.aar` rename task is removed. The new
// `ide-log-plugin` AAR is produced by the `:ide-log-plugin` module and
// copied into assets by the build pipeline directly, so this build no
// longer has to munge its own output filename.
