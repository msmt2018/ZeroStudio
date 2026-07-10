import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.plugins.NoDesugarPlugin

plugins { id("com.android.library") }

apply { plugin(NoDesugarPlugin::class.java) }

description =
    "ide-log-plugin: ZeroStudio's debug-time host plugin injected into " +
        "the debug variant of every project built with the IDE. Provides " +
        "the JDWP server (PR-2) and the logcat streaming service used by " +
        "the IDE debugger and log viewer."

android {
  namespace = "${BuildConfig.packageName}.zerostudio.ide.logplugin"

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
  compileOnly(libs.androidx.annotation)

  // Shared wire protocol module: the IDE side and the host-plugin
  // side both consume this so the two ends can never disagree on
  // message layout.
  implementation(project(":debugger:log-runtime:logwire"))

  testImplementation(libs.tests.junit)
}
