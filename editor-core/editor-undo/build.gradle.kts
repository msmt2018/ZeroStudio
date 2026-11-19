plugins {
  alias(libs.plugins.kotlin.jvm)
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  api(projects.editorCore.editorApi)
  implementation(projects.editorCore.editorText)
}
