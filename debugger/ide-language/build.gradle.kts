plugins {
    id("com.android.library")
}

android {
    namespace = "com.zerostudio.language"
    compileSdk = 37
    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api(project(":debugger:ide-decompiler"))
    compileOnly("com.github.javaparser:javaparser-core:3.17.0")
    testImplementation("com.github.javaparser:javaparser-core:3.17.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest-core:1.3")
}