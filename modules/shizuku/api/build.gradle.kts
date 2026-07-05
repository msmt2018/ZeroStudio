plugins { id("com.android.library") }

android {
  namespace = "rikka.shizuku.api"
  buildFeatures { buildConfig = false }
}

dependencies {
  api(projects.utilities.buildInfo)
  api(projects.modules.shizuku.aidl)
  api(projects.modules.shizuku.shared)
  implementation(libs.androidx.annotation)
}
