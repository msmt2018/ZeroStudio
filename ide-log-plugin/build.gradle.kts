import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.plugins.NoDesugarPlugin

plugins { id("com.android.library") }

apply { plugin(NoDesugarPlugin::class.java) }

description =
    "ide-log-plugin: A consolidated log service + JDWP bridge plugin. Packaged as an AAR and " +
        "injected into the debug variant of the user's project to provide a stable, " +
        "high-fidelity log channel (logcat, crash, ANR, JNI native) and a JDWP debug server " +
        "that the ZeroStudio IDE can attach to."

android {
  namespace = "${BuildConfig.packageName}.ide.logplugin"

  defaultConfig {
    minSdk = 21
    vectorDrawables.useSupportLibrary = true
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions { jvmTarget = "17" }

  buildFeatures {
    aidl = true
    viewBinding = false
  }
}

dependencies {
  // Provided as compileOnly so that the host application brings its own logback
  // implementation. In the IDE we provide the same coordinates via the main project.
  compileOnly(libs.logging.logback.classic) {
    exclude(group = "ch.qos.logback", module = "logback-core")
  }
  compileOnly(libs.logging.logback.core)
  compileOnly(libs.tooling.slf4j)

  // The plugin needs to be portable: the host application may not include all of
  // the AndroidX pieces we use, so declare them compileOnly.
  compileOnly(libs.androidx.core.ktx)
  compileOnly(libs.androidx.annotation)
  compileOnly(libs.androidx.lifecycle.process)
  compileOnly(libs.kotlin.stdlib)
  compileOnly(libs.kotlinx.coroutines.core)
  compileOnly(libs.kotlinx.coroutines.android)

  // PR-1: depend on the shared wire protocol library so that the IDE
  // and the host application stay in lock-step on packet format.
  implementation(project(":utilities:logwire"))
}
