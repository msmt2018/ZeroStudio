import com.google.protobuf.gradle.*

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.google.protobuf")
}

android {
    namespace = "com.itsaky.androidide.lsp.rpc"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    // 引用 TOML 中的 gRPC 和 Protobuf 依赖
    api(libs.grpc.android)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.grpc.kotlin.stub)
    api(libs.google.protobuf.kotlin)
    api(libs.kotlinx.coroutines.core)
    api("com.google.protobuf:protobuf-java-util:${libs.versions.protobufVersion.get()}")
    api(libs.google.gson)
    api(libs.kotlinx.coroutines.core)
    api(libs.common.javax.annotation.api)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobufVersion.get()}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.ioGrpcVersion.get()}"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.ioGrpcStubVersion.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
                id("grpckt")
            }
            it.builtins {
                id("java")
                id("kotlin")
            }
        }
    }
}
