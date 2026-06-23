plugins { id("com.android.library") }
apply { plugin("com.itsaky.androidide.plugins.NoDesugarPlugin") }

description =
    "ide-language: ZeroStudio's compiler-grade language analysis library. " +
        "Provides lexer / parser / symbol / cross-file-index services for " +
        "Java, Kotlin, C and C++ source code, plus a Go-to-Definition engine " +
        "that the code editor and the debugger share. Replaces the deleted " +
        "language-lexer module with a single, well-scoped library."

android {
  namespace = "com.zerostudio.ide.language"

  defaultConfig {
    minSdk = 21
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions { jvmTarget = "17" }

  buildFeatures {
    aidl = false
    viewBinding = false
  }

  testOptions { unitTests.isReturnDefaultValues = true }

  // 暴露 Tree-Sitter 等原生 ABI 给运行时。
  sourceSets {
    named("main") {
      jniLibs.srcDirs("src/main/jniLibs")
    }
  }
}

dependencies {
  // 基础 / Kotlin 反射
  compileOnly(libs.androidx.core.ktx)
  compileOnly(libs.androidx.annotation)
  compileOnly(libs.common.kotlin)
  compileOnly(libs.kotlin.stdlib)
  compileOnly(libs.kotlin.reflect)

  // 编译器
  implementation(libs.common.javaparser)             // Java 解析 + 符号求解
  implementation(libs.common.asm)                    // .class 反查
  implementation(libs.org.jetbrains.kotlin.compiler) // Kotlin 解析（IDE 侧非嵌入模式）

  // 跨文件索引
  implementation(libs.common.io)
  implementation(libs.common.lang3)
  implementation(libs.guava.listenablefuture)

  // 事件 / 协程
  compileOnly(libs.kotlinx.coroutines.core)
  compileOnly(libs.kotlinx.coroutines.android)

  // 日志
  implementation(libs.logging.logback.core)

  // Tree-Sitter（C / C++ 词法 + 语法）
  // JNI 由 editor/treesitter 模块提供；本库通过 ABI 调用。
  // 见 :editor:tree-sitter-ndk:tree-sitter-jnilibs
  compileOnly(project(":editor:tree-sitter-ndk:tree-sitter-jnilibs"))

  // 单测
  testImplementation(libs.tests.junit)
}
