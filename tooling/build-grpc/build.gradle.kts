@Suppress("JavaPluginLanguageLevel")
plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  alias(libs.plugins.protobuf)
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

kotlin {
  jvmToolchain(17)
}

sourceSets {
  main {
    proto {
      srcDir("src/main/proto")

      // The build/bazel protos are kept under this source directory only as imports for
      // build_service.proto. Generate their Java/Kotlin classes from :tooling:reapi-proto
      // instead, otherwise Android receives the same build.bazel.* classes from both
      // modules and D8 fails while merging dex archives.
      include("binary_protocol.proto")
      include("build_service.proto")
    }
  }
}

dependencies {
  api(projects.tooling.api)
  api(projects.tooling.reapiProto)

  api(libs.google.protobuf.java)
  api(libs.google.protobuf.kotlin)
  api(libs.grpc.protobuf)
  api(libs.grpc.stub)
  api(libs.grpc.kotlin.stub)

  implementation(libs.grpc.netty.shaded)
  implementation(libs.kotlinx.coroutines.core)

  protobuf("com.google.api.grpc:proto-google-common-protos:2.62.0")

  compileOnly("javax.annotation:javax.annotation-api:1.3.2")
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:${libs.versions.protobufVersion.get()}"
  }
  plugins {
    create("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.ioGrpcVersion.get()}"
    }
    create("grpckt") {
      artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.ioGrpcStubVersion.get()}:jdk8@jar"
    }
  }

  generateProtoTasks {
    all().forEach { task ->
      task.plugins {
        create("grpc")
        create("grpckt")
      }
      task.builtins {
        create("kotlin")
      }
    }
  }
}


tasks.processResources {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
