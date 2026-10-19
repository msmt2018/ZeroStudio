import com.itsaky.androidide.build.config.BuildConfig

plugins {
  id("com.android.library")
}

android { namespace = "${BuildConfig.packageName}.lsp.smali" }

dependencies {
  implementation(projects.core.lspApi)
  implementation(projects.core.lspModels)
  implementation(projects.core.projects)
  implementation(projects.editor.api)
  implementation(projects.editor.impl)
  implementation(projects.editor.lexers)
  implementation(libs.common.kotlin)
}
