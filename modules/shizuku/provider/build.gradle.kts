plugins {
	id("com.android.library")
}

android {
    namespace 'rikka.shizuku.provider'
    defaultConfig {
        consumerProguardFiles "consumer-rules.pro"
    }
    buildFeatures {
        buildConfig false
    }
    buildTypes {
        release {
            minifyEnabled false
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
	implementation(projects.modules.shizuku.api)
	implementation(libs.androidx.annotation)
}
