plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "android.zero.studio.fastboot"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.tests.junit)
}
