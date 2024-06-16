package no.njoh.pulseengine.core.shared.utils

import java.io.File
import java.lang.management.ManagementFactory
import java.lang.reflect.Method
import java.lang.reflect.Modifier
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

    val classCache = mutableMapOf<String, Class<*>>()
    val annotationCache = mutableMapOf<String, MutableMap<String, MutableSet<*>>>()

    fun getFullyQualifiedClassNames(maxSearchDepth: Int = 10): List<String>
    {
        // Get paths from classpath - necessary for when the application is run in IDE
        val paths = ManagementFactory.getRuntimeMXBean().classPath
            .split(File.pathSeparator)
            .mapNotNull { if (!it.endsWith(".jar")) Paths.get(it) else null }
            .toMutableList()

        // Get paths from inside JAR file - used when application is run from a JAR or imported as a library
        ReflectionUtil::class.java.getResource("/no")?.toURI()
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

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> List<Class<*>>.getClassesOfSuperType(superType: KClass<T>): List<Class<out T>> =
        this.filter { superType.java.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers) } as List<Class<out T>>

    /**
     * Find the first annotation on the named property of type [T].
     */
    inline fun <reified T> KClass<*>.findPropertyAnnotation(propertyName: String): T? =
        findPropertyAnnotations<T>(propertyName).firstOrNull()

    /**
     * Find all annotations on the named property of type [T].
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> KClass<*>.findPropertyAnnotations(propertyName: String): Set<T>
    {
        // Return cached annotations if available
        val classPropKey = this.simpleName!! + propertyName
        val annotationName = T::class.simpleName!!
        annotationCache[classPropKey]?.get(annotationName)?.let { return it as Set<T> }

        // Search for annotations on both class and interface functions
        val functions = this.java.getAllFunctions()
        val functionName = "get" + propertyName.capitalize()
        val foundAnnotations = functions
            .filter { it.name.startsWith(functionName) && it.name.getOrNull(functionName.length)?.isLetter() != true }
            .flatMap { f -> (f.annotations + f.annotations.flatMap { it.annotationClass.annotations }).filterIsInstance<T>() }
            .toMutableSet()

        // Add annotations to cache and return
        val annotationTypes = annotationCache.getOrPut(classPropKey) { mutableMapOf() }
        return annotationTypes.getOrPut(annotationName) { foundAnnotations } as Set<T>
    }

    /**
     * Recursively collects functions on current class, parent class and interfaces.
     */
    fun Class<*>.getAllFunctions(): List<Method> =
        declaredMethods.toList() +
        (superclass?.getAllFunctions()?.toList() ?: emptyList()) +
        interfaces.flatMap { it.getAllFunctions() }
}