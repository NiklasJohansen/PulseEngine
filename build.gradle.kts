import org.gradle.internal.os.OperatingSystem

val lwjglVersion = "3.2.3"
val kotlinVersion = "1.3.11"

var lwjglNatives = when (OperatingSystem.current()) {
    OperatingSystem.LINUX   -> System.getProperty("os.arch").let {
        if (it.startsWith("arm") || it.startsWith("aarch64"))
            "natives-linux-${if (it.contains("64") || it.startsWith("armv8")) "arm64" else "arm32"}"
        else
            "natives-linux"
    }
    OperatingSystem.MAC_OS  -> "natives-macos"
    OperatingSystem.WINDOWS -> if (System.getProperty("os.arch").contains("64")) "natives-windows" else "natives-windows-x86"
    else -> throw Error("Unrecognized or unsupported Operating system. Please set \"lwjglNatives\" manually")
}

println("OS: $lwjglNatives")

//lwjglNatives = "natives-linux"


plugins {
    kotlin("jvm") version "1.3.11"
    java
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
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.+")
    implementation("org.lwjgl", "lwjgl")
    //implementation("org.lwjgl", "lwjgl-assimp")
    //implementation("org.lwjgl", "lwjgl-bgfx")
    //implementation("org.lwjgl", "lwjgl-cuda")
    //implementation("org.lwjgl", "lwjgl-egl")
    implementation("org.lwjgl", "lwjgl-glfw")
    //implementation("org.lwjgl", "lwjgl-jawt")
    implementation("org.lwjgl", "lwjgl-jemalloc")
    //implementation("org.lwjgl", "lwjgl-libdivide")
    //implementation("org.lwjgl", "lwjgl-llvm")
    //implementation("org.lwjgl", "lwjgl-lmdb")
    //implementation("org.lwjgl", "lwjgl-lz4")
    //implementation("org.lwjgl", "lwjgl-meow")
    //implementation("org.lwjgl", "lwjgl-nanovg")
    //implementation("org.lwjgl", "lwjgl-nfd")
    //implementation("org.lwjgl", "lwjgl-nuklear")
    //implementation("org.lwjgl", "lwjgl-odbc")
    implementation("org.lwjgl", "lwjgl-openal")
    //implementation("org.lwjgl", "lwjgl-opencl")
    implementation("org.lwjgl", "lwjgl-opengl")
    //implementation("org.lwjgl", "lwjgl-opengles")
    //implementation("org.lwjgl", "lwjgl-openvr")
    //implementation("org.lwjgl", "lwjgl-opus")
    //implementation("org.lwjgl", "lwjgl-ovr")
    //implementation("org.lwjgl", "lwjgl-par")
    //implementation("org.lwjgl", "lwjgl-remotery")
    //implementation("org.lwjgl", "lwjgl-rpmalloc")
    //implementation("org.lwjgl", "lwjgl-shaderc")
    //implementation("org.lwjgl", "lwjgl-sse")
    implementation("org.lwjgl", "lwjgl-stb")
    //implementation("org.lwjgl", "lwjgl-tinyexr")
    //implementation("org.lwjgl", "lwjgl-tinyfd")
    //implementation("org.lwjgl", "lwjgl-tootle")
    //implementation("org.lwjgl", "lwjgl-vma")
    //implementation("org.lwjgl", "lwjgl-vulkan")
    //implementation("org.lwjgl", "lwjgl-xxhash")
    //implementation("org.lwjgl", "lwjgl-yoga")
    //implementation("org.lwjgl", "lwjgl-zstd")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-assimp", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-bgfx", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-jemalloc", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-libdivide", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-llvm", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-lmdb", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-lz4", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-meow", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-nanovg", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-nfd", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-nuklear", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-openal", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-opengles", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-openvr", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-opus", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-ovr", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-par", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-remotery", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-rpmalloc", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-shaderc", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-sse", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-stb", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-tinyexr", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-tinyfd", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-tootle", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-vma", classifier = lwjglNatives)
    //if (lwjglNatives == "natives-macos") runtimeOnly("org.lwjgl", "lwjgl-vulkan", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-xxhash", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-yoga", classifier = lwjglNatives)
    //runtimeOnly("org.lwjgl", "lwjgl-zstd", classifier = lwjglNatives)
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "game.example.ConsoleExampleKt"
    }

    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
