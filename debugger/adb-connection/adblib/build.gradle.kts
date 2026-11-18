plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "android.zero.studio.adblib"

    defaultConfig {
    }

    testOptions {
    }

    lint {
        baseline = file("lint-baseline.xml")
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
    api(fileTree("libs") { include("*.jar") })
    testImplementation(libs.tests.junit)
}
