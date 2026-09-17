import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins{
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.kotlin.kapt") version "2.4.10"
    id("me.champeau.jmh") version "0.7.3"
    id("com.gradleup.shadow") version "9.5.1"
}

val platforms = listOf(
    "natives-linux",
    "natives-linux-arm64",
    "natives-macos",
    "natives-macos-arm64",
    "natives-windows",
    "natives-windows-arm64"
)

repositories {
    maven {
        url = uri("https://repo.repsy.io/mvn/njoh/public")
    }
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // LWJGL
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.6"))
    listOf(
        "lwjgl",        // Core LWJGL library
        "lwjgl-glfw",   // GLFW for window management
        "lwjgl-opengl", // OpenGL for graphics
        "lwjgl-openal", // OpenAL for audio
        "lwjgl-stb",    // STB for image loading
        "lwjgl-nfd",    // Native File Dialog for file selection
        "lwjgl-assimp"  // Assimp for 3D model loading
    ).forEach { module ->
        implementation("org.lwjgl:$module")
        platforms.forEach { runtimeOnly("org.lwjgl", module, classifier = it) }
    }

    // 3D Physics
    implementation("no.njoh:box3d-java:0.2.0")
    platforms.forEach { runtimeOnly("no.njoh:box3d-java-$it:0.2.0") }

    // Data structures
    implementation("org.joml:joml:1.10.8")
    implementation("it.unimi.dsi:fastutil:8.5.18")

    // Data serialization / deserialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
    implementation("de.undercouch:bson4jackson:2.15.1")
    implementation("com.esotericsoftware:kryo:5.6.2")
    implementation("org.objenesis:objenesis:3.4")

    // Other
    compileOnly("org.jspecify:jspecify:1.0.0")

    // Java Microbenchmark Harness
    jmh("org.openjdk.jmh:jmh-core:1.37")
    kaptJmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

val lwjglJvmArgs = listOf(
    "--enable-native-access=ALL-UNNAMED",
    "--sun-misc-unsafe-memory-access=allow" // Temporary until LWJGL 3.3.6
)

tasks.withType<JavaExec>().configureEach {
    jvmArgs(lwjglJvmArgs)
}

tasks.named<Jar>("jar") { enabled = false }

tasks.named<ShadowJar>("shadowJar") {
    // Makes it the main artifact name
    archiveClassifier.set("")

    // Merge service registrations and Kotlin metadata, keeps duplicate inputs so Shadow can combine them.
    mergeServiceFiles()
    filesMatching(listOf("META-INF/services/**", "META-INF/*.kotlin_module")) { duplicatesStrategy = DuplicatesStrategy.INCLUDE }

    // fastutil is large, keep only the parts used in the production code  
    minimize { include(dependency("it.unimi.dsi:fastutil:.*")) }
    sourceSetsClassesDirs.setFrom(sourceSets["main"].output.classesDirs.filter { it.isDirectory })

    // Exclude testbed in published lib. Comment this line out when running JAR locally.
    exclude("testbed/**")
    mainClass.set(providers.gradleProperty("mainClass"))

    manifest {
        attributes["Enable-Native-Access"] = "ALL-UNNAMED"
    }
}

tasks.named("assemble") { dependsOn("shadowJar") }

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(kotlin.sourceSets["main"].kotlin)
    exclude("testbed/**")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs = listOf("-Xno-param-assertions", "-Xno-call-assertions")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.named<ShadowJar>("shadowJar"))
            artifact(tasks.named<Jar>("sourcesJar"))
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