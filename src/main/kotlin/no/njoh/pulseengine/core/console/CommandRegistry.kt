package no.njoh.pulseengine.core.console

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.window.ScreenMode.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.njoh.pulseengine.core.graphics.GraphicsInternal
import no.njoh.pulseengine.core.scene.SceneManagerInternal
import no.njoh.pulseengine.core.shared.utils.FileWatcher
import no.njoh.pulseengine.core.shared.utils.Logger
import java.io.File
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

object CommandRegistry
{
    private const val SCRIPT_EXTENSION_TYPE = ".ps"
    private val keyBindingSubscriptions = mutableMapOf<String, Subscription>()

    fun registerEngineCommands(engine: PulseEngine)
    {
        ///////////////////////////////////////////// EXIT COMMAND /////////////////////////////////////////////

        engine.console.registerCommand(
            template = "exit",
            description = "Shuts down the application"
        ) {
            engine.window.close()
            CommandResult("Exiting")
        }

        ///////////////////////////////////////////// ASYNC COMMAND /////////////////////////////////////////////

        engine.console.registerCommand(
            template = "async {command:String} {delay:Float?}",
            description = "Runs the command in a background thread"
        ) {
            val delay = getOptionalFloat("delay") ?: 0f
            GlobalScope.launch {
                delay((delay*1000f).toLong())
                engine.console.run(getString("command"))
            }
            CommandResult("", showCommand = true)
        }

        ///////////////////////////////////////////// HISTORY COMMAND /////////////////////////////////////////////

        engine.console.registerCommand(
            template = "history",
            description = "Lists all the previously run commands"
        ) {
            CommandResult("\n------- Command History -------\n" +
                engine.console.getHistory()
                    .filter { it.type == MessageType.COMMAND }
                    .mapIndexed { i, entry -> "$i ${entry.message}" }
                    .joinToString("\n"))
        }

        ///////////////////////////////////////////// CLEAR COMMAND /////////////////////////////////////////////

        engine.console.registerCommand(
            template = "clear",
            description = "Clears the console history"
        ) {
            engine.console.clearHistory()
            CommandResult("", showCommand = false)
        }

        ///////////////////////////////////////////// FULLSCREEN COMMAND /////////////////////////////////////////////

        engine.console.registerCommand(
            template = "toggleFullscreen",
            description = "Toggles fullscreen "
        ) {
            engine.window.updateScreenMode(if (engine.window.screenMode == WINDOWED) FULLSCREEN else WINDOWED)
            CommandResult("Screen mode set to: ${engine.window.screenMode}", showCommand = true)
        }

        ///////////////////////////////////////////// RUN SCRIPT COMMAND /////////////////////////////////////////////

        engine.console.registerCommand(
            template = "run {scriptPath:String} {showCmd:Boolean?}",
            description = "Runs all commands in a script file"
        ) {
            val scriptPath = "/${getString("scriptPath")}".replaceFirst("//", "/")
            if (!scriptPath.endsWith(SCRIPT_EXTENSION_TYPE))
                return@registerCommand CommandResult("$scriptPath is not a PulseEngine script ($SCRIPT_EXTENSION_TYPE)", MessageType.ERROR)

            var url = CommandRegistry::class.java.getResource(scriptPath)
            if (url == null)
            {
                val file = File(".$scriptPath")
                if (!file.exists() || !file.isFile)
                    return@registerCommand CommandResult("Failed to locate $scriptPath", MessageType.ERROR)
                else
                    url = file.toURI().toURL()
            }

            val showCommands = getOptionalBoolean("showCmd") ?: true
            GlobalScope.launch {
                url.readText().lines().forEach { engine.console.run(it, showCommands) }
            }

            CommandResult("")
        }

        ///////////////////////////////////////////// ALIAS COMMAND /////////////////////////////////////////////

        engine.console.registerCommand(
            "alias {name:String} {command:String} {description:String?}",
            "Creates a new command with the given alias"
        ) {
            val name = getString("name")
            val command = getString("command")
            val description = "Alias for ($command). ".plus(getOptionalString("description") ?: "")

            if (command.substringBefore(" ") == name)
                return@registerCommand CommandResult("Recursive commands are not supported", MessageType.ERROR)

            if (engine.console.getSuggestions(name).any { it.base == name && !it.isAlias })
                return@registerCommand CommandResult("$name is already a registered command", MessageType.ERROR)

            engine.console.registerCommand(name, description, true) {
                engine.console.run(command, showCommand = false)
                CommandResult("", showCommand = true)
            }
            CommandResult("$name was registered")
        }

        ///////////////////////////////////////////// SET COMMAND /////////////////////////////////////////////

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

            if (field !is KMutableProperty<*>)
                return@registerCommand CommandResult("Field ${field.name} is not mutable", MessageType.ERROR)

            val type = field.javaField?.type
            val typedValue: Any = try
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

        ///////////////////////////////////////////// BIND KEY COMMAND /////////////////////////////////////////////

        engine.console.registerCommand(
            "bind {key:String} {command:String}"
        ) {
            val command = getString("command")
            val keyString = getString("key")
            val keys = keyString
                .split("+")
                .map {
                    try { Key.valueOf(it.trim().uppercase()) }
                    catch (e: Exception) { return@registerCommand CommandResult("No key with name $it. Did you mean any of these: ${Key.values().filter { k -> k.toString().contains(it) }}", MessageType.ERROR) }
                }

            val subscription = engine.input.setOnKeyPressed()
            {
                if (it != keys.last()) return@setOnKeyPressed

                for (i in 0 until keys.size - 1)
                {
                    if (!engine.input.isPressed(keys[i])) return@setOnKeyPressed
                }

                engine.console.runLater(command, showCommand = true)
            }

            // Unsubscribe previous and add new unsub callback
            keyBindingSubscriptions[keys.toString()]?.unsubscribe()
            keyBindingSubscriptions[keys.toString()] = subscription

            CommandResult("Command bound to ${keys.joinToString("+")}", showCommand = false)
        }

        ///////////////////////////////////////////// UNBIND KEY COMMAND /////////////////////////////////////////////

        engine.console.registerCommand(
            "unbind {key:String}"
        ) {
            val keyString = getString("key")
            val keys = keyString
                .split("+")
                .map {
                    try { Key.valueOf(it.trim().uppercase()) }
                    catch (e :Exception) { return@registerCommand CommandResult("No key with name $it. Did you mean any of these: " +
                        "${Key.values().filter { k -> k.toString().contains(it) }}", MessageType.ERROR) }
                }

            keyBindingSubscriptions.remove(keys.toString())?.let {
                it.unsubscribe()
                return@registerCommand CommandResult("Key ${keys.joinToString("+")} was unbound", showCommand = false)
            }

            CommandResult("No command bound for key: ${keys.joinToString("+")}", MessageType.ERROR)
        }

        ///////////////////////////////////////////// RELOAD SYSTEM AND ENTITY TYPES COMMAND /////////////////////////////////////////////

        engine.console.registerCommand(
            "reloadEntityAndSystemTypes"
        ) {
            (engine.scene as? SceneManagerInternal)?.registerSystemsAndEntityClasses()
            CommandResult("Reloaded entity types", showCommand = false)
        }

        ///////////////////////////////////////////// RELOAD SHADERS /////////////////////////////////////////////

        engine.console.registerCommand(
            "reloadAllShaders"
        ) {
            Logger.debug("\nReloading all shaders...")
            (engine.gfx as? GraphicsInternal)?.reloadAllShaders()
            CommandResult("Reloading all shaders...", showCommand = false)
        }

        engine.console.registerCommand(
            "reloadShader {fileName:String}"
        ) {
            val fileName = getString("fileName")
            (engine.gfx as? GraphicsInternal)?.reloadShader(fileName)
            CommandResult("Reloading shader: $fileName", showCommand = false)
        }

        ///////////////////////////////////////////// WATCH FILE CHANGES /////////////////////////////////////////////

        engine.console.registerCommand(
            "watchFileChange {path:String} {triggerCommand:String} {interval:Int?} {fileTypes:String?} {maxDepth:Int?}"
        ) {
            val path = getString("path")
            val command = getString("triggerCommand")
            val interval = getOptionalInt("interval") ?: 10
            val fileTypes = getOptionalString("fileTypes")?.split(";") ?: emptyList()
            val maxDepth = getOptionalInt("maxDepth") ?: 5
            FileWatcher.setOnFileChanged(path, fileTypes, maxDepth, interval) { fileName ->
                engine.console.runLater("$command \"$fileName\"", showCommand = true)
            }
            CommandResult("Watching for file changes every $interval seconds in $path", showCommand = false)
        }
    }
}