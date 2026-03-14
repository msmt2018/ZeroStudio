import com.itsaky.androidide.build.config.BuildConfig
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {

  alias(libs.plugins.android.library)
    // id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    
}
val dbSchemaLocation="$projectDir/schemas"
room {
    schemaDirectory(dbSchemaLocation)
}
android {

    val packageName = "com.catpuppyapp.puppygit.play.pro"

    namespace = packageName
    compileSdk = BuildConfig.compileSdk
    ndkVersion = BuildConfig.ndkVersion

    defaultConfig {
        minSdk = BuildConfig.minSdk

        buildConfigField("String", "FILE_PROVIDIER_AUTHORITY", "\"$packageName.file_provider\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("arm64-v8a","x86_64","armeabi-v7a")
        }
        
        externalNativeBuild {
            cmake {
                arguments+= listOf("-DANDROID_STL=none") //none = 用于禁止NDK的任何C++标准库（STL（std::string、std::vector、std::map、std::algorithm 等 STL））
            }
        }
        multiDexEnabled = true 
    }
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.31.1"
        }
    }

    buildTypes {
        release {
            // isminifyenabled = false
            // isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "gson.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true 

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes.add("META-INF/INDEX.LIST")
            excludes.add("META-INF/io.netty.versions.properties")
            excludes.add("META-INF/versions/9/module-info.class")
        }
    }
}

dependencies {

    // file encoding detector
    implementation("com.github.albfernandez:juniversalchardet:2.5.0")
    implementation("org.eclipse.jdt:org.eclipse.jdt.annotation:2.3.100")
    implementation("org.jruby.joni:joni:2.2.6")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.snakeyaml:snakeyaml-engine:2.10")

    // start: temporary markdown dependencies, remove when 'compose-markdown' support custom coilStore(for load image from relative path)
    val markwonVersion = "4.6.2"
    val coilVersion = "2.7.0"
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("io.noties.markwon:core:$markwonVersion")
    implementation("io.noties.markwon:ext-strikethrough:$markwonVersion")
    implementation("io.noties.markwon:ext-tables:$markwonVersion")
    implementation("io.noties.markwon:html:$markwonVersion")
    implementation("io.noties.markwon:linkify:$markwonVersion")
    implementation("io.noties.markwon:ext-tasklist:$markwonVersion")
    implementation("com.github.jeziellago:Markwon:58aa5aba6a")
    // end: temporary markdown dependencies

    // 文件管理器显示图片缩略图
    implementation("io.coil-kt:coil:$coilVersion")
    implementation("io.coil-kt:coil-compose:$coilVersion")
    implementation("io.coil-kt:coil-gif:$coilVersion")
    implementation("io.coil-kt:coil-svg:$coilVersion")

    // ktor for http server (git pull/push api)
    val ktorVersion = "3.2.2"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")

    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("androidx.documentfile:documentfile:1.1.0")
    val lifecycleVersion = "2.9.2"
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    // room start
    val room_version = "2.7.2"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")
    // To use Kotlin Symbol Processing (KSP)
    ksp("androidx.room:room-compiler:$room_version")
    // optional - Kotlin Extensions and Coroutines support for Room
    implementation("androidx.room:room-ktx:$room_version")
    // room end

    // javax NonNull annotation for git24j
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    //启动屏幕
    implementation("androidx.core:core-splashscreen:1.0.1")

    val composeBom = platform("androidx.compose:compose-bom:2025.07.00")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended")
    // implementation("androidx.compose.material:material-pull-refresh:1.6.1")
     implementation(projects.core.resources)
     implementation(libs.common.editor)
     implementation(projects.modules.soraLanguageMonarch)
     implementation(libs.common.soraLanguageTextmate)
     // implementation(projects.modules.soraLanguageTreesitter)
     implementation(projects.modules.soraOnigurumaNative)
     
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5") //脱糖
    testImplementation(composeBom)
    testImplementation(libs.tests.junit)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kotlin {
    jvmToolchain(17)
}
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}