package no.njoh.pulseengine.modules.shared.utils

import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.KClass

object ReflectionUtil
{
    val STRINGS_TO_IGNORE_IN_CLASS_SEARCH = mutableListOf(
        "$", "/kotlin", "/org/joml", "/org/lwjgl", "/jetbrains", "/intellij"
    )

    private val classCache = mutableMapOf<String, Class<*>>()

    fun getFullyQualifiedClassNames(maxSearchDepth: Int = 10): List<String>
    {
        // Get paths from classloader - necessary for when the application is run in IDE
        val paths = (ClassLoader.getSystemClassLoader() as URLClassLoader).urLs
            .mapNotNull { if (it.file.endsWith("/")) Paths.get(it.toURI()) else null }
            .toMutableList()

        // Get paths from inside JAR file - used when application is run from a JAR or imported as a library
        ReflectionUtil::class.java.getResource("/no").toURI()
            ?.takeIf { it.scheme == "jar" }
            ?.let { jarUri ->
                val fileSystem =
                    try { FileSystems.getFileSystem(jarUri) }
                    catch (e: Exception) { FileSystems.newFileSystem(jarUri, emptyMap<String, Any>()) }
                paths.add(fileSystem.getPath("/"))
            }

        return paths.flatMap { findClassNames(it, maxSearchDepth, STRINGS_TO_IGNORE_IN_CLASS_SEARCH ) }
    }

    private fun findClassNames(startPath: Path, maxSearchDepth: Int, ignoreStrings: List<String> ): List<String> =
        Files.walk(startPath, maxSearchDepth)
            .iterator()
            .asSequence()
            .mapNotNull { path ->
                val filePath = path.toString()
                if (!filePath.endsWith(".class") || ignoreStrings.any { filePath.contains(it) }) null
                else filePath
                    .removePrefix("$startPath")
                    .removePrefix("/")
                    .removePrefix("\\")
                    .removeSuffix(".class")
                    .replace("/", ".")
                    .replace("\\", ".")
            }.toList()

    fun List<String>.getClassesFromFullyQualifiedClassNames() : List<Class<*>> =
        this.mapNotNull { className ->
            runCatching { if (className !in classCache) Class.forName(className) else classCache[className]!! }
                .onSuccess { classCache[className] = it }
                .getOrNull()
        }

    fun List<Class<*>>.getClassesWithAnnotation(annotationType: KClass<*>): List<Class<*>> =
        this.filter { c -> c.annotations.any { it.annotationClass == annotationType } }

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> List<Class<*>>.getClassesOfSuperType(superType: KClass<T>): List<Class<out T>> =
        this.filter { superType.java.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers) } as List<Class<out T>>
}