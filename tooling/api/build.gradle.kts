/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

@Suppress("JavaPluginLanguageLevel")
plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  // id("com.google.protobuf")
}

dependencies {

  // implementation("com.google.protobuf:protobuf-kotlin:4.33.2")
  // implementation("io.grpc:grpc-protobuf:1.78.0")
  // implementation("io.grpc:grpc-stub:1.78.0")
  // implementation("io.grpc:grpc-kotlin-stub:1.5.0")
  compileOnly("javax.annotation:javax.annotation-api:1.3.2")

  api(projects.logging.logger)
  api(projects.tooling.events)
  api(projects.tooling.model)
  api(projects.utilities.buildInfo)
  api(projects.utilities.shared)

  api(libs.google.gson)
  api(libs.common.ch.epfl.scala.bsp4j)
  api(libs.common.lsp4j.jsonrpc)
  implementation(libs.common.jkotlin)
}

// protobuf {
    // protoc {
        // artifact = "com.google.protobuf:protoc:3.25.3"
    // }
    // plugins {
        // create("grpc") {
            // artifact = "io.grpc:protoc-gen-grpc-java:1.62.0"
        // }
        // create("grpckt") {
            // artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        // }
    // }
    // generateProtoTasks {
        // all().forEach {
            // it.plugins {
                // create("grpc")
                // create("grpckt")
            // }
            // it.builtins {
                // create("kotlin")
            // }
        // }
    // }
// }
