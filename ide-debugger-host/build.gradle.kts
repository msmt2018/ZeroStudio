import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.plugins.NoDesugarPlugin

plugins { id("com.android.library") }

apply { plugin(NoDesugarPlugin::class.java) }

description =
    "ide-debugger-host: Host-side Android Debug Runtime (ADRT). Runs INSIDE " +
        "the host application process to bridge its JDWP socket to the IDE. " +
        "This module is referenced by ide-log-plugin (which is auto-injected " +
        "into the debug variant of every project built with ZeroStudio) and " +
        "by Shizuku's host-plugin / Root attach-agent paths."

android {
  namespace = "${BuildConfig.packageName}.zerostudio.ide.debugger.host"

  defaultConfig {
    minSdk = 21
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    aidl = false
    viewBinding = false
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }
}

dependencies {
  // AndroidX annotation / coroutines (host process may not have full IDE deps)
  compileOnly(libs.androidx.core.ktx)
  compileOnly(libs.androidx.annotation)
  implementation(libs.common.kotlin.coroutines.android)

  // unit tests
  testImplementation(libs.tests.junit)
}
