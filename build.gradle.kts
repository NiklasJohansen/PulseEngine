import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins{
    `maven-publish`
    kotlin("jvm") version "2.2.0"
    kotlin("kapt") version "2.2.0"
    id("me.champeau.jmh") version "0.7.3"
    id("com.gradleup.shadow") version "8.3.8"
}

val mainClass: String by project

enum class Platform(val classifier: String) {
    LINUX("natives-linux"),
    LINUX_ARM64("natives-linux-arm64"),
    MACOS("natives-macos"),
    MACOS_ARM64("natives-macos-arm64"),
    WINDOWS("natives-windows"),
    WINDOWS_ARM64("natives-windows-arm64");
}

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // LWJGL
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.6"))
    listOf(
        "lwjgl",        // Core LWJGL library
        "lwjgl-glfw",   // GLFW for window management
        "lwjgl-opengl", // OpenGL bindings
        "lwjgl-openal", // OpenAL for audio
        "lwjgl-stb",    // STB for image loading
        "lwjgl-nfd"     // Native File Dialog for file selection
    ).forEach { module ->
        implementation("org.lwjgl", module)
        Platform.values().forEach { platform -> runtimeOnly("org.lwjgl", module, classifier = platform.classifier) }
    }

    // Other
    implementation("org.joml:joml:1.10.8")
    implementation("net.sf.trove4j:trove4j:3.0.3")
    implementation("de.undercouch:bson4jackson:2.15.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")

    // Java Microbenchmark Harness
    jmh("org.openjdk.jmh:jmh-core:1.37")
    kaptJmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

val sourcesJar by tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(kotlin.sourceSets["main"].kotlin)
}

tasks.named<Jar>("jar") { enabled = false }
tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("") // Makes it the main artifact name
    mergeServiceFiles()
    manifest { attributes["Main-Class"] = mainClass }
}
tasks.named("assemble") { dependsOn("shadowJar") }

kotlin {
    jvmToolchain(23)
    compilerOptions {
        freeCompilerArgs = listOf("-Xno-param-assertions", "-Xno-call-assertions")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.named<ShadowJar>("shadowJar"))
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