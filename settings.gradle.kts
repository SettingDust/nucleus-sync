includeBuild("laven-sponge") // For exclude gradle plugin
includeBuild("laven-sponge/laven") // For exclude gradle plugin

pluginManagement {
    repositories {
        mavenCentral()

        maven("https://plugins.gradle.org/m2/")
    }
}

rootProject.name = "nucleus-sync"
include("nucleus-sync-bungee")
include("nucleus-sync-sponge")
