import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
}

android {
    namespace = "android.zero.studio.terminal"
    android.buildFeatures.buildConfig = true
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }


}

dependencies {
    // AndroidX
    api(libs.androidx.appcompat)
    api(libs.androidx.constraintlayout)
    api(libs.androidx.activity.ktx)
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.palette.ktx)

    // Compose
    api(libs.androidx.activity.compose)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.navigation.compose)

    // Project modules
    api(project(":core:terminal-view"))
    api(project(":core:terminal-emulator"))

    // Other
    api(libs.common.utilcode)
    api(libs.okhttp)
    api(libs.anrwatchdog)
}
