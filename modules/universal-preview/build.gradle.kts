import com.itsaky.androidide.build.config.BuildConfig

/*
 * modules/universal-preview
 *
 * 一站式 3D/2D 图形空间渲染预览组件:
 *   核心A — WebView + Three.js (静态 AST / 代码拓扑 / 数据可视化)
 *   核心B — GLSurfaceView + JNI C++ NDK (高帧率 3D 模型 / 物理算法 / Dear ImGui)
 *
 * 对外暴露 UniversalPreviewEngineFragment, 由 core/app 集成。
 * 本地 C++ 引擎编译为 libnative_preview_engine.so。
 */
plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "com.zerostudio.preview"

  compileSdk = BuildConfig.compileSdk
  ndkVersion = BuildConfig.ndkVersion

  defaultConfig {
    minSdk = BuildConfig.minSdk

    // GLES 3.0 + ImGui 需要的 ABI; 跟 image-preview 一致
    ndk { abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a") }

    externalNativeBuild {
      cmake {
        arguments += listOf("-DANDROID_STL=c++_static")
        cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
      }
    }

    consumerProguardFiles("consumer-rules.pro")
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures { viewBinding = false }
}

dependencies {
  // AndroidX Fragment / AppCompat — Fragment 宿主 + WebView / GLSurfaceView 基础
  implementation("androidx.fragment:fragment:1.6.2")
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("com.google.android.material:material:1.11.0")
  implementation("androidx.annotation:annotation:1.7.1")

  testImplementation(libs.tests.junit)
}
