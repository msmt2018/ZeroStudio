// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

plugins {
    alias(libs.plugins.android.library)
}

group = "android.zero.studio"
version = "3.1.0"

android {
    namespace = "android.zero.studio.adb"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        aarMetadata {
            minCompileSdk = 1
        }
    }


    lint {
        baseline = file("lint-baseline.xml")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.spake2.android)

    testImplementation(libs.tests.junit)
}
