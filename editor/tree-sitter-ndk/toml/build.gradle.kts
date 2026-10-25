import com.itsaky.androidide.build.config.BuildConfig

plugins { id("com.android.library")
  alias(libs.plugins.com.google.devtools.ksp)
}

android {
  namespace = "com.itsaky.androidide.treesitter.toml"
  ndkVersion = BuildConfig.ndkVersion

  defaultConfig {
    ndk { abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")) }

    externalNativeBuild { cmake { arguments("-DCMAKE_CXX_FLAGS=-std=c++17") } }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.31.1"
    }
  }
  buildFeatures { buildConfig = false }
}

dependencies {
  implementation(projects.editor.treeSitterNdk.androidTreeSitter)
  implementation(projects.editor.treeSitterNdk.annotations)
  ksp(projects.editor.treeSitterNdk.annotationProcessors)
}
