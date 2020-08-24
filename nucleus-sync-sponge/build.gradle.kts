import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("kapt")

    id("com.github.johnrengelman.shadow")
}

val major = 1
val minor = 0
val patch = 0

val mainVersion = arrayOf(major, minor, patch).joinToString(".")

group = "org.spongepowered"
version = {
    var version = mainVersion
    val suffix = mutableListOf("")
    if (System.getenv("BUILD_NUMBER") != null) {
        suffix += System.getenv("BUILD_NUMBER").toString()
    }
    if (System.getenv("GITHUB_REF") == null || System.getenv("GITHUB_REF").endsWith("-dev")) {
        suffix += "unstable"
    }
    version += suffix.joinToString("-")
    version
}()

repositories {
    jcenter()
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
    maven("https://repo.codemc.org/repository/maven-public")
    maven("http://repo.drnaylor.co.uk/artifactory/list/minecraft")
}

dependencies {
    kapt("org.spongepowered:spongeapi:7.2.0")

    implementation("org.spongepowered:spongecommon:1.12.2-7.2.2:dev")

    api("io.github.nucleuspowered:nucleus-api:1.14.7-SNAPSHOT-S7.1")

    shadow("org.bstats:bstats-sponge-lite:1.6")

    val laven = "me.settingdust:laven-sponge:latest"
    shadow(laven) {
        exclude("org.spongepowered")
    }
    api(laven)

    shadow(rootProject)
    api(rootProject)
}

val shadow by configurations.named("shadow")

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    named<ShadowJar>("shadowJar") {
        configurations = listOf(shadow)
        archiveClassifier.set("")
        relocate("com.zaxxer.hikari","$group.runtime.hikari")
    }
    named<Jar>("jar") {
        enabled = false
    }
    named<Task>("build") {
        dependsOn("shadowJar")
    }
}