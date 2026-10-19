import com.itsaky.androidide.build.config.BuildConfig

plugins {
  id("com.android.library")
  id("kotlin-parcelize")
}

android {
  namespace = "android.zero.studio.images.preview"
  compileSdk = BuildConfig.compileSdk
  ndkVersion = BuildConfig.ndkVersion

  defaultConfig {
    minSdk = BuildConfig.minSdk
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk { abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a") }

    externalNativeBuild {
      cmake {
        arguments += listOf("-DANDROID_STL=c++_static")
        cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
      }
    }
  }

  externalNativeBuild {
    cmake {
      path = file("CMakeLists.txt")
      version = "3.22.1"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures { viewBinding = true }
}

dependencies {
  // Coil - 图片加载基础
  val coilVersion = "2.7.0"
  implementation("io.coil-kt:coil:$coilVersion")
  implementation("io.coil-kt:coil-gif:$coilVersion")
  implementation("io.coil-kt:coil-svg:$coilVersion") // Coil 也带 SVG，但我们可以优先用 ThorVG

  // AndroidX & Material
  implementation("androidx.core:core-ktx:1.12.0")
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("com.google.android.material:material:1.11.0")

  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5") // 脱糖
  testImplementation(libs.tests.junit)
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
