plugins {
    id("com.android.library")
}

android {
    namespace = "com.itsaky.androidide.treesitter.aidl"
    ndkVersion = "27.1.12297006"

    defaultConfig {
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"))
        }
        
        externalNativeBuild {
            cmake {
                arguments("-DCMAKE_CXX_FLAGS=-std=c++17")
            }
        }
        
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.androidide.ts)
    implementation("com.itsaky.androidide.treesitter:annotations:4.3.2")
    annotationProcessor(projects.editor.treeSitterNdk.annotationProcessors)
}
