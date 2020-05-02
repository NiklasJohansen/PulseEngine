package engine.modules.console

import engine.GameEngine
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

@ConsoleTarget("Prints the given text as an info message")
fun info(text: String) = CommandResult(text, MessageType.INFO, false)

@ConsoleTarget("Prints the given text as a warning message")
fun warning(text: String) = CommandResult(text, MessageType.WARN, false)

@ConsoleTarget("Prints the given text as an error message")
fun error(text: String) = CommandResult(text, MessageType.ERROR, false)

object CommandRegistry
{
    fun registerEngineCommands(engine: GameEngine)
    {
        engine.console.registerCommand(
            "set {target:String} {value:String}",
            "Sets the value of a given property. Format of target is: module.(sub-module).property"
        ) {
            val target = getString("target")
            val value = getString("value")
            val path = target.split(".")

            if (path.size < 2)
                return@registerCommand CommandResult("Target is not on format module.(sub-module).property", MessageType.ERROR)

            val fieldName = path.last()
            var moduleInstance: Any = engine

            path
                .dropLast(1)
                .forEach { moduleName ->
                    val moduleProperty = moduleInstance::class.declaredMemberProperties.find { it.name == moduleName }
                        ?: return@registerCommand CommandResult("Module $moduleName was not found in ${moduleInstance::class.simpleName}", MessageType.ERROR)

                    moduleInstance = moduleProperty.getter.call(moduleInstance)
                        ?: return@registerCommand CommandResult("Failed to get instance of module $moduleName", MessageType.ERROR)
                }

            val field = moduleInstance::class.memberProperties.find { it.name == fieldName }
                ?: return@registerCommand CommandResult("Field $fieldName was not found in module ${moduleInstance::class.simpleName}", MessageType.ERROR)

            field.annotations
                .plus(moduleInstance::class.annotations)
                .filterIsInstance<ConsoleTarget>()
                .ifEmpty { return@registerCommand CommandResult("Field ${field.name} or class ${moduleInstance::class.simpleName} is not marked as a @ConsoleTarget", MessageType.ERROR) }

            if (field !is KMutableProperty<*>)
                return@registerCommand CommandResult("Field ${field.name} is not mutable", MessageType.ERROR)

            val type = field.javaField?.type
            val typedValue = try
            {
                when (type)
                {
                    String::class.java  -> value
                    Int::class.java     -> value.toInt()
                    Float::class.java   -> value.toFloat()
                    Double::class.java  -> value.toDouble()
                    Long::class.java    -> value.toLong()
                    Boolean::class.java -> value.toBoolean()
                    else                -> return@registerCommand CommandResult("Field has type $type which cannot be set by command", MessageType.ERROR)
                }
            }
            catch (e: Exception) { return@registerCommand CommandResult("Failed to parse value $value into required type: $type", MessageType.ERROR) }

            try { field.setter.call(moduleInstance, typedValue) }
            catch (e: Exception) { return@registerCommand CommandResult("Field $fieldName is private and cannot be set from console", MessageType.ERROR) }

            return@registerCommand CommandResult("Field ${field.name} was set to $value")
        }
    }
}