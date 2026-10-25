/*
 * ZeroStudio IDE - 设备连接管理 connection 模块
 *
 * 包含连接逻辑实现:
 *   - AdbConnectionManager: RSA 2048 密钥对生成、X509 自签名证书、持久化
 *   - WifiAdbRepositoryImpl: mDNS 发现 + QR 配对 + TLS 连接 + 心跳保活 + 自动重连
 *   - OtgRepositoryImpl: USB OTG ADB 连接
 *   - FastbootRepositoryImpl: Fastboot 协议连接
 *   - AdbConnectionService / SelfPairingService: 前台服务保活
 *   - ViewModels: ShellViewModel / WifiAdbViewModel / OtgViewModel / FastbootViewModel
 *   - Room 数据库: WifiAdbDeviceDao / BookmarkDao
 *   - Hilt DI 模块: DatabaseModule / NetworkModule / RepositoryModule / ShellModule
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.com.google.devtools.ksp)
}

android {
    namespace = "android.zero.studio"

    compileSdk = 36

    defaultConfig {
        minSdk = 26  

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "VERSION_NAME", "\"v8.0.0-alpha01\"")
        buildConfigField("int", "VERSION_CODE", "63")
        buildConfigField("String", "DIST_FLAVOR_GITHUB", "\"github\"")
        buildConfigField("String", "DIST_FLAVOR_FDROID", "\"fdroid\"")
        buildConfigField("String", "FLAVOR", "DIST_FLAVOR_GITHUB")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**"
            )
        }
    }

    testOptions {
        targetSdk = 36
    }

    lint {
        targetSdk = 36
        abortOnError = false
    }


}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

ksp {
    arg("dagger.fastInit", "enabled")
    arg("dagger.hilt.disableModulesHaveInstallInCheck", "true")
    arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
}


dependencies {
    constraints {
        implementation("androidx.core:core-ktx:1.16.0")
        implementation("androidx.core:core:1.16.0")
    }
    // 项目内 ADB 协议库
    implementation(projects.debugger.adbConnection.adblib)
    implementation(projects.debugger.adbConnection.libadb)
    implementation(projects.debugger.adbConnection.fastbootlib)

    // Shizuku 客户端 (Local ADB 通道桥接系统服务)
    implementation(projects.modules.shizuku.api)
    implementation(projects.modules.shizuku.provider)

    // settings-dsl 模块 (SettingsProvider.kt 引用 android.zero.studio.settingsdsl.dsl.* 和 model.*)
    // 提供 switchItem()/clickableItem()/radioGroupItem()/buttonGroupItem()/settingsPage() 等 DSL 构建器
    implementation(projects.debugger.adbConnection.settingsDsl)

    // Kotlin 标准库 / 反射
    implementation(libs.org.jetbrains.kotlin.reflect)
    implementation(libs.common.kotlin)
    implementation(libs.common.kotlin.coroutines.android)

    // AndroidX 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Material Design
    implementation(libs.google.material)

    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.google.dagger.hilt.compiler)
    
    // 序列化
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.google.gson)

    // JmDNS: WiFi ADB mDNS 服务发现
    implementation(libs.jmdns)

    // Ktor 客户端 (mDNS 心跳/HTTP 通信)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.slf4j.android)

    // libsu: Root shell 探测/执行 (Local ADB Root 通道)
    implementation(libs.libsu.core)

    // Lottie 动画 (Onboarding/状态指示动画)
    implementation(libs.lottie.compose)

    // QR 码生成 (WiFi ADB Pairing 二维码)
    implementation(libs.nayuki.qrcode)

    // sun.security 替代实现 (libadb 需要)
    implementation(libs.sun.security.android)

    // LSposed HiddenApiBypass (反射系统隐藏 API)
    implementation(libs.common.hiddenApiBypass)

    // Coil 图片加载 (Compose)
    implementation(libs.io.coil.compose)
    implementation(libs.io.coil.okhttp)

    // AboutLibraries 开源协议清单
    implementation(libs.aboutlibraries.core)

    // 测试
    testImplementation(libs.tests.junit)
    androidTestImplementation(libs.tests.androidx.junit)
    androidTestImplementation(libs.tests.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
