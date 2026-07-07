import com.itsaky.androidide.build.config.BuildConfig

/*
 * modules/web-preview
 *
 * Web 预览引擎 library:
 *   - androidx.webkit 1.17.0-alpha03 (系统 WebView compat 层, APK 0 增加)
 *   - Compose WebView 封装 (WebPreviewEngine + WebViewState + WebContent)
 *   - 设备参数库 (DeviceProfile, 11 档预置)
 *   - 后端运行时抽象 (BackendRuntime + TermuxBackendRuntime, 同进程 Termux 集成)
 *   - Chrome DevTools 桥接 (CDP unix socket → localhost TCP, 内嵌完整 DevTools UI)
 *
 * 对外暴露 com.zerostudio.webpreview.* 下全部 API, 由 core/app 的
 * WebPreviewFragment 集成组装 UI (用 FrostedComponents 磨砂玻璃控件)。
 *
 * 不含 UI 主题代码 (FrostedGlass 在 core/app, 避免循环依赖)。
 */
plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
}

android {
  namespace = "com.zerostudio.webpreview"

  compileSdk = BuildConfig.compileSdk

  defaultConfig {
    minSdk = BuildConfig.minSdk
    consumerProguardFiles("consumer-rules.pro")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    viewBinding = false
    compose = true
  }
}

dependencies {
  // androidx.webkit — 系统 WebView compat 层 (WebSettingsCompat.setForceDark 等)
  implementation(libs.androidx.webkit)

  // Compose — AndroidView 嵌入 WebView
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.bundles.compose)
  implementation(libs.androidx.compose.foundation)

  // Fragment / AppCompat — Fragment 宿主
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.appcompat)

  // Kotlin 协程 — BackendRuntime suspend fun
  implementation(libs.common.kotlin.coroutines.android)

  // Termux 同进程集成 — TermuxBackendRuntime 用 TermuxCommand DSL
  implementation(projects.termux.shell)
  implementation(projects.termux.shared)

  // SLF4J — 日志
  implementation(libs.tooling.slf4j)

  testImplementation(libs.tests.junit)
}
