import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.plugins.NoDesugarPlugin

plugins { id("com.android.library") }

apply { plugin(NoDesugarPlugin::class.java) }

description =
    "ide-debugger: ZeroStudio's pure-Kotlin JDWP debugger engine. Runs " +
        "on the IDE side and talks JDWP to the host application's JDWP " +
        "server (which is started by the ide-log-plugin AAR injected into " +
        "the debug variant of every project built with ZeroStudio)."

android {
  namespace = "${BuildConfig.packageName}.zerostudio.ide.debugger"

  defaultConfig {
    minSdk = 21
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions { jvmTarget = "17" }

  buildFeatures {
    aidl = false
    viewBinding = false
  }

  // PR-7: enable JUnit 4 unit tests for the parser.
  testOptions {
    unitTests.isReturnDefaultValues = true
  }
}

dependencies {
  compileOnly(libs.androidx.core.ktx)
  compileOnly(libs.androidx.annotation)
  compileOnly(libs.kotlin.stdlib)
  compileOnly(libs.kotlinx.coroutines.core)
  compileOnly(libs.kotlinx.coroutines.android)

  // PR-2: depend on the shared logwire module for the IDE ↔ plugin
  // transport that carries JDWP-bridge notifications.
  implementation(project(":utilities:logwire"))

  // Phase G1: JavaParser for extracting class signatures from .java source files.
  implementation(libs.common.javaparser)

  // Phase G2: ASM for reading .class files (LineNumberTable, SourceFile attribute).
  implementation(libs.common.asm)

  // PR-7: unit tests for EvalEngine.Parser.
  testImplementation(libs.tests.junit)
}
