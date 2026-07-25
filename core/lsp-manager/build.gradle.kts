import com.itsaky.androidide.build.config.BuildConfig

plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "${BuildConfig.packageName}.lsp.manager"
}

dependencies {
  api(projects.core.lspApi)
  api(projects.editor.editorLsp)
  implementation(libs.common.org.eclipse.lsp4j)
  implementation(libs.common.lsp4j.jsonrpc)
  implementation(libs.kotlinx.coroutines.core)
  compileOnly(libs.common.editor)
}
