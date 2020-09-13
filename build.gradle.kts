import org.gradle.internal.os.OperatingSystem

group = "no.njoh"
version = "0.3.2"

val lwjglVersion = "3.2.3"
val kotlinVersion = "1.3.72"

var lwjglNatives = when (OperatingSystem.current()) {
    OperatingSystem.WINDOWS -> if (System.getProperty("os.arch").contains("64")) "natives-windows" else "natives-windows-x86"
    OperatingSystem.MAC_OS  -> "natives-macos"
    OperatingSystem.LINUX   -> System.getProperty("os.arch").let {
        if (it.startsWith("arm") || it.startsWith("aarch64"))
            "natives-linux-${if (it.contains("64") || it.startsWith("armv8")) "arm64" else "arm32"}"
        else
            "natives-linux"
    }
    else -> throw Error("Unrecognized operating system")
}

println("OS: $lwjglNatives")

plugins {
    java
    `maven-publish`
    kotlin("jvm") version "1.3.72"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.l33tlabs.twl:pngdecoder:1.0")
    implementation("org.joml:joml:1.9.22")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.4")
    implementation("de.undercouch:bson4jackson:2.9.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.10")
    implementation("org.lwjgl", "lwjgl")
    implementation("org.lwjgl", "lwjgl-glfw")
    implementation("org.lwjgl", "lwjgl-jemalloc")
    implementation("org.lwjgl", "lwjgl-openal")
    implementation("org.lwjgl", "lwjgl-opengl")
    implementation("org.lwjgl", "lwjgl-stb")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-jemalloc", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-openal", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-stb", classifier = lwjglNatives)
}

val sourcesJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    archiveClassifier.set("sources")
    exclude("*.png", "*.jpg", "*.ttf", "*.ogg", "*.txt")
    from(sourceSets.main.get().allSource)
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "testbed.TestbedKt"
    }
    exclude( "**/*.kotlin_metadata", "**/*.kotlin_module", "**/*.kotlin_builtins")
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) } })
}

publishing {
    publications {
        create<MavenPublication>("default") {
            artifact(jar)
            artifact(sourcesJar)
        }
    }
    repositories {
        maven {
            url = uri("$buildDir/repository")
        }
    }
}