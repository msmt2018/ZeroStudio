@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}

repositories {
  google()
  mavenCentral()
}

extensions.configure<LibraryExtension> {
  namespace = "androidx.compose.material3.pullrefresh"
  buildFeatures { compose = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  composeOptions { kotlinCompilerExtensionVersion = "2.2.0" }
}

dependencies {
  compileOnly(libs.bundles.compose)
  compileOnly("androidx.compose.foundation:foundation:1.10.0")
}

kotlin { jvmToolchain(11) }

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}
