import com.itsaky.androidide.build.config.BuildConfig

plugins {
  id("com.android.library")
}

android { namespace = "${BuildConfig.packageName}.lsp.groovy" }

dependencies {
  implementation(files("libs/groovy.jar"))

  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.12.0")
  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.12.0")
  // implementation("org.apache.groovy:groovy:4.0.26")
  implementation("com.google.code.gson:gson:2.13.1")
  implementation("io.github.classgraph:classgraph:4.8.179")
  
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.1")
  
  implementation(projects.core.lspApi)
  implementation(projects.core.lspModels)
  implementation(projects.core.projects)
  implementation(projects.editor.lexers)
  implementation(libs.common.kotlin)
}
