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
    api(project(":debugger:ide-decompiler"))

    // JavaParserFacade directly instantiates JavaParser at runtime.
    // Keep the parser on the runtime classpath and align it with the
    // repository's JavaParser 3.27.1 version.
    implementation("com.github.javaparser:javaparser-core:3.27.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest-core:1.3")
}
