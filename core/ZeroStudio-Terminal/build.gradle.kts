import com.android.build.api.dsl.LibraryExtension
import com.itsaky.androidide.plugins.TerminalBootstrapPackagesPlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
}

apply { plugin(TerminalBootstrapPackagesPlugin::class.java) }

extensions.configure<LibraryExtension> {
    namespace = "android.zero.studio.termux"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }

    externalNativeBuild {
        ndkBuild { path = file("src/main/cpp/Android.mk") }
    }

    packaging.jniLibs.useLegacyPackaging = true
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    // AndroidX
    api(libs.androidx.appcompat)
    api(libs.google.material)
    api(libs.androidx.constraintlayout)
    api(libs.androidx.navigation.fragment)
    api(libs.androidx.navigation.ui)
    api(libs.androidx.nav.fragment)
    api(libs.androidx.nav.ui)
    api(libs.androidx.activity)
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)

    // Compose
    api(libs.androidx.activity.compose)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.material3)
    api(libs.androidx.navigation.compose)
    api(libs.androidx.compose.material.icons.core)
    api(libs.androidx.palette.ktx)
    api(libs.com.google.accompanist.systemuicontroller)

    // Project modules (termux)
    api(project(":termux:view"))
    api(project(":termux:emulator"))
    api(project(":termux:shared"))

    // Other
    api(libs.common.utilcode)
    api(libs.okhttp)
    api(libs.anrwatchdog)
}
