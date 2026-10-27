import com.itsaky.androidide.build.config.BuildConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.android.library)
  id("kotlin-parcelize")
  alias(libs.plugins.com.google.devtools.ksp)
  alias(libs.plugins.androidx.room)
  alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization)
  alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
}

val dbSchemaLocation = "$projectDir/schemas"

room { schemaDirectory(dbSchemaLocation) }

val anyAbi = "arm64-v8a"
val prebuiltNativeLibsAvailable =
    file("src/main/jniLibs/$anyAbi/libgit2.so").exists()

android {
  val packageName = "com.catpuppyapp.puppygit.play.pro"

  namespace = packageName
  compileSdk = BuildConfig.compileSdk
  ndkVersion = BuildConfig.ndkVersion

  defaultConfig {
    minSdk = BuildConfig.minSdk
    //versionName = "1.1.5.5"
    buildConfigField("String", "FILE_PROVIDIER_AUTHORITY", "\"$packageName.file_provider\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }
    ndk { abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a") }

    if (prebuiltNativeLibsAvailable) {
      externalNativeBuild {
        cmake {
          arguments +=
              listOf(
                  "-DANDROID_STL=none"
              ) // none = 用于禁止NDK的任何C++标准库（STL（std::string、std::vector、std::map、std::algorithm 等 STL））
        }
      }
    }
    multiDexEnabled = true
  }
  if (prebuiltNativeLibsAvailable) {
    externalNativeBuild {
      cmake {
        path = file("CMakeLists.txt")
        version = "3.31.1"
      }
    }
  } else {
    logger.warn(
        "core:git prebuilt native libs not found at src/main/jniLibs/*/libgit2.so; " +
            "skipping externalNativeBuild. The puppygit/git2j JNI will fail to load at runtime."
    )
  }

  buildTypes {
    release {
      // isminifyenabled = false
      // isShrinkResources = true
      proguardFiles(
          getDefaultProguardFile("proguard-android-optimize.txt"),
          "proguard-rules.pro",
          "gson.pro",
      )
    }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  buildFeatures {
    compose = true
    buildConfig = true
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
  implementation(libs.github.juniversalchardet)
  implementation(libs.common.org.eclipse.jdt.annotation)
  implementation(libs.common.org.jruby.joni)
  implementation(libs.google.gson)
  implementation(libs.common.snakeyaml.engine)

  // temporary markdown dependencies
  implementation(libs.androidx.appcompat)
  implementation(libs.bundles.io.markwon)

  // 文件管理器显示图片缩略图
  implementation(libs.bundles.io.coil)

  // ktor for http server
  implementation(libs.bundles.ktor.server)

  implementation(libs.common.bcrypt)
  implementation(libs.androidx.documentfile)
  
  // Lifecycle
  implementation(libs.bundles.androidx.lifecycle)
  
  // Room 数据库
  implementation(libs.bundles.androidx.room)
  ksp(libs.androidx.room.compiler)
  
  // javax NonNull annotation for git24j
  implementation(libs.google.findbugs.jsr305)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.navigation.compose)

  // 启动屏幕
  implementation(libs.androidx.splashscreen)

  // Compose 核心套件
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.bundles.compose)
  
  // 额外显式声明的扩展图标
  implementation(libs.androidx.compose.material.icons.extended)

  implementation(projects.core.resources)
  implementation(libs.common.editor)
  implementation(projects.modules.soraLanguageMonarch)
  implementation(libs.common.soraLanguageTextmate)
  implementation(projects.modules.soraOnigurumaNative)

  // 脱糖
  coreLibraryDesugaring(libs.androidx.libDesugaring) 
  // 测试相关
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.tests.junit)
  // Android 测代与 Espresso
  androidTestImplementation(libs.tests.androidx.junit)
  androidTestImplementation(libs.tests.androidx.espresso.core)
  // Compose 自动化测试与调试
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

}

kotlin { jvmToolchain(21) }

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
}
