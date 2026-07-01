package com.itsaky.androidide.templates.impl.template.library.baselineProfile

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder

/**
 * 生成 `:baselineprofile` 模块的 `build.gradle`（Groovy DSL）。
 *
 * 与 [baselineProfileKtDsl] 字段一一对应，仅在语法层面将 kts 转换为 groovy：
 *  - `${'$'}{...}` -> 直接取值或 GString
 *  - 字符串 ' 与 " 互换时按 Groovy 习惯保留
 *  - 去除 kts 专属的赋值 `=` 风格改为 `xxx =`
 *  - alias(libs.x) -> alias libs.x
 *
 * 如果 [baselineProfileKtDsl] 修改，请同步修改本文件。
 */
internal fun AndroidModuleTemplateBuilder.baselineProfileGroovyDsl(): String {
  val namespace = data.packageName
  val compileSdk = data.versions.compileSdk.api
  val target = ":app"
  return """
plugins {
    alias libs.plugins.android.test
    alias libs.plugins.baselineprofile
}

android {
    namespace = '$namespace'
    compileSdk = $compileSdk

    targetProjectPath = '$target'

    defaultConfig {
        testInstrumentationRunner = 'androidx.test.runner.AndroidJUnitRunner'
    }

    testOptions {
        managedDevices {
            // 在此列出要在 CI 上运行的设备
        }
    }
}

baselineProfile {
    managedBenchmarkRules = [
        '${namespace}.BaselineProfileGenerator',
        '${namespace}.StartupBenchmarks',
    ]
}

dependencies {
    implementation libs.androidx.benchmark.macro.junit4
    implementation libs.androidx.junit
    implementation libs.androidx.espresso.core
    implementation libs.androidx.uiautomator
    implementation libs.androidx.profileinstaller
}
""".trimIndent()
}
