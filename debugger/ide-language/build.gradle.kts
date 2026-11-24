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

    // The language engine does not depend on the decompiler. The decompiler
    // is a consumer of language analysis, so this dependency must not point
    // in the opposite direction (which would create an architectural cycle).

    testImplementation(libs.tests.junit)
    testImplementation("org.hamcrest:hamcrest-core:1.3")
}
