package no.njoh.pulseengine.util

import java.lang.Exception
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.reflect.KClass

object ReflectionUtil
{
    val DEFAULT_STRINGS_TO_IGNORE_IN_CLASS_SEARCH = mutableListOf(
        "$", "/kotlin", "/org/joml", "/org/lwjgl", "/jetbrains", "/intellij"
    )

    fun getFullyQualifiedClassNames(packageName: String, ignoreStrings: List<String> = emptyList(), maxSearchDepth: Int = 10): List<String>
    {
        val ignore = DEFAULT_STRINGS_TO_IGNORE_IN_CLASS_SEARCH + ignoreStrings
        val uri = ReflectionUtil::class.java.getResource("/$packageName")?.toURI()
            ?: return emptyList()

        val packagePath = if (uri.scheme == "jar")
            FileSystems.newFileSystem(uri, Collections.emptyMap<String, Any>()).getPath("/.")
        else
        {
            // If the uri does not point to a JAR, use the code source location
            Paths.get(ReflectionUtil::class.java.protectionDomain.codeSource.location.toURI())
        }

        // Walk the directory/JAR and search for functions marked as console targets
        return Files.walk(packagePath, maxSearchDepth)
            .iterator()
            .asSequence()
            .filter {
                val filePath = it.toString()
                filePath.endsWith(".class") && !ignore.any { filePath.contains(it) }
            }
            .map { path -> path
                .toString()
                .removePrefix("$packagePath")
                .removePrefix("/")
                .removePrefix("\\")
                .removeSuffix(".class")
                .replace("/", ".")
                .replace("\\", ".")
            }
            .toList()
    }

    fun List<String>.getClassesFromFullyQualifiedClassNames() : List<Class<*>> =
        this.mapNotNull {
            try { Class.forName(it) }
            catch (e: Exception) {
                Logger.error("Failed to create class from: $it")
                null
            }
        }

    fun List<Class<*>>.getClassesWithAnnotation(annotationType: KClass<*>): List<Class<*>> =
        this.filter { c -> c.annotations.any { it.annotationClass == annotationType } }

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> List<Class<*>>.getClassesOfSuperType(superType: KClass<T>): List<Class<out T>> =
        this.filter { superType.java.isAssignableFrom(it) } as List<Class<out T>>
}