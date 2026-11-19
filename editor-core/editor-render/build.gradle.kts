plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "com.itsaky.androidide.editorcore.render"
}

dependencies {
  api(projects.editorCore.editorApi)
  implementation(projects.editorCore.editorText)
  implementation(projects.editorCore.editorLayout)
  implementation(libs.androidx.core.ktx)
}
