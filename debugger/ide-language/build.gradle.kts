plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zerostudio.language"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // JavaParser is used directly by JavaParserFacade. Its types are an
    // implementation detail and are not exposed by this module's public API.
    implementation("com.github.javaparser:javaparser-core:3.27.1")

    // SourceLocator and SourceResolver consume the decompiler API to resolve
    // class sources from bytecode. The decompiler module is independent of this
    // language module, so this is a one-way dependency and does not form a cycle.
    implementation(project(":debugger:ide-decompiler"))

    testImplementation(libs.tests.junit)
    testImplementation("org.hamcrest:hamcrest-core:1.3")
}
