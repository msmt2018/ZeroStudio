plugins {
    alias(libs.plugins.android.library)
  id("com.itsaky.androidide.build")
}

android {
  namespace = "com.github.appintro"

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }

  tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
    this.compilerOptions {
        this.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}
}


kotlin { jvmToolchain(17) }

dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.viewpager2)
  implementation(libs.androidx.fragment.ktx)
}
