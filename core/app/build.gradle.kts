@file:Suppress("UnstableApiUsage")

import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.plugins.AndroidIDEAssetsPlugin
import java.io.FileInputStream
import java.util.Properties

plugins {
  id("com.itsaky.androidide.core-app")
  id("com.android.application")
  id("kotlin-android")
  id("kotlin-kapt")
  id("kotlin-parcelize")
  id("androidx.navigation.safeargs.kotlin")
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.google.firebase.firebase-perf")
}

apply { plugin(AndroidIDEAssetsPlugin::class.java) }

buildscript { dependencies { classpath(libs.logging.logback.core) } }

android {
  namespace = BuildConfig.packageName

  defaultConfig {
    applicationId = BuildConfig.packageName
    vectorDrawables.useSupportLibrary = true
  }

  androidResources { generateLocaleConfig = true }

  signingConfigs {
    create("all") {
      val localProperties = Properties()
      val localPropertiesFile = rootProject.file("signing.properties")
      enableV1Signing = true // 启用 V1 签名
      enableV2Signing = true // 启用 V2 签名 (推荐，Android 7.0+)
      enableV3Signing = true // 启用 V3 签名 (推荐，Android 9+)
      enableV4Signing = true
      if (localPropertiesFile.exists()) {
        localProperties.load(FileInputStream(localPropertiesFile))
        val storeFilePath = localProperties.getProperty("storeFile")
        val storePasswordValue = localProperties.getProperty("storePassword")
        val keyAliasValue = localProperties.getProperty("keyAlias")
        val keyPasswordValue = localProperties.getProperty("keyPassword")
        if (
            storeFilePath != null &&
                storePasswordValue != null &&
                keyAliasValue != null &&
                keyPasswordValue != null
        ) {
          storeFile = file(storeFilePath)
          storePassword = storePasswordValue
          keyAlias = keyAliasValue
          keyPassword = keyPasswordValue
        }
      }
    }
  }

  buildTypes {
    all { signingConfig = signingConfigs.getByName("all") }
    debug { isShrinkResources = false }
    release { isMinifyEnabled = false }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    isCoreLibraryDesugaringEnabled = true
  }
  composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
  buildFeatures { compose = true }
  packaging.resources.excludes +=
      listOf(
          "**/*.kotlin_builtins",
          "kotlin/kotlin.kotlin_builtins",
          "THIRD-PARTY",
          "META-INF/DEPENDENCIES",
          "META-INF/kotlin-stdlib.kotlin_module",
          "META-INF/NOTICE.md",
          "META-INF/plugin.xml",
          "META-INF/services/reactor.blockhound.integration.BlockHoundIntegration",
          "com/android/builder/model/version.properties",
          "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
      )
  packaging {
    resources {
      pickFirsts +=
          setOf(
              "messages/KotlinNJ2KServicesBundle.properties",
              "META-INF/io.netty.versions.properties",
              "META-INF/kotlinx_coroutines_core.version",
          )
    }
  }

  lint {
    abortOnError = false
    checkReleaseBuilds = false
    disable.addAll(arrayOf("VectorPath", "NestedWeights", "ContentDescription", "SmallSp"))
  }
}

kapt { arguments { arg("eventBusIndex", "${BuildConfig.packageName}.events.AppEventsIndex") } }

configurations.all {
  resolutionStrategy {
    force(libs.hamcrest.all)
    force(libs.tests.junit)
    force(libs.common.lsp4j.jsonrpc)
    force(libs.common.org.eclipse.lsp4j)

    force(libs.org.jetbrains.kotlin.stdlib)
    force(libs.org.jetbrains.kotlin.compiler)
    force(libs.org.jetbrains.kotlin.kotlin.scripting.jvm.host)
    force(libs.org.jetbrains.kotlin.ktscompiler)
    force(libs.org.jetbrains.kotlin.sam.with.receiver.compiler.plugin)
    force(libs.org.jetbrains.kotlin.reflect)
    force(libs.org.jetbrains.kotlin.jvm)

    force(libs.google.protobuf)
  }
  exclude(group = "com.google.firebase", module = "protolite-well-known-types")
  exclude(group = "com.google.protobuf", module = "protobuf-java")
  exclude(group = "com.android.tools.build", module = "builder-model")
}

dependencies {

  // Lottie Animation SDK
  implementation(libs.common.com.airbnb.android.lottie)

  // Annotation processors
  kapt(libs.common.glide.ap)
  kapt(libs.google.auto.service)
  kapt(projects.annotation.processors)

  implementation(libs.common.editor)
  implementation(libs.common.utilcode)
  implementation(libs.common.glide)
  implementation(libs.common.jsoup)
  implementation(libs.common.kotlin.coroutines.android)
  implementation(libs.common.retrofit)
  implementation(libs.common.retrofit.gson)
  implementation(libs.common.charts)
  implementation(libs.common.hiddenApiBypass)
  implementation(libs.aapt2.common)
  implementation(libs.common.org.apache.commons.compress)
  implementation(libs.common.org.tukaani.tarxzip)
  implementation(libs.common.org.eclipse.lsp4j)
  implementation(libs.bundles.io.markwon)

  implementation(libs.google.auto.service.annotations)
  implementation(libs.google.gson)
  implementation(libs.google.guava)
  implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
  // Add the dependency for the Performance Monitoring library
  // When using the BoM, you don't specify versions in Firebase library dependencies
  implementation("com.google.firebase:firebase-perf")

  // AndroidX
  implementation(libs.androidx.splashscreen)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.cardview)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.coordinatorlayout)
  implementation(libs.androidx.drawer)
  implementation(libs.androidx.grid)
  implementation(libs.androidx.nav.fragment)
  implementation(libs.androidx.nav.ui)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.transition)
  implementation(libs.androidx.vectors)
  implementation(libs.androidx.animated.vectors)
  implementation(libs.androidx.work)
  implementation(libs.androidx.work.ktx)
  implementation(libs.androidx.multidex)
  implementation(libs.google.material)
  implementation(libs.google.flexbox)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.crashlytics)
  implementation(libs.firebase.config)
  implementation(libs.androidx.compose.material.icons.extended)

  // UI/UX
  implementation(libs.bundles.compose) // androidx compose
  implementation(libs.androidx.core.ktx)

  implementation(libs.common.kotlin)

  // Dependencies in composite build
  implementation(libs.composite.appintro)
  implementation(libs.composite.javapoet)

  // java格式化
  implementation(libs.composite.googleJavaFormat) {
    exclude(group = "com.google.guava", module = "guava")
  }

  // Local projects here
  implementation(projects.core.actions)
  implementation(projects.core.common)
  implementation(projects.core.indexingApi)
  implementation(projects.core.indexingCore)
  implementation(projects.core.lspApi)
  implementation(projects.core.projects)
  implementation(projects.core.resources)
  implementation(projects.core.zeroMcpServer)
  implementation(projects.editor.impl)
  implementation(projects.editor.lexers)
  implementation(projects.editor.editorLsp)
  implementation(projects.java.javacServices)
  implementation(projects.java.lsp)
  implementation(projects.lsp.kotlin)
  implementation(projects.logging.idestats)
  implementation(projects.logging.logsender)
  implementation(projects.termux.application)
  implementation(projects.termux.view)
  implementation(projects.termux.emulator)
  implementation(projects.termux.shared)
  implementation(projects.termux.shell)
  implementation(projects.xml.aaptcompiler)
  implementation(projects.xml.lsp)
  implementation(projects.xml.utils)
  implementation(projects.tooling.api)
  implementation(projects.tooling.pluginConfig)
  compileOnly(projects.tooling.impl)
  implementation(projects.utilities.buildInfo)
  implementation(projects.utilities.lookup)
  implementation(projects.utilities.flashbar)
  implementation(projects.utilities.preferences)
  implementation(projects.utilities.templatesApi)
  implementation(projects.utilities.templatesImpl)
  implementation(projects.utilities.treeview)
  implementation(projects.utilities.fileTree)
  implementation(projects.utilities.xmlInflater)
  implementation(projects.event.eventbusAndroid)
  implementation(projects.event.eventbusEvents)
  implementation(projects.event.eventbus)
  implementation(projects.event.eventbusAndroid)
  implementation(projects.event.eventbusEvents)
  implementation(projects.modules.mtDataFilesProvider)
  implementation(projects.modules.deviceCompat)
  implementation(projects.core.git)
  implementation(projects.core.layoutEditor)
  // implementation(projects.core.chatai.app)
  // implementation(projects.core.chatai.ai)
  // implementation(projects.core.chatai.common)
  // implementation(projects.core.chatai.document)
  // implementation(projects.core.chatai.highlight)
  // implementation(projects.core.chatai.search)
  // implementation(projects.core.chatai.tts)
  implementation(projects.modules.zeroRegularPreview)
  implementation(projects.modules.composePreview)
  // implementation(projects.modules.colorpicker)
  implementation(libs.common.soraLanguageTextmate)

  coreLibraryDesugaring(libs.androidx.libDesugaring) // 脱糖
  testImplementation("org.conscrypt:conscrypt-openjdk:2.5.2")
  testImplementation(projects.testing.unitTest)
  androidTestImplementation(projects.testing.androidTest)
  debugImplementation(libs.common.leakcanary)
  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
}
