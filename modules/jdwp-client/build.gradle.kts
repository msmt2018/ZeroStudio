plugins {
  kotlin("jvm")
  `java-library`
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
  implementation(kotlin("stdlib"))
}
