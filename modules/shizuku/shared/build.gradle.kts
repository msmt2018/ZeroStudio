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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
	implementation(projects.modules.shizuku.aidl)
	implementation(libs.androidx.annotation)
}
