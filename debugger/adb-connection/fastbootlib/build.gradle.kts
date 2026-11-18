plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
}

android {
    namespace = "android.zero.studio.fastboot"

    defaultConfig {
    }

    testOptions {
    }

    lint {
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
