/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:Suppress("UnstableApiUsage")

/**
 * -------------------------------------------------------------------------------------------------
 * 文件名：settings.gradle.kts (Root Settings)
 * 用途与功能：
 *   ZeroStudio IDE 根设置脚本。
 *   负责启用类型安全项目访问器、配置全局插件管理源、复合构建（Composite Builds）、
 *   依赖项强制替换（Dependency Substitution）、F-Droid 构建检测以及全项目数百个子模块的注册。
 *   已全面适配 Gradle 9+ 配置缓存规范，使用 ValueSource 安全替代配置阶段的外部进程调用。
 * 工作流程线路图：
 *   1. 启用 TYPESAFE_PROJECT_ACCESSORS 特性预览。
 *   2. pluginManagement -> 配置全局 Gradle 插件仓库及 ObjectBox 插件重定向策略。
 *   3. dependencyResolutionManagement -> 挂载 composite-builds/build-deps 复合构建，集中替换内部依赖。
 *   4. 使用 ValueSource 安全检测 Git 状态，加载 F-Droid 配置或通过 Nyx 动态注入 Git 版本号。
 *   5. 注册全项目所有模块。
 * -------------------------------------------------------------------------------------------------
 * @author android_zero
 */

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  includeBuild("composite-builds/build-logic")

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
              ),
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
}

val skipNyx = System.getenv("ZEROSTUDIO_SKIP_NYX") == "true"

buildscript {
  repositories { 
  mavenCentral() 
  gradlePluginPortal()
  }
  dependencies {
    if (System.getenv("ZEROSTUDIO_SKIP_NYX") != "true") {
      classpath("com.mooltiverse.oss.nyx:com.mooltiverse.oss.nyx.gradle.plugin:3.1.7")
    }
  }
}

/**
 * -------------------------------------------------------------------------------------------------
 * 类名：GitInsideWorkTreeValueSource
 * 用途与功能：
 *   符合 Gradle 9 配置缓存规范的 ValueSource 实现。
 *   替代原先被废弃的、在配置阶段直接通过 ProcessBuilder 启动外部进程的做法，
 *   安全地检测当前目录是否处于 Git 工作树内。
 * -------------------------------------------------------------------------------------------------
 */
abstract class GitInsideWorkTreeValueSource : org.gradle.api.provider.ValueSource<Boolean, org.gradle.api.provider.ValueSourceParameters.None> {
  @get:javax.inject.Inject
  abstract val execOperations: org.gradle.process.ExecOperations

  override fun obtain(): Boolean {
    return try {
      val stdout = java.io.ByteArrayOutputStream()
      execOperations.exec {
        commandLine("git", "rev-parse", "--is-inside-work-tree")
        standardOutput = stdout
      }
      stdout.toString().trim() == "true"
    } catch (e: Exception) {
      false
    }
  }
}

// 采用 Gradle 9 配置缓存安全的惰性 Provider 获取 Git 状态
val isGitRepo = providers.of(GitInsideWorkTreeValueSource::class) {}.get()

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

// 保持按字母顺序排序注册全项目子模块
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
    ":core:projects",
    ":core:resources",
    ":core:git",
    ":core:layout-editor",
    ":core:ZeroStudio-Terminal",
    
    ":core:rikkahub:ai",
    ":core:rikkahub:app",
    ":core:rikkahub:common",
    ":core:rikkahub:document",
    ":core:rikkahub:highlight",
    ":core:rikkahub:search",
    ":core:rikkahub:speech",
    ":core:rikkahub:web",
    ":core:rikkahub:workspace",
    ":core:rikkahub:material3",
    ":core:rikkahub:ui",
    ":core:rikkahub:terminal", 
    ":core:rikkahub:app:baselineprofile",
    ":core:rikkahub:mediagen",
    ":core:rikkahub:oauth",

    ":debugger:Breakpoint-debugger:library",

    // ADB 连接管理模块
    ":debugger:adb-connection:adblib",
    ":debugger:adb-connection:libadb",
    ":debugger:adb-connection:fastbootlib",
    ":debugger:adb-connection:connection",
    ":debugger:adb-connection:settings-dsl",

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

    ":java:javac-services",
    ":java:lsp",
    ":lsp:kotlin",
    ":lsp:toml",
    ":lsp:clangd",
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
    // ":utilities:treeview",
    ":utilities:FileTree",
    
    ":xml:aaptcompiler",
    ":xml:dom",
    ":xml:lsp",
    ":xml:resources-api",
    ":xml:utils",
    ":xml:vectormaster",
    ":modules:colorpicker",
    ":modules:compose-preview",
    ":modules:deviceCompat",
    ":modules:kotlinc",
    ":modules:mt-data-files-provider",
    ":modules:soraLanguageMonarch",
    ":modules:soraOnigurumaNative",
    ":modules:thinkmap-treeview",
    ":modules:zero-Symbol-input-view",
    ":modules:zero-mcp-server",
    ":modules:zero-regular-preview",
    ":modules:universal-preview",
    ":modules:web-preview",
    ":modules:zero-onboarding-guide",
    
    // Shizuku 客户端 API
    ":modules:shizuku:aidl",
    ":modules:shizuku:shared",
    ":modules:shizuku:api",
    ":modules:shizuku:provider",
)

/**
 * -------------------------------------------------------------------------------------------------
 * 类名：FDroidConfig
 * 用途与功能：
 *   负责在构建初始化阶段解析 rootDir 下的 `fdroid.properties` 配置文件，
 *   以确定当前是否为 F-Droid 离线/打包渠道构建，并安全注入版本信息。
 * -------------------------------------------------------------------------------------------------
 */
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