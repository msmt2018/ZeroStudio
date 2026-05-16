plugins {
  id("com.android.library")
  id("kotlin-parcelize")
}

android {
  namespace = "android.zero.studio.chatai.server.mcp"
  compileSdk = 36

  defaultConfig {
    minSdk = 26

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // buildTypes {
  // release {
  // isMinifyEnabled = true
  // proguardFiles(
  // getDefaultProguardFile("proguard-android-optimize.txt"),
  // "proguard-rules.pro"
  // )
  // }
  // }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions { jvmTarget = "17" }

  buildFeatures {
    viewBinding = true
    buildConfig = true
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.google.material)
  implementation(libs.androidx.constraintlayout)

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.fragment.ktx)

  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.livedata.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.service)

  implementation(libs.common.org.nanohttpd)

  implementation(libs.google.gson)

  implementation(libs.androidx.documentfile)
  implementation(projects.core.resources)
  testImplementation(libs.tests.junit)
  androidTestImplementation(libs.tests.androidx.junit)
  androidTestImplementation(libs.tests.androidx.espresso.core)
}
