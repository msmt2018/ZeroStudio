plugins {
    alias(libs.plugins.android.library)
    // 关键修复: 必须显式声明 Kotlin 插件, 否则此模块的 Kotlin 源文件不会被编译,
    // 导致 connection 模块中所有 fastbootlib 类型 (FastbootCommand/FastbootResponse/
    // ResponseStatus/FastbootDeviceContext 等) 出现 Unresolved reference 错误。
    id("kotlin-android")
}

android {
    namespace = "android.zero.studio.fastboot"
    compileSdk = 36

    defaultConfig {
        minSdk = 26  // 与 app 模块一致 (app minSdk=26), 避免清单合并冲突
    }

    testOptions {
        targetSdk = 36
    }

    lint {
        targetSdk = 36
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.tests.junit)
}
