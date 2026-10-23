import com.itsaky.androidide.build.config.BuildConfig

plugins { id("com.android.library") }

android {
  namespace = "android.studio.zero.regular.expression.preview"
  compileSdk = BuildConfig.compileSdk
  ndkVersion = BuildConfig.ndkVersion

  defaultConfig {
    minSdk = BuildConfig.minSdk
    consumerProguardFiles("consumer-rules.pro")
    externalNativeBuild {
      cmake {
        cppFlags("-std=c++17 -Wno-register -Wno-deprecated-register")
        cFlags("-Wno-deprecated-declarations")
        arguments("-DANDROID_STL=c++_shared")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.31.1"
    }
  }

  buildFeatures { viewBinding = true }
}

dependencies {
  implementation("com.google.code.gson:gson:2.10.1")
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("com.google.android.material:material:1.9.0")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")

  implementation(projects.modules.thinkmapTreeview)
  implementation(projects.modules.soraOnigurumaNative)
  implementation(projects.core.resources)
}
