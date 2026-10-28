/**
 * @author android_zero
 *
 * 变更说明：
 * 1. 采用 AGP 8.x + Kotlin 2.x 标准构建配置。
 * 2. 指定 CMake 3.31.1，开启 C11/C++17 并启用 -O3、-flto 优化。
 * 3. 引入 Kotlin Coroutines 库以支持 StateFlow/SharedFlow 响应式事件分发。
 */

plugins {
    id("com.android.library")
}

android {
    namespace = "com.zerostudio.ide"
    compileSdk = 37
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = 26

        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }

        externalNativeBuild {
            cmake {
                version = "4.1.2"
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release"
                )
                cFlags(
                    "-O3",
                    "-std=c11",
                    "-D_GNU_SOURCE",
                    "-fvisibility=hidden",
                    "-flto"
                )
                cppFlags(
                    "-O3",
                    "-std=c++17",
                    "-fvisibility=hidden",
                    "-fvisibility-inlines-hidden",
                    "-fno-rtti",
                    "-fno-exceptions",
                    "-flto"
                )
            }
        }
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

   tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
    }
}


    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    sourceSets {
        getByName("main") {
assets.directories.add("src/main/assets")

        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
}