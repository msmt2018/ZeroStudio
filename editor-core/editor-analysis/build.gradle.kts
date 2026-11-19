plugins {
  id("com.android.library")
  id("kotlin-android")
}

android {
  namespace = "android.zero.studio.editor.analysis"
}

// Intentionally dependency-free bootstrap module.
// Add implementation dependencies only after its public contracts are designed.
