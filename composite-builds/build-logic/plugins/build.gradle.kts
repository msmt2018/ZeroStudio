import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-gradle-plugin`
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.sam.with.receiver)
}

group = "com.zerostudio.idea.buildlogic"

samWithReceiver {
  annotation("org.gradle.api.HasImplicitReceiver")
}

repositories {
  google()
  gradlePluginPortal()
  mavenCentral()
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

dependencies {
  compileOnly(gradleKotlinDsl())
  compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
  compileOnly("org.jetbrains.kotlin:kotlin-sam-with-receiver:2.4.10")
    
  implementation(projects.buildLogic.common)
  implementation(projects.buildLogic.propertiesParser)

  implementation("com.android.tools.build:gradle:${libs.versions.agp.asProvider().get()}")
  implementation(libs.maven.publish)
  implementation(libs.kotlin.gradle.plugin)

  implementation(libs.common.jkotlin)
  implementation(libs.common.antlr4)
  implementation(libs.google.gson)
  implementation(libs.google.java.format)
}

gradlePlugin {
  plugins {
    register("build-logic.root-project") {
      id = "build-logic.root-project"
      implementationClass = "com.itsaky.androidide.plugins.AndroidIDERootProjectPlugin"
    }

    register("com.itsaky.androidide.build") {
      id = "com.itsaky.androidide.build"
      implementationClass = "com.itsaky.androidide.plugins.AndroidIDEPlugin"
    }
    register("com.itsaky.androidide.core-app") {
      id = "com.itsaky.androidide.core-app"
      implementationClass = "com.itsaky.androidide.plugins.AndroidIDECoreAppPlugin"
    }
    register("com.itsaky.androidide.build.propsparser") {
      id = "com.itsaky.androidide.build.propsparser"
      implementationClass = "com.itsaky.androidide.plugins.PropertiesParserPlugin"
    }
    register("com.itsaky.androidide.build.lexergenerator") {
      id = "com.itsaky.androidide.build.lexergenerator"
      implementationClass = "com.itsaky.androidide.plugins.LexerGeneratorPlugin"
    }
  }
}