import com.itsaky.androidide.build.config.BuildConfig

plugins {
  id("com.android.library")
  id("kotlin-android")
}

android {
  namespace = "android.zero.studio.editor.core"
}

// Intentionally dependency-free bootstrap module.
// Add implementation dependencies only after its public contracts are designed.
