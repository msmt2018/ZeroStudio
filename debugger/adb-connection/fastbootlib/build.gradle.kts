plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "android.zero.studio.fastboot"
    compileSdk = 37

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
