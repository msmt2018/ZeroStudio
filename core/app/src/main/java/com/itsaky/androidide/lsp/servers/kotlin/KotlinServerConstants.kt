package com.itsaky.androidide.lsp.servers.kotlin

/**
 * Kotlin Language Server 所需的文件清单常量。
 * 用于安装完整性校验。
 *
 * @author android_zero
 */
object KotlinServerConstants {
    const val DOWNLOAD_URL = "https://github.com/msmt2018/SDK-tool-for-Android-platform/releases/download/kotlin-lsp/kotlinLanguageServices.zip"
    
    // 启动脚本名称
    const val LAUNCHER_SCRIPT_NAME = "kotlin-language-server"

    // 核心依赖 JAR 列表 (必须全部存在)
    val REQUIRED_LIB_JARS = listOf(
        "annotations-24.0.0.jar",
        "checker-qual-3.43.0.jar",
        "error_prone_annotations-2.36.0.jar",
        "exposed-core-0.37.3.jar",
        "exposed-dao-0.37.3.jar",
        "exposed-jdbc-0.37.3.jar",
        "failureaccess-1.0.2.jar",
        "google-java-format-1.8.jar",
        "gson-2.10.1.jar",
        "guava-33.4.0-jre.jar",
        "h2-1.4.200.jar",
        "j2objc-annotations-3.0.0.jar",
        "java-decompiler-engine-243.22562.218.jar",
        "jcommander-1.82.jar",
        "jline-3.24.1.jar",
        "jna-4.2.2.jar",
        "jsr305-3.0.2.jar",
        "kotlin-compiler-2.1.0.jar",
        "kotlin-reflect-2.1.0.jar",
        "kotlin-sam-with-receiver-compiler-plugin-2.1.0.jar",
        "kotlin-script-runtime-2.1.0.jar",
        "kotlin-scripting-common-2.1.0.jar",
        "kotlin-scripting-compiler-2.1.0.jar",
        "kotlin-scripting-compiler-impl-2.1.0.jar",
        "kotlin-scripting-jvm-2.1.0.jar",
        "kotlin-scripting-jvm-host-unshaded-2.1.0.jar",
        "kotlin-stdlib-2.1.0.jar",
        "kotlin-stdlib-jdk7-2.1.0.jar",
        "kotlin-stdlib-jdk8-2.1.0.jar",
        "kotlinx-coroutines-core-jvm-1.6.4.jar",
        "ktfmt-b5d31d1.jar",
        "listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar",
        "org.eclipse.lsp4j.jsonrpc-0.21.2.jar",
        "org.eclipse.lsp4j-0.21.2.jar",
        "protobuf-java-3.18.2.jar",
        "protobuf-java-util-3.18.2.jar",
        "server-1.6.5-bazel.jar",
        "shared-1.6.5-bazel.jar",
        "slf4j-api-1.7.25.jar",
        "sqlite-jdbc-3.41.2.1.jar",
        "trove4j-1.0.20200330.jar"
    )
}