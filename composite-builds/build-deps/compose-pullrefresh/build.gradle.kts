@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
  alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
  
}

repositories {
  google()
  mavenCentral()
}

android {
  namespace = "androidx.compose.material3.pullrefresh"
  compileSdk = 37
  defaultConfig { minSdk = 26 }
  buildFeatures { compose = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

dependencies {
  compileOnly(libs.bundles.compose)
  compileOnly("androidx.compose.foundation:foundation:1.10.0")
}

kotlin { jvmToolchain(21) }

  tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
    this.compilerOptions {
        this.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
