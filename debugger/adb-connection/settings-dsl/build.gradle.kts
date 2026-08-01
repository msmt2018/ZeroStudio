import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    // 关键修复: 显式声明 Kotlin 插件, 否则此模块的 Kotlin DSL 源文件不会被编译,
    // 导致 connection 模块中所有 android.zero.studio.settingsdsl.* 引用出现 Unresolved reference 错误。
    id("kotlin-android")
    // 使用 Compose Compiler 插件 (与 connection 模块一致), 替代原项目的
    // org.jetbrains.compose (JetBrains Compose Multiplatform) 插件。
    alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
    // 注意: 移除原项目 alias(libs.plugins.compose.stability.analyzer),
    // 该插件别名在本项目版本目录中不存在, 应用会导致构建失败。
}

android {
    namespace = "android.zero.studio.settingsdsl"
    // 关键修复: compileSdk 37 需 AGP 9.0+, 本项目使用 AGP 8.13.2 + compileSdk 36,
    // 必须降级到 36 以兼容项目工具链 (用户硬性约束: 保持 8.13 agp 版本)。
    compileSdk = 36

    defaultConfig {
        // 与 :core:app 模块一致 (app minSdk=26), 避免清单合并失败。
        // settings-dsl 是纯 Kotlin DSL + Compose UI, Compose 最低要求 minSdk 21,
        // 26 完全覆盖所需 API, 无需更高 SDK 级别。
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // 关键修复: 原项目使用 libs.compose.bom / libs.compose.ui / libs.material3 /
    // libs.material.icons.extended / libs.annotation 等无前缀别名, 这些别名在本项目
    // 版本目录中不存在。本项目统一使用 androidx- 前缀别名。
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.annotation)
}
