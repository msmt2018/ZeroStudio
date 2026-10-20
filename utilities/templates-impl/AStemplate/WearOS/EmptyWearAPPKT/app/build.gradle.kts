plugins {
  alias(libs.plugins.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
}

android {
  namespace = "com.example.emptywearappkt"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.example.emptywearappkt"
    minSdk = 30
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes { release { optimization { enable = false } } }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  useLibrary("wear-sdk")
  buildFeatures { compose = true }
}

dependencies {
  implementation(platform(libs.compose.bom))
  implementation(libs.activity.compose)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui.tooling)
  implementation(libs.core.splashscreen)
  implementation(libs.play.services.wearable)
  implementation(libs.ui)
  implementation(libs.ui.graphics)
  implementation(libs.ui.tooling.preview)
  implementation(libs.wear.tooling.preview)
  androidTestImplementation(platform(libs.compose.bom))
  androidTestImplementation(libs.ui.test.junit4)
  debugImplementation(libs.ui.test.manifest)
  debugImplementation(libs.ui.tooling)
}
