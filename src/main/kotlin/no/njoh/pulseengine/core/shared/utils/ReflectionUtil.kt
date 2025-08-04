package no.njoh.pulseengine.core.shared.utils

import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import java.io.File
import java.io.File.pathSeparator
import java.io.File.separatorChar
import java.lang.management.ManagementFactory
import java.lang.reflect.Method
import java.lang.reflect.Modifier.isAbstract
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import kotlin.reflect.KClass
import kotlin.sequences.forEach
import kotlin.use

object ReflectionUtil
{
    val annotationCache = mutableMapOf<String, MutableMap<String, MutableSet<*>>>()

    /**
     * Finds all classes in the specified packages and sub-packages down to the specified depth.
     */
    fun getClassesInPackages(
        vararg packages: String,
        maxSearchDepth: Int = 10,
        includeAbstractClasses: Boolean = false
    ): List<Class<*>> {
        val loader = Thread.currentThread().contextClassLoader
        val classNames = ConcurrentHashMap.newKeySet<String>()

        for (file in ManagementFactory.getRuntimeMXBean().classPath.split(pathSeparator).map(::File))
        {
            if (file.isDirectory)
            {
                for (pkg in packages)
                {
                    val basePath = if (pkg.isEmpty()) file.toPath() else file.toPath().resolve(pkg.replace('.', '/'))
                    if (pkg.isNotEmpty() && !Files.exists(basePath))
                        continue

                    Files.walk(basePath, maxSearchDepth).use { stream ->
                        stream
                            .filter { path -> path.toString().let { it.endsWith(".class") && '$' !in it } }
                            .map { basePath.relativize(it).toString().removeSuffix(".class").replace(separatorChar, '.') }
                            .forEach { classNames += if (pkg.isEmpty()) it else "$pkg.$it" }
                    }
                }
            }
            else if (file.isFile && file.name.endsWith(".jar", true))
            {
                JarFile(file).use { jar ->
                    for (pkg in packages)
                    {
                        val prefix = if (pkg.isEmpty()) "" else pkg.replace('.', '/') + "/"
                        jar.entries().asSequence()
                            .map { it.name }
                            .filter { it.endsWith(".class") && it.startsWith(prefix) && '$' !in it }
                            .map { it.removePrefix(prefix).removeSuffix(".class").replace('/', '.') }
                            .forEach { classNames += if (pkg.isEmpty()) it else "$pkg.$it" }
                    }
                }
            }
        }

        return classNames.mapNotNull { name ->
            runCatching { Class.forName(name, false, loader)}.getOrNull()?.takeIf { includeAbstractClasses || !isAbstract(it.modifiers) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> List<Class<*>>.forEachClassWithSupertype(action: (Class<out T>) -> Unit) =
        this.forEachFast { if (T::class.java.isAssignableFrom(it)) action(it as Class<out T>) }

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