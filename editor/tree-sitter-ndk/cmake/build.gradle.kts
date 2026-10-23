import com.itsaky.androidide.build.config.BuildConfig

plugins { id("com.android.library") }

android {
  namespace = "com.itsaky.androidide.treesitter.cmake"
  ndkVersion = BuildConfig.ndkVersion

  defaultConfig {
    ndk { abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")) }

    externalNativeBuild { cmake { arguments("-DCMAKE_CXX_FLAGS=-std=c++17") } }
  }
  buildFeatures { buildConfig = false }
  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.31.1"
    }
  }
}

dependencies {
  implementation(projects.editor.treeSitterNdk.androidTreeSitter)
  implementation(projects.editor.treeSitterNdk.annotations)
  annotationProcessor(projects.editor.treeSitterNdk.annotationProcessors)
}
