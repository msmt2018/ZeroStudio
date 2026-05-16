plugins {
  id("com.android.library")
}

android { namespace = "com.termux.shell" }

dependencies {
  implementation(projects.core.common)
  implementation(projects.core.resources)
  implementation(projects.termux.shared)
  implementation(projects.termux.emulator)
}
