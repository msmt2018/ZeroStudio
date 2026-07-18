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
  // Hilt 依赖注入 (设备连接管理 connection 模块需要)
  alias(libs.plugins.hilt)
  alias(libs.plugins.com.google.devtools.ksp)
  alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization)
}

apply { plugin(AndroidIDEAssetsPlugin::class.java) }

buildscript { dependencies { classpath(libs.logging.logback.core) } }

android {
  namespace = BuildConfig.packageName

  defaultConfig {
    applicationId = BuildConfig.packageName
    vectorDrawables.useSupportLibrary = true
  }

      androidResources {
        generateLocaleConfig = true
    }

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
          // gRPC/protobuf modules carry proto descriptors that are compile-time
          // inputs only. Exclude them from the APK to avoid duplicate Java
          // resource merge failures from overlapping proto artifacts.
          "**/*.proto",
      )
  packaging {
    jniLibs {
      // matrix-backtrace 与 zero-regular-preview 都携带 libc++_shared.so，合并时需去重
      pickFirsts += setOf("lib/*/libc++_shared.so")
    }
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
  // resolutionStrategy {
  // force(libs.org.jetbrains.kotlin.jvm)
  // }

  exclude(group = "com.google.googlejavaformat", module = "google-java-format")
  exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
  exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler")
  // exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
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
  implementation(libs.bundles.io.markwon) // io.noties.markwon (still used by DisclaimerFragment onboarding)
  // compose-markdown 内部使用 coil 2.x(与 core/app 自己的 coil 3.x 是分开的版本,
  // 只为 MarkdownImageSources 暴露的 coil.request.ImageRequest / Disposable 类型存在)
  implementation("io.coil-kt:coil:2.7.0")
  // 【图片预览】Coil 3.x —— 用于 SVG / 位图 / GIF 解码 (ImagePreviewFragment).
  // 跟上面 coil 2.x 互不干扰, 各自走自己的 ImageLoader.
  implementation(libs.io.coil.core)
  implementation(libs.io.coil.svg)
  implementation(libs.io.coil.gif)
  // 【图片预览】xml/vectormaster —— Android XML vector drawable 解析器,
  // 让我们从 raw .xml 文件 (在 drawable/ 或 res/ 之外) 也能渲染 vector.
  implementation(projects.xml.vectormaster)

  // === Image Preview Fragment: Coil 3.x ===
  // - coil-core: 核心解码器
  // - coil-svg: SVG 矢量图解码 (AndroidSVG decoder)
  // - coil-gif: GIF 动图解码
  // 三个加起来约 400KB, 比 Glide 小, 支持本地文件 + 多种矢量 / 动图格式.
  // XML 矢量图走 xml/vectormaster (VectorMasterDrawable) 解析, 不经 Coil.
  implementation(libs.io.coil.core)
  implementation(libs.io.coil.svg)
  implementation(libs.io.coil.gif)

  implementation(libs.google.auto.service.annotations)
  implementation(libs.google.gson)
  implementation(libs.google.guava)

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
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.bundles.compose) // androidx compose
  // Compose Navigation — 用于 GitHostScreen 的 NavHost (puppygit Screen 跳转栈)
  implementation(libs.androidx.navigation.compose)
  // Compose Foundation — HorizontalPager / rememberPagerState (GitHostScreen 标签页)
  implementation(libs.androidx.compose.foundation)
  // 磨砂玻璃 (Frosted glass) - 音频/视频预览 fragment 控件
  implementation(libs.haze)
  implementation(libs.haze.blur)
  implementation(libs.haze.blur.materials)

  // Media3 - 音频/视频预览 fragment 播放引擎
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.datasource)
  implementation(libs.androidx.media3.extractor)
  // androidx.compose.runtime.livedata.observeAsState (used by MarkdownPreviewFragment)
  implementation(libs.androidx.compose.runtime.livedata)
  implementation(libs.androidx.core.ktx)

  implementation(libs.common.kotlin)

  // Dependencies in composite build
  implementation(libs.composite.appintro)
  implementation(libs.composite.javapoet)

  // java格式化
  implementation(libs.composite.googleJavaFormat) {
    exclude(group = "com.google.googlejavaformat", module = "google-java-format")
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
  implementation(projects.debugger.breakpointDebugger.library)
  implementation(projects.modules.zeroMcpServer)
  implementation(projects.modules.zeroOnboardingGuide)
  implementation(projects.editor.impl)
  implementation(projects.editor.lexers)
  implementation(projects.editor.editorLsp)
  implementation(projects.java.javacServices)
  implementation(projects.java.lsp)
  implementation(projects.lsp.kotlin)
  implementation(projects.lsp.toml)
  implementation(projects.lsp.clangd)
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
  implementation(projects.xml.vectormaster)
  implementation(projects.tooling.api)
 
  implementation(projects.tooling.pluginConfig)
  implementation(projects.debugger.breakpointDebugger.logwire)
  implementation(projects.utilities.buildInfo)
  implementation(projects.utilities.lookup)
  implementation(projects.utilities.flashbar)
  implementation(projects.utilities.preferences)
  implementation(projects.utilities.templatesApi)
  implementation(projects.utilities.templatesImpl)
  // implementation(projects.utilities.treeview)
  implementation(projects.utilities.fileTree)
  // implementation(projects.utilities.xmlInflater) //归档
  implementation(projects.event.eventbusAndroid)
  implementation(projects.event.eventbusEvents)
  implementation(projects.event.eventbus)
  implementation(projects.event.eventbusAndroid)
  implementation(projects.event.eventbusEvents)
  implementation(projects.debugger.breakpointDebugger.ideDebugger)
  implementation(projects.modules.mtDataFilesProvider)
  implementation(projects.modules.deviceCompat)
  implementation(projects.modules.zeroSymbolInputView)
  implementation(projects.core.git)
  implementation(projects.core.layoutEditor)
  implementation(project(":core:ZeroStudio-Terminal"))
  implementation(projects.core.chatai.app)
  // QuickJS 原生库 —— IDEApplication.onCreate() 调用 QuickJSLoader.init() 加载 .so,
  // chatai 模块 (highlight/common/search) 虽然用 api(libs.quickjs) 声明,
  // 但 core/app 是通过 implementation(projects.core.chatai.app) 引入它们的,
  // implementation 不传递 api 依赖, 所以 core/app 自己也要声明一条。
  implementation(libs.quickjs)
  implementation(projects.modules.zeroRegularPreview)
  implementation(projects.modules.composePreview)
  implementation(projects.modules.colorpicker)
  implementation(projects.modules.universalPreview)
  implementation(projects.modules.webPreview)
  implementation(libs.common.soraLanguageTextmate)

  // Shizuku 客户端 API (子项目 3 断点调试连接层用)
  // 不依赖 Shizuku server / manager, 只用 rikka.shizuku.Shizuku 静态 API
  implementation(projects.modules.shizuku.api)
  // ShizukuProvider: ContentProvider 接收 Shizuku server 下发的 binder,
  //   没有它 Shizuku.pingBinder() 永远返回 false (设备连接管理 BottomSheet 用)
  implementation(projects.modules.shizuku.provider)

  // libsu: Root 通道探测/执行用, 跟 debugger/android-adb-shell 参考工程一致。
  // - Shell.getShell().isRoot 检测 root 可用性
  // - Shell.cmd(...).exec() / .submit() 执行 root 命令
  // - Shell.Builder.setCommands() 支持自定义 su 路径 (DebugConnectionPreferences.rootSuBin)
  implementation(libs.libsu.core)

  // ADB 连接管理模块 (复刻自 debugger/android-adb-shell 参考工程)
  // - adblib: Cameron Gutman 的 ADB 协议 Java 实现 (OTG/USB ADB 用)
  // - libadb: Muntashirakon 的 ADB 库 (WiFi/TLS ADB 用, 含 mDNS + SPAKE2 配对)
  // - fastbootlib: Fastboot 协议 Kotlin 实现
  // - connection: 完整复刻 app/shell+core 源码, 提供 AdbConnectionManager/Repositories
  //   /ViewModels/Services 等真正实现 (Clean Architecture + Hilt + Room)
  // 设备连接管理 BottomSheet 通过这些模块实现 Local+WiFi+OTG+Fastboot 四种连接方式
  implementation(projects.debugger.adbConnection.adblib)
  implementation(projects.debugger.adbConnection.libadb)
  implementation(projects.debugger.adbConnection.fastbootlib)
  implementation(projects.debugger.adbConnection.connection)

  // JmDNS —— WiFi ADB mDNS 服务发现 (_adb-tls-connect._tcp / _adb-tls-pairing._tcp)
  implementation(libs.jmdns)

  // Hilt 依赖注入 (设备连接管理 connection 模块需要 @HiltAndroidApp + @HiltViewModel)
  // 使用 kapt 而非 ksp 处理 Hilt 注解, 规避 Dagger #3965 类加载器冲突
  // (Hilt 插件与 KSP 插件在不同作用域声明导致 classloader 不一致)
  implementation(libs.hilt.android)
  kapt(libs.hilt.android.compiler)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.hilt.work)
  kapt(libs.hilt.compiler)

  // Room (connection 模块的 WifiAdbDeviceDao/BookmarkDao 在 app 进程内运行)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // QR 码生成 (WiFi ADB Pairing 二维码 UI)
  implementation(libs.nayuki.qrcode)
  // 形状指示器组件
  implementation(libs.shapeindicators)
  // Lottie Compose 动画
  implementation(libs.lottie.compose)
  // 加密 SharedPreferences (ADB 配对凭据)
  implementation(libs.androidx.security.crypto)
  // Ktor CIO client
  implementation(libs.io.ktor.client.cio)

  coreLibraryDesugaring(libs.androidx.libDesugaring) // 脱糖
  testImplementation("org.conscrypt:conscrypt-openjdk:2.5.2")
  testImplementation(projects.testing.unitTest)
  androidTestImplementation(projects.testing.androidTest)
  debugImplementation(libs.common.leakcanary)
  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
}
