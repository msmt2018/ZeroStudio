plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zerostudio.decompiler"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // CFR is shipped inside the workspace and is included as a JAR
    // dependency on the classpath; we never expose it to consumers
    // (the Decompiler API shields the caller from CFR's internals).
    compileOnly(files("${rootDir}/decompile/cfr-0.152.jar"))
    testImplementation(files("${rootDir}/decompile/cfr-0.152.jar"))

    testImplementation("junit:junit:4.13.2")
}
