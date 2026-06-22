import com.itsaky.androidide.build.config.BuildConfig

plugins { id("com.android.library") }

description =
    "logwire: shared wire protocol types for the ZeroStudio IDE and the " +
        "ide-log-plugin AAR. This is a tiny, dependency-free library."

android {
  namespace = "${BuildConfig.packageName}.zerostudio.logwire"

  defaultConfig {
    minSdk = 21
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  // Keep android annotations compileOnly so the module can be consumed by
  // both the IDE and the host application without forcing a specific
  // AndroidX version.
  compileOnly("androidx.annotation:annotation:1.7.1")
}
