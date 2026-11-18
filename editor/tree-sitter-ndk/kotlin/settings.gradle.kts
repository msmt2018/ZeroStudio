rootProject.name = "ktreesitter"

pluginManagement {
    includeBuild("ktreesitter-plugin")
    includeBuild("../../../composite-builds/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":ktreesitter")

file("languages").listFiles { file -> file.isDirectory }?.forEach {
    include(":languages:${it.name}")
}
