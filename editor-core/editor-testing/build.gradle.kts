plugins {
  alias(libs.plugins.kotlin.jvm)
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  testImplementation(projects.editorCore.editorApi)
  testImplementation(projects.editorCore.editorText)
  testImplementation(projects.editorCore.editorUndo)
  testImplementation(projects.editorCore.editorLayout)
  testImplementation(projects.editorCore.editorLanguage)
  testImplementation(projects.editorCore.editorDiagnostics)
  testImplementation(projects.editorCore.editorExtensions)
  testImplementation(libs.tests.junit)
  testImplementation(libs.tests.google.truth)
}
