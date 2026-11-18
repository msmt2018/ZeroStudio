// Reuse the canonical Android SDK/NDK versions from composite-builds/build-logic.
subprojects {
    plugins.withId("com.android.library") {
        apply(plugin = "com.itsaky.androidide.android-sdk")
    }
}
