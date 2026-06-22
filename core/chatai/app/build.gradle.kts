import com.android.build.api.dsl.Packaging
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.com.google.devtools.ksp)
}

android {
    namespace = "me.rerere.rikkahub"
    compileSdk = 36

    defaultConfig {
        // applicationId = "me.rerere.rikkahub"
        minSdk = 26
        // targetSdk = 37
        // versionCode = 165
        // versionName = "2.3.2"

        buildConfigField("String", "VERSION_NAME", "\"2.2.3\"")
        buildConfigField("int", "VERSION_CODE", "159")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 把 consumer-rules.pro 显式列出来, AAR 消费方 (:core:app) 一旦开启
        // isMinifyEnabled = true 就能自动拿到 chatai/app 的反射入口 keep 规则。
        // 当前 :core:app 的 release 还是 false, 但写在这里以防未来切换。
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // splits {
        // abi {
            // // AppBundle tasks usually contain "bundle" in their name
            // //noinspection WrongGradleMethod
            // val isBuildingBundle = gradle.startParameter.taskNames.any { it.lowercase().contains("bundle") }
            // isEnable = !isBuildingBundle
            // reset()
            // include("arm64-v8a", "x86_64")
            // isUniversalApk = true
        // }
    // }

    // signingConfigs {
        // create("release") {
            // val localProperties = Properties()
            // val localPropertiesFile = rootProject.file("local.properties")

            // if (localPropertiesFile.exists()) {
                // localProperties.load(FileInputStream(localPropertiesFile))

                // val storeFilePath = localProperties.getProperty("storeFile")
                // val storePasswordValue = localProperties.getProperty("storePassword")
                // val keyAliasValue = localProperties.getProperty("keyAlias")
                // val keyPasswordValue = localProperties.getProperty("keyPassword")

                // if (storeFilePath != null && storePasswordValue != null &&
                    // keyAliasValue != null && keyPasswordValue != null
                // ) {
                    // storeFile = file(storeFilePath)
                    // storePassword = storePasswordValue
                    // keyAlias = keyAliasValue
                    // keyPassword = keyPasswordValue
                // }
            // }
        // }
    // }

    buildTypes {
        release {
            // signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            // isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            // buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
        }
        debug {
            // applicationIdSuffix = ".debug"
            // buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            // buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
        }
        // create("baseline") {
            // initWith(getByName("release"))
            // matchingFallbacks.add("release")
            // // signingConfig = signingConfigs.getByName("debug")
            // applicationIdSuffix = ".debug"
            // isDebuggable = false
            // isMinifyEnabled = false
            // isShrinkResources = false
            // isProfileable = true
        // }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
    // androidResources {
        // generateLocaleConfig = true
    // }
    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += "lib/*/libtermux.so"
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        compilerOptions.optIn.add("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
        compilerOptions.optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        compilerOptions.optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
        compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        compilerOptions.optIn.add("androidx.navigation3.runtime.ExperimentalNavigation3Api")
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(
        project.layout.projectDirectory.file("compose_compiler_config.conf")
    )
}

tasks.register("buildAll") {
    dependsOn("assembleRelease", "bundleRelease")
    description = "Build both APK and AAB"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// kotlin { jvmToolchain(17) }
tasks.withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.profileinstaller)
    implementation(projects.termux.view)
    implementation(libs.guava.listenablefuture)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.material3.adaptive.navigation3)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Image metadata extractor
    // https://github.com/drewnoakes/metadata-extractor
    implementation(libs.metadata.extractor)

    // Haze (background blur)
    implementation(libs.haze)
    implementation(libs.haze.blur.materials)
    implementation(libs.haze.blur)

    // koin
    // Exposed via api() so that consumers (e.g. core/app, which owns the
    // Application class and therefore calls startKoin) can resolve the
    // Koin DSL symbols (startKoin, androidLogger, androidContext,
    // workManagerFactory, modules) and the Module class itself without
    // having to redeclare the same Koin coordinates.
    api(platform(libs.koin.bom))
    api(libs.koin.android)
    api(libs.koin.compose)
    api(libs.koin.androidx.workmanager)

    // jetbrains markdown parser
    implementation(libs.jetbrains.markdown)

    // okhttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.common.retrofit)
    implementation(libs.retrofit.serialization.json)

    // ktor client
    implementation(libs.io.ktor.client.core)
    implementation(libs.io.ktor.client.okhttp)
    implementation(libs.io.ktor.client.content.negotiation)
    implementation(libs.io.ktor.serialization.kotlinx.json)

    // ucrop
    implementation(libs.ucrop)

    // pebble (template engine)
    implementation(libs.pebble)

    // java-diff-utils (unified diff)
    implementation(libs.diffutils)

    // coil
    implementation(libs.io.coil.compose)
    implementation(libs.io.coil.gif)
    implementation(libs.io.coil.okhttp)
    implementation(libs.io.coil.svg)
    implementation(libs.io.coil.cache.control)

    // serialization
    implementation(libs.kotlinx.serialization.json)

    // zxing
    implementation(libs.zxing.core)

    // quickie (qrcode scanner)
    implementation(libs.quickie.bundled)
    implementation(libs.barcode.scanning)
    implementation(libs.androidx.camera.core)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // Paging3
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Apache Commons Text
    implementation(libs.commons.text)

    // Toast (Sonner)
    implementation(libs.sonner)

    // Reorderable (https://github.com/Calvin-LL/Reorderable/)
    implementation(libs.reorderable)

    // lucide icons
    implementation(libs.lucide.icons)
    implementation(libs.huge.icons)

    // image viewer
    implementation(libs.image.viewer)

    // JLatexMath
    // https://github.com/rikkahub/jlatexmath-android
    implementation(libs.jlatexmath)
    implementation(libs.jlatexmath.font.greek)
    implementation(libs.jlatexmath.font.cyrillic)

    // mcp
    implementation(libs.modelcontextprotocol.kotlin.sdk)

    // jmDNS (mDNS/Bonjour for .local hostname)
    implementation(libs.jmdns)

    // Use only the SLF4J API here. The host app provides the single runtime SLF4J
    // provider (logback-classic via :logging:logger). Adding another provider such as
    // uk.uuid.slf4j:slf4j-android makes SLF4J choose that factory at runtime and
    // breaks IDELogFragment, which attaches a Logback appender to the root logger.
    implementation(libs.tooling.slf4j)

    // sqlite-android (requery SQLite for Android)
    implementation(libs.sqlite.android)

    // modules
    implementation(projects.core.common)
    implementation(projects.termux.application)
    implementation(projects.core.chatai.ai)
    implementation(projects.core.chatai.web)
    implementation(projects.core.chatai.document)
    implementation(projects.core.chatai.highlight)
    implementation(projects.core.chatai.search)
    implementation(projects.core.chatai.speech)
    implementation(projects.core.chatai.common)
    implementation(projects.core.chatai.material3)
    implementation(projects.core.chatai.workspace)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(kotlin("reflect"))

    // Leak Canary
    // debugImplementation(libs.leakcanary.android)

    // tests
    testImplementation(libs.tests.junit)
    androidTestImplementation(libs.tests.androidx.junit)
    androidTestImplementation(libs.tests.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
