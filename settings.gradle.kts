@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  includeBuild("composite-builds/build-logic") { name = "build-logic" }

  repositories {
    google()
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
    gradlePluginPortal()
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
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
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
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
    mavenCentral()
  }
  dependencies {
    if (System.getenv("ZEROSTUDIO_SKIP_NYX") != "true") classpath("com.mooltiverse.oss.nyx:gradle:2.5.2")
  }
}

val isGitRepo by lazy {
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
    ":utilities:treeview",
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
    ":modules:soraLanguageTreesitter",
    ":modules:soraOnigurumaNative",
    ":modules:thinkmap-treeview",
    ":modules:zero-Symbol-input-view",
    ":modules:zero-mcp-server",
    ":modules:zero-regular-preview",
    ":modules:universal-preview",
    ":modules:web-preview",
    ":modules:zero-onboarding-guide",
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
