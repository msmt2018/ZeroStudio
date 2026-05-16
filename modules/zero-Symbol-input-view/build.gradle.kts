plugins {
    id("com.android.library")
}

android {
    namespace = "android.zero.studio.widget.editor.symbolinput"
}

dependencies {
    implementation(libs.google.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.gson)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.viewpager)
    implementation(libs.androidx.recyclerview)

    implementation(libs.common.editor)
    api(libs.androidx.annotation)

}