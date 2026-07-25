@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  includeBuild("composite-builds/build-logic") { name = "build-logic" }

  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
    mavenLocal()
    maven("https://cache-redirector.jetbrains.com/kotlin.bintray.com/kotlin-plugin")
    maven("https://jitpack.io")
    maven("https://repo1.maven.org/maven2/")
    maven("https://repo.itextsupport.com/android")
    maven(url = "https://repo.gradle.org/gradle/libs-releases/")
    maven { url = uri("${rootProject.projectDir}/gradle/libs") }
  }

  resolutionStrategy {
    eachPlugin {
      if (requested.id.id == "io.objectbox") {
        useModule("io.objectbox:objectbox-gradle-plugin:${requested.version}")
      }
    }
  }
}

dependencyResolutionManagement {
  val dependencySubstitutions =
      mapOf(
          "build-deps" to
              arrayOf(
                  "appintro",
                  "fuzzysearch",
                  "google-java-format",
                  "java-compiler",
                  "javac",
                  "javapoet",
                  "jaxp",
                  "jdk-compiler",
                  "jdk-jdeps",
                  "jdt",
                  "layoutlib-api",
                  "logback-core",
                  "editor",
                  "compose-pullrefresh",
                  
                  "soraLanguageTextmate",
                  // "kotlinc",

              ),
          "build-deps-common" to arrayOf("desugaring-core"),
      )

    for ((build, modules) in dependencySubstitutions) {
    includeBuild("composite-builds/${build}") {
      this.name = build
      dependencySubstitution {
        for (module in modules) {
          substitute(module("com.itsaky.androidide.build:${module}"))
            .using(project(":${module}"))
        }
        
      }
    }
  }

  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
    maven("https://repo1.maven.org/maven2/")
    maven("https://repo.itextsupport.com/android")
    maven(url = "https://repo.gradle.org/gradle/libs-releases/")
    maven(url = "https://www.jetbrains.com/intellij-repository/releases/")
    maven { url = uri("${rootProject.projectDir}/gradle/libs") }
  }
  // versionCatalogs { create("ktlib") { from(files("gradle/kotlin.versions.toml")) } }
}

val skipNyx = System.getenv("ZEROSTUDIO_SKIP_NYX") == "true"

buildscript {
  repositories { mavenCentral() }
  dependencies {
    if (System.getenv("ZEROSTUDIO_SKIP_NYX") != "true") classpath("com.mooltiverse.oss.nyx:gradle:2.5.2")
  }
}

val isGitRepo by lazy {
  // 如果遇到"git",构建报错，就设置git的绝对路径，比如d：/git/git.exe
  cmdOutput("git", "rev-parse", "--is-inside-work-tree").trim() == "true"
}

private fun cmdOutput(vararg args: String): String {
  return ProcessBuilder(*args)
      .directory(File("."))
      .redirectErrorStream(true)
      .start()
      .inputStream
      .bufferedReader()
      .readText()
      .trim()
}

FDroidConfig.load(rootDir)

if (FDroidConfig.hasRead && FDroidConfig.isFDroidBuild) {
  gradle.rootProject {
    val regex = Regex("^v\\d+\\.?\\d+\\.?\\d+-\\w+")

    val simpleVersion =
        regex.find(FDroidConfig.fDroidVersionName!!)?.value
            ?: throw IllegalArgumentException(
                "Invalid version '${FDroidConfig.fDroidVersionName}. Version name must have semantic version format.'"
            )

    project.setProperty("version", simpleVersion)
  }
} else if (isGitRepo && !skipNyx) {
  apply { plugin("com.mooltiverse.oss.nyx") }
}

rootProject.name = "ZeroStudio"

// keep this sorted alphabetically
include(
    ":annotation:annotations",
    ":annotation:processors",
    ":annotation:processors-ksp",
    ":core:actions",
    ":core:app",
    ":core:common",
    ":core:indexing-api",
    ":core:indexing-core",
    ":core:lsp-api",
    ":core:lsp-models",
    ":core:lsp-manager",
    ":core:projects",
    ":core:resources",
    ":core:git",
    ":core:layout-editor",
    ":core:ZeroStudio-Terminal",
    
    ":core:chatai:ai",
    ":core:chatai:app",
    ":core:chatai:common",
    ":core:chatai:document",
    ":core:chatai:highlight",
    ":core:chatai:search",
    ":core:chatai:speech",
    ":core:chatai:web",
    ":core:chatai:workspace",
    ":core:chatai:material3",

    ":debugger:Breakpoint-debugger:library",

    // ADB 连接管理模块 (复刻自 debugger/android-adb-shell 参考工程的 adblib/libadb/fastbootlib)
    // - adblib: Cameron Gutman 的 ADB 协议 Java 实现 (OTG/USB ADB 用)
    // - libadb: Muntashirakon 的 ADB 库 (WiFi/TLS ADB 用, 含 mDNS + SPAKE2 配对)
    // - fastbootlib: Fastboot 协议 Kotlin 实现
    // - connection: 完整复刻 android-adb-shell/app 的 shell+core 源码, 包含
    //   AdbConnectionManager/WifiAdbRepositoryImpl/OtgRepositoryImpl/FastbootRepositoryImpl
    //   /ShellViewModel/WifiAdbViewModel/OtgViewModel/FastbootViewModel 等真正实现
    //   Clean Architecture + Hilt + Room + JmDNS + Shizuku
    // 设备连接管理 BottomSheet 通过这些模块实现 Local+WiFi+OTG+Fastboot 四种连接方式
    ":debugger:adb-connection:adblib",
    ":debugger:adb-connection:libadb",
    ":debugger:adb-connection:fastbootlib",
    ":debugger:adb-connection:connection",
    // settings-dsl 模块 (复刻自 debugger/android-adb-shell 参考工程)
    // 提供 Kotlin DSL 风格的设置页构建器 (switchItem/clickableItem/radioGroupItem/
    // buttonGroupItem/settingsPage 等), 被 connection 模块的 SettingsProvider.kt 引用。
    ":debugger:android-adb-shell:settings-dsl",

    ":editor:api",
    ":editor:impl",
    ":editor:lexers",
    ":editor:treesitter",
    ":editor:editor-lsp",
    
    ":editor:tree-sitter-ndk:android-tree-sitter",
    ":editor:tree-sitter-ndk:annotation-processors",
    ":editor:tree-sitter-ndk:annotations",
    ":editor:tree-sitter-ndk:tree-sitter-jnilibs",
    ":editor:tree-sitter-ndk:toml",
    ":editor:tree-sitter-ndk:cmake",
    ":editor:tree-sitter-ndk:yaml",
    ":editor:tree-sitter-ndk:aidl",
    ":editor:tree-sitter-ndk:cpp",
    ":editor:tree-sitter-ndk:html",

    ":event:eventbus",
    ":event:eventbus-android",
    ":event:eventbus-events",

    ":debugger:ide-decompiler",
    ":debugger:Breakpoint-debugger:ide-debugger",
    ":debugger:Breakpoint-debugger:ide-debugger-host",
    ":debugger:Breakpoint-debugger:ide-log-plugin",
    ":debugger:ide-language",
    ":debugger:Breakpoint-debugger:logwire",
    ":debugger:Breakpoint-debugger:library",

    ":java:javac-services",
    ":java:lsp",
    ":lsp:kotlin",
    ":lsp:toml",
    ":lsp:clangd",
    // ":lsp:smali",
    // ":lsp:groovy",
    ":logging:idestats",
    ":logging:logger",
    ":logging:logsender",
    ":termux:application",
    ":termux:emulator",
    ":termux:shared",
    ":termux:view",
    ":termux:shell",
    ":termux:proot",
    ":testing:androidTest",
    ":testing:benchmarks",
    ":testing:commonTest",
    ":testing:gradleToolingTest",
    ":testing:lspTest",
    ":testing:unitTest",
    
    ":tooling:api",
    ":tooling:build-grpc",
    ":tooling:builder-model-impl",
    ":tooling:events",
    ":tooling:impl",
    ":tooling:model",
    ":tooling:plugin",
    ":tooling:plugin-config",

    ":utilities:build-info",
    ":utilities:flashbar",
    ":utilities:framework-stubs",
    ":utilities:lookup",
    ":utilities:preferences",
    ":utilities:shared",
    ":utilities:templates-api",
    ":utilities:templates-impl",
    ":utilities:treeview",
    ":utilities:FileTree",
    // ":utilities:uidesigner",  //已经完全归档
    // ":utilities:xml-inflater", //归档
    ":xml:aaptcompiler",
    ":xml:dom",
    ":xml:lsp",
    ":xml:resources-api",
    ":xml:utils",
    ":xml:vectormaster",
    ":modules:colorpicker",
    ":modules:compose-preview",
    ":modules:deviceCompat",
  //  ":modules:image-preview",
    ":modules:kotlinc",
    ":modules:mt-data-files-provider",
    ":modules:soraLanguageMonarch",
    ":modules:soraLanguageTreesitter",
    ":modules:soraOnigurumaNative",
    ":modules:thinkmap-treeview",
    ":modules:zero-Symbol-input-view",
    ":modules:zero-mcp-server",
    ":modules:zero-regular-preview",
    ":modules:universal-preview",
    ":modules:web-preview",
    ":modules:zero-onboarding-guide",
    // Shizuku 客户端 API: 子项目 3 断点调试连接层用
    // (api 内部依赖 :modules:shizuku:aidl 和 :modules:shizuku:shared)
    // provider: ShizukuProvider ContentProvider, 接收 Shizuku server 下发的 binder,
    //   没有 provider 注册则 Shizuku.pingBinder() 永远 false (设备连接管理 BottomSheet 用)
    ":modules:shizuku:aidl",
    ":modules:shizuku:shared",
    ":modules:shizuku:api",
    ":modules:shizuku:provider",

)

object FDroidConfig {

  var hasRead: Boolean = false
    private set

  var isFDroidBuild: Boolean = false
    private set

  var fDroidVersionName: String? = null
    private set

  var fDroidVersionCode: Int? = null
    private set

  const val PROP_FDROID_BUILD = "ide.build.fdroid"
  const val PROP_FDROID_BUILD_VERSION = "ide.build.fdroid.version"
  const val PROP_FDROID_BUILD_VERCODE = "ide.build.fdroid.vercode"

  fun load(rootDir: File) {
    val propsFile = File(rootDir, "fdroid.properties")
    if (!propsFile.exists() || !propsFile.isFile) {
      hasRead = true
      isFDroidBuild = false
      return
    }

    val properties = propsFile.let { props ->
      java.util.Properties().also { it.load(props.reader()) }
    }

    hasRead = true
    isFDroidBuild = properties.getProperty(PROP_FDROID_BUILD, null).toBoolean()

    fDroidVersionName = properties.getProperty(PROP_FDROID_BUILD_VERSION, null)
    fDroidVersionCode = properties.getProperty(PROP_FDROID_BUILD_VERCODE, null)?.toInt()
  }
}
