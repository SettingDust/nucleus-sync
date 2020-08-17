import com.diffplug.gradle.spotless.SpotlessApply
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    val kotlinVersion = "1.4.0"
    kotlin("jvm") version kotlinVersion
    kotlin("kapt") version kotlinVersion

    `maven-publish`

    id("com.github.johnrengelman.shadow") version "6.0.0"
    id("net.kyori.blossom") version "1.1.0"
    id("com.diffplug.spotless") version "5.1.0"
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
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
    maven("https://repo.codemc.org/repository/maven-public")
    maven("http://repo.drnaylor.co.uk/artifactory/list/minecraft")
}

dependencies {
    api(kotlin("stdlib-jdk8"))
    api("io.github.nucleuspowered:nucleus-api:2.0.0-SNAPSHOT")

    val sponge = "org.spongepowered:spongeapi:7.2.0"
    kapt(sponge)
    api(sponge)

    val bstats = "org.bstats:bstats-sponge-lite:1.6"
    shadow(bstats)
    implementation(bstats)

    val laven = "me.settingdust:laven-sponge:latest"
    shadow(laven) {
        exclude("org.jetbrains.kotlin")
    }
    api(laven) {
        exclude("org.jetbrains.kotlin")
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/SettingDust/nucleus-sync")
            credentials {
                username = project.findProperty("gpr.user") as? String ?: System.getenv("GPR_USER")
                password = project.findProperty("gpr.key") as? String ?: System.getenv("GPR_API_KEY")
            }
        }
    }
    publications {
        create<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
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
        relocate("kotlin", "$group.nucleussync.runtime.kotlin")
        relocate("kotlinx", "$group.nucleussync.runtime.kotlinx")
    }
    named<Jar>("jar") {
        enabled = false
    }
    named<Task>("build") {
        dependsOn("shadowJar", withType<SpotlessApply>())
    }
}

blossom {
    replaceToken("@version@", version)
}

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}
