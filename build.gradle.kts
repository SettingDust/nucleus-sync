import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    val kotlinVersion = "1.4.0"
    kotlin("jvm") version kotlinVersion

    `maven-publish`

    id("com.github.johnrengelman.shadow") version "6.0.0"
    id("net.kyori.blossom") version "1.1.0"
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
    maven("http://repo.drnaylor.co.uk/artifactory/list/minecraft")
}

dependencies {
    val kotlinVersion = "1.4.0"
    val kotlinReflect = "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion"
    api(kotlinReflect)
    shadow(kotlinReflect)

    val exposeVersion = "0.26.2"
    api("org.jetbrains.exposed", "exposed-core", exposeVersion) {
        exclude("org.jetbrains.kotlin")
    }
    api("org.jetbrains.exposed", "exposed-dao", exposeVersion) {
        exclude("org.jetbrains.kotlin")
    }
    api("org.jetbrains.exposed", "exposed-jdbc", exposeVersion)
    api("org.jetbrains.exposed", "exposed-jodatime", exposeVersion)
    shadow("org.jetbrains.exposed", "exposed-core", exposeVersion)
    shadow("org.jetbrains.exposed", "exposed-dao", exposeVersion)
    shadow("org.jetbrains.exposed", "exposed-jdbc", exposeVersion)
    shadow("org.jetbrains.exposed", "exposed-jodatime", exposeVersion)

    val laven = "me.settingdust:laven:latest"
    shadow(laven) {
        exclude("org.spongepowered")
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
    replaceToken("@version@", version)
}