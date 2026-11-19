plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
}

android {
  namespace = "com.itsaky.androidide.editorcore.compose"

  buildFeatures {
    compose = true
  }
}

dependencies {
  api(projects.editorCore.editorApi)
  implementation(projects.editorCore.editorText)
  implementation(projects.editorCore.editorUndo)
  implementation(projects.editorCore.editorLayout)
  implementation(projects.editorCore.editorInput)
  implementation(projects.editorCore.editorRender)

  implementation(platform("androidx.compose:compose-bom:2026.05.00"))
  implementation("androidx.compose.runtime:runtime")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
}
