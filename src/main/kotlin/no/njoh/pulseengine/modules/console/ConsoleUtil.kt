package no.njoh.pulseengine.modules.console

import no.njoh.pulseengine.PulseEngine
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Collections.emptyMap
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.kotlinFunction


@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConsoleTarget(val description: String = "")

object ConsoleUtil
{
    private const val PACKAGE_NAME = "pulseengine"

    /**
     * Registers top-level functions marked with a [ConsoleTarget] annotation
     */
    fun registerConsoleFunctions(engine: PulseEngine)
    {
        // Don't search for ConsoleTarget annotations in class paths containing the following
        val stringsToIgnore = listOf("$", "/kotlin", "/org/joml", "/org/lwjgl", "/jetbrains", "/intellij")
        val uri = ConsoleUtil::class.java.getResource("/$PACKAGE_NAME")?.toURI()
            ?: return

        val path = if (uri.scheme == "jar")
            FileSystems.newFileSystem(uri, emptyMap<String, Any>()).getPath("/.")
        else
        {
            // If the uri does not point to a JAR, use the code source location
            Paths.get(ConsoleUtil::class.java.protectionDomain.codeSource.location.toURI())
        }

        // Walk the directory/JAR and search for functions marked as console targets
        Files.walk(path, 10)
            .iterator()
            .asSequence()
            .filter {
                val filePath = it.toString()
                filePath.endsWith(".class") && !stringsToIgnore.any { filePath.contains(it) }
            }
            .forEach {
                val fullyQualifiedClassName = it.toString()
                    .removePrefix("$path")
                    .removePrefix("/")
                    .removePrefix("\\")
                    .removeSuffix(".class")
                    .replace("/", ".")
                    .replace("\\", ".")

                registerConsoleFunctions(engine, fullyQualifiedClassName)
            }
    }

    private fun registerConsoleFunctions(engine: PulseEngine, className: String)
    {
        try
        {
            val functions = Class
                .forName(className)
                .methods
                .mapNotNull { it.kotlinFunction }
                .filter { it.annotations.any { it.annotationClass == ConsoleTarget::class} }

            for (func in functions)
            {
                // Functions with parameters missing its name is bound to an instance and not top-level
                if (func.parameters.any { it.name == null})
                {
                    engine.console.log("Cannot register function: ${func.name} in class $className. It is not a top-level function!", MessageType.WARN)
                    continue
                }

                // Get description form annotation and create template form parameters
                val description = func.findAnnotation<ConsoleTarget>()?.description ?: ""
                val template = func.name + " " + func.parameters
                    .joinToString(" ") { "{${it.name}:${it.type.toString().removePrefix("kotlin.")}}" }

                // Register command with template and description
                engine.console.registerCommand(template, description) {
                    val arguments = func.parameters.map { param ->
                        val name = param.name
                            ?: return@registerCommand CommandResult("Failed to find name of parameter", MessageType.ERROR)
                        when (param.type.classifier)
                        {
                            String::class  -> getString(name)
                            Int::class     -> getInt(name)
                            Float::class   -> getFloat(name)
                            Double::class  -> getDouble(name)
                            Long::class    -> getLong(name)
                            Boolean::class -> getBoolean(name)
                            else           -> return@registerCommand CommandResult("Parameter ${name} is not a primitive type", MessageType.ERROR)
                        }
                    }

                    // Call function and creat CommandResult from the function output
                    when (val result = func.call(*arguments.toTypedArray()))
                    {
                        is CommandResult -> result
                        is Unit -> CommandResult("")
                        else -> CommandResult(result?.toString() ?: "")
                    }
                }
            }
        }
        catch (e: UnsupportedClassVersionError) { }
        catch (e: Exception) { }
    }
}