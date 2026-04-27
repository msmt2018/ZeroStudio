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

buildscript {
  repositories { mavenCentral() }
  dependencies { classpath("com.mooltiverse.oss.nyx:gradle:2.5.2") }
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
} else if (isGitRepo) {
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
    ":core:zero-mcp-server",
    // ":core:chatai:app",
    // ":core:chatai:highlight",
    // ":core:chatai:ai",
    // ":core:chatai:search",
    // ":core:chatai:tts",
    // ":core:chatai:common",
    // ":core:chatai:document",
    // ":core:chatai:web",

    ":editor:api",
    ":editor:impl",
    ":editor:lexers",
    ":editor:treesitter",
    ":editor:editor-lsp",
    
    ":editor:tree-sitter-ndk:annotation-processors",
    ":editor:tree-sitter-ndk:tree-sitter-jnilibs",
    ":editor:tree-sitter-ndk:toml",
    ":editor:tree-sitter-ndk:cmake",
    ":editor:tree-sitter-ndk:yaml",
    ":editor:tree-sitter-ndk:aidl",
    ":editor:tree-sitter-ndk:cpp",

    ":event:eventbus",
    ":event:eventbus-android",
    ":event:eventbus-events",
    ":java:javac-services",
    ":java:lsp",
    ":lsp:kotlin",
    ":lsp:toml",
    // ":lsp:clangd",
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
    // ":utilities:uidesigner",  //已经完全归档
    // ":utilities:xml-inflater", //归档
    ":xml:aaptcompiler",
    ":xml:dom",
    ":xml:lsp",
    ":xml:resources-api",
    ":xml:utils",
    ":xml:vectormaster",
    ":modules:mt-data-files-provider",
    ":modules:soraLanguageMonarch",
    ":modules:soraLanguageTreesitter",
    ":modules:soraOnigurumaNative",
    ":modules:deviceCompat",
    ":modules:zero-Symbol-input-view",
    ":modules:zero-regular-preview",
    ":modules:thinkmap-treeview",
    ":modules:compose-preview",
    // ":modules:colorpicker",
    ":modules:kotlinc",

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
