plugins {
    id("com.android.library")
}

android {
    namespace = "com.zerostudio.decompiler"
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
    compileOnly(files("${rootDir}/decompile/cfr-0.152.jar"))
    testImplementation(files("${rootDir}/decompile/cfr-0.152.jar"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest-core:1.3")
}