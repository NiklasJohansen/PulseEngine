plugins {
    `maven-publish`
    kotlin("jvm") version "2.1.10"
    kotlin("kapt") version "2.1.10"
    id("me.champeau.jmh") version "0.7.3"
}

val version: String by project
val group: String by project
val artifact: String by project
val mainClass: String by project

enum class Platform(val classifier: String) {
    LINUX("natives-linux"),
    LINUX_ARM64("natives-linux-arm64"),
    LINUX_ARM32("natives-linux-arm32"),
    MACOS("natives-macos"),
    MACOS_ARM64("natives-macos-arm64"),
    WINDOWS("natives-windows"),
    WINDOWS_X86("natives-windows-x86"),
    WINDOWS_ARM64("natives-windows-arm64");
}

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // LWJGL
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.6"))
    implementation("org.lwjgl", "lwjgl")
    implementation("org.lwjgl", "lwjgl-glfw")
    implementation("org.lwjgl", "lwjgl-jemalloc")
    implementation("org.lwjgl", "lwjgl-openal")
    implementation("org.lwjgl", "lwjgl-opengl")
    implementation("org.lwjgl", "lwjgl-stb")
    implementation("org.lwjgl", "lwjgl-nfd")
    Platform.values().forEach { platform ->
        runtimeOnly("org.lwjgl", "lwjgl", classifier = platform.classifier)
        runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = platform.classifier)
        runtimeOnly("org.lwjgl", "lwjgl-jemalloc", classifier = platform.classifier)
        runtimeOnly("org.lwjgl", "lwjgl-openal", classifier = platform.classifier)
        runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = platform.classifier)
        runtimeOnly("org.lwjgl", "lwjgl-stb", classifier = platform.classifier)
        runtimeOnly("org.lwjgl", "lwjgl-nfd", classifier = platform.classifier)
    }

    // Other
    implementation("org.joml:joml:1.10.8")
    implementation("net.sf.trove4j:trove4j:3.0.3")
    implementation("de.undercouch:bson4jackson:2.15.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")

    // Java Microbenchmark Harness
    kapt("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    implementation("org.openjdk.jmh:jmh-core:1.37")
    implementation("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

val sourcesJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    archiveClassifier.set("sources")
    exclude("*.png", "*.jpg", "*.ttf", "*.ogg", "*.txt")
    from(sourceSets.main.get().allSource)
}

val jar by tasks.getting(Jar::class) {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest {
        attributes["Main-Class"] = mainClass
    }
    // exclude("**/*.kotlin_module", "**/*.kotlin_metadata", "testbed/**")
    exclude("**/*.kotlin_module", "**/*.kotlin_metadata")
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) } })
}

kotlin {
    jvmToolchain(23)
    compilerOptions {
        freeCompilerArgs = listOf("-Xno-param-assertions", "-Xno-call-assertions")
    }
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
            url = uri("https://repo.repsy.io/mvn/njoh/public")
            credentials {
                username = System.getenv("REPSY_USERNAME")
                password = System.getenv("REPSY_PASSWORD")
            }
        }

        // Local repo
        // maven {
        //     url = uri("$buildDir/repository")
        // }
    }
}

jmh { profilers.set(listOf("gc")) }