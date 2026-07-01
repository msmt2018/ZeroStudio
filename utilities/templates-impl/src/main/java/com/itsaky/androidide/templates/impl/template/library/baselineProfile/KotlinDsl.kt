package com.itsaky.androidide.templates.impl.template.library.baselineProfile

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder

/**
 * 生成 `:baselineprofile` 模块的 `build.gradle.kts`。
 *
 * 关键点：
 *  - 使用 `com.android.test` 而非 `com.android.library` 插件（android.test 模块专属）。
 *  - 应用 `androidx.baselineprofile` 插件以自动生成 baseline-prof.txt。
 *  - 依赖全部走 `libs.xxx.xxx` 引用（要求项目启用 toml）。
 *  - `targetProjectPath` 指向待测的 `:app` 模块。
 *
 * 注意：Groovy 版本见 [baselineProfileGroovyDsl]（在 GroovyDsl.kt 中）。
 * 两侧字段必须保持同步；如果修改本文件，请同步修改 GroovyDsl.kt。
 */
internal fun AndroidModuleTemplateBuilder.baselineProfileKtDsl(): String {
  val namespace = data.packageName
  val compileSdk = data.versions.compileSdk.api
  val target = ":app"
  return """
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "$namespace"
    compileSdk = $compileSdk

    targetProjectPath = "$target"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        managedDevices {
            // 在此列出要在 CI 上运行的设备
            // localDevices {
            //     create("pixel6") {
            //         device = "Pixel 6"
            //         apiLevel = 31
            //     }
            // }
        }
    }
}

// 为 baselineprofile 插件指定入口生成器
baselineProfile {
    managedBenchmarkRules = listOf(
        "$namespace.BaselineProfileGenerator",
        "$namespace.StartupBenchmarks",
    )
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.profileinstaller)
}
""".trimIndent()
}
