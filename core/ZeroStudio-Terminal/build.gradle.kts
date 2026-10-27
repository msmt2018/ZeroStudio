import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
}

android {
    namespace = "android.zero.studio.termux"
    android.buildFeatures.buildConfig = true
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }
    
// composeCompiler {
    // // 启用强跳过模式（Kotlin 2.0.20 开始已默认开启，低版本可手动开启）
    // enableStrongSkippingMode = true
    
    // // 如果需要输出稳定性报告（用于性能调优），可以配置输出目录
    // // metricsDestination = layout.buildDirectory.dir("compose_compiler/metrics")
    // // reportsDestination = layout.buildDirectory.dir("compose_compiler/reports")
// }


}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
    this.compilerOptions {
        this.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
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
    api(project(":termux:proot"))

    // Other
    api(libs.common.utilcode)
    api(libs.okhttp)
    api(libs.anrwatchdog)
}