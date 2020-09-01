import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    val kotlinVersion = "1.4.0"
    kotlin("jvm") version kotlinVersion
    kotlin("kapt") version kotlinVersion

    `maven-publish`

    id("com.github.johnrengelman.shadow") version "6.0.0"
    id("net.kyori.blossom") version "1.1.0"
}

val major = 1
val minor = 0
val patch = 0

val mainVersion = arrayOf(major, minor, patch).joinToString(".")

group = "me.settingdust"
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

    api("io.github.nucleuspowered:nucleus-api:1.14.7-SNAPSHOT-S7.1")

    shadow("org.bstats:bstats-sponge-lite:1.6")

    val laven = "me.settingdust:laven-sponge:latest"
    shadow(laven) {
        exclude("org.spongepowered")
        exclude("org.jetbrains", "annotations")
        exclude("org.intellij.lang", "annotations")
    }
    api(laven)
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

blossom {
    replaceToken("@version@", version, "src/main/kotlin/me/settingdust/nucleussync/NucleusSync.kt")
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
        relocate("kotlin", "${project.group}.runtime.kotlin")
        relocate("org.bstats", "${project.group}.runtime.bstats")
        exclude("META-INF/**")
    }
    named<Jar>("jar") {
        enabled = false
    }
    named<Task>("build") {
        dependsOn("shadowJar")
    }
}