plugins {
    kotlin("jvm")

    id("com.github.johnrengelman.shadow")
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
    maven("https://oss.sonatype.org/content/repositories/snapshots")
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

    api("net.md-5:bungeecord-api:1.12-SNAPSHOT")

    shadow("com.h2database:h2:1.4.200")
    shadow("org.mariadb.jdbc:mariadb-java-client:2.6.2")
    shadow("com.zaxxer:HikariCP:3.4.5")

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
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        configurations = listOf(shadow)
        archiveClassifier.set("")
    }
    named<Jar>("jar") {
        enabled = false
    }
    named<Task>("build") {
        dependsOn("shadowJar")
    }
}