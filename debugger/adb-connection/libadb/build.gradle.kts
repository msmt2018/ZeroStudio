// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

plugins {
    alias(libs.plugins.android.library)
}

group = "android.zero.studio"
version = "3.1.0"

android {
    namespace = "android.zero.studio.adb"
    compileSdk = 36

    defaultConfig {
        minSdk = 14
        aarMetadata {
            minCompileSdk = 1
        }
    }

    testOptions {
        targetSdk = 36
    }

    lint {
        baseline = file("lint-baseline.xml")
        targetSdk = 36
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    implementation(libs.bcprov.jdk15to18)
    implementation(libs.spake2.android)

    testImplementation(libs.tests.junit)
}
