includeBuild("laven-sponge") // For exclude gradle plugin
includeBuild("laven-sponge/laven") // For exclude gradle plugin

pluginManagement {
    repositories {
        maven("https://dl.bintray.com/kotlin/kotlin-eap")

        mavenCentral()

        maven("https://plugins.gradle.org/m2/")
    }
}

rootProject.name = "nucleus-sync"
