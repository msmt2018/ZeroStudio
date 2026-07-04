plugins {
	id("com.android.library")
}

android {
    namespace = "rikka.shizuku.shared"
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
	implementation(projects.modules.shizuku.aidl)
	implementation(libs.androidx.annotation)
}
