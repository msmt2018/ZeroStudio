plugins { 
    `kotlin-dsl` apply false
  alias(libs.plugins.kotlin.jvm) apply false 
id("org.jetbrains.kotlin.plugin.sam.with.receiver") version "2.4.10" apply false
}

subprojects {
  repositories {
        maven { url = java.net.URI("https://maven.aliyun.com/repository/public") }
        maven { url = java.net.URI("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
  }
  plugins.withId("java-library") {
    extensions.getByType(JavaPluginExtension::class.java).apply {
      sourceCompatibility = JavaVersion.VERSION_17
      targetCompatibility = JavaVersion.VERSION_17
    }
  }
}