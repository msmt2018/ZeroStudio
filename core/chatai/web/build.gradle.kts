plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.library)
}

val webUiDir = rootProject.layout.projectDirectory.dir("core/chatai/web-ui")
val webStaticResourcesDir = layout.projectDirectory.dir("src/main/resources/static")

val buildWebUi = tasks.register<Exec>("buildWebUi") {
    group = "build"
    description = "Build web-ui and copy its static output into the web module resources."

    workingDir = webUiDir.asFile
    commandLine("pnpm", "run", "build")

    inputs.files(
        webUiDir.file("package.json"),
        webUiDir.file("pnpm-lock.yaml"),
        webUiDir.file("components.json"),
        webUiDir.file("copy.ts"),
        webUiDir.file("react-router.config.ts"),
        webUiDir.file("tsconfig.json"),
        webUiDir.file("vite.config.ts"),
        webUiDir.file("vite-env.d.ts")
    )
    inputs.dir(webUiDir.dir("app"))
    inputs.dir(webUiDir.dir("public"))
    outputs.dir(webStaticResourcesDir)
}

android {
    namespace = "me.rerere.rikkahub.web"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.named("preBuild") {
    dependsOn(buildWebUi)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)

    // ktor server
    implementation(libs.io.ktor.server.default.headers)
    implementation(libs.io.ktor.server.conditional.headers)
    implementation(libs.io.ktor.server.compression)
    implementation(libs.io.ktor.server.cors)
    api(libs.io.ktor.server.auth)
    api(libs.io.ktor.server.auth.jwt)
    api(libs.io.ktor.server.core)
    implementation(libs.io.ktor.server.host.common)
    api(libs.io.ktor.server.content.negotiation)
    api(libs.io.ktor.server.status.pages)
    api(libs.io.ktor.server.sse)
    api(libs.io.ktor.server.cio)

    testImplementation(libs.tests.junit)
    androidTestImplementation(libs.tests.androidx.junit)
    androidTestImplementation(libs.tests.androidx.espresso.core)
}
