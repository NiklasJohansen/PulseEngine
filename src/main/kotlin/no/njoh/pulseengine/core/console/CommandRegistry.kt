package no.njoh.pulseengine.core.console

import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.window.ScreenMode.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.FileWatcher
import java.io.File

object CommandRegistry
{
    private const val SCRIPT_EXTENSION_TYPE = ".ps"
    private val keyBindingSubscriptions = mutableMapOf<String, Subscription>()

    fun registerEngineCommands(engine: PulseEngineInternal)
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
                url?.readText()?.lines()?.forEachFast { engine.console.run(it, showCommands) }
            }

            CommandResult("")
        }

        ///////////////////////////////////////////// ALIAS COMMAND /////////////////////////////////////////////

        engine.console.registerCommand(
            template = "alias {name:String} {command:String} {description:String?}",
            description = "Creates a new command with the given alias"
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

        ///////////////////////////////////////////// BIND KEY COMMAND /////////////////////////////////////////////

        engine.console.registerCommand(
            template = "bind {key:String} {command:String}",
            description = "Binds a command to be run when one or more keys are pressed"
        ) {
            val command = getString("command")
            val keyString = getString("key")
            val keys = keyString
                .split("+")
                .map {
                    try { Key.valueOf(it.trim().uppercase()) }
                    catch (e: Exception) { return@registerCommand CommandResult("No key with name $it. Did you mean any of these: ${Key.entries.filter { k -> k.toString().contains(it) }}", MessageType.ERROR) }
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
            template = "unbind {key:String}",
            description = "Unbinds a command from a key"
        ) {
            val keyString = getString("key")
            val keys = keyString
                .split("+")
                .map {
                    try { Key.valueOf(it.trim().uppercase()) }
                    catch (e :Exception) { return@registerCommand CommandResult("No key with name $it. Did you mean any of these: " +
                        "${Key.entries.filter { k -> k.toString().contains(it) }}", MessageType.ERROR) }
                }

            keyBindingSubscriptions.remove(keys.toString())?.let {
                it.unsubscribe()
                return@registerCommand CommandResult("Key ${keys.joinToString("+")} was unbound", showCommand = false)
            }

            CommandResult("No command bound for key: ${keys.joinToString("+")}", MessageType.ERROR)
        }

        ///////////////////////////////////////////// RELOAD SYSTEM AND ENTITY TYPES COMMAND /////////////////////////////////////////////

        engine.console.registerCommand(
            template = "reloadEntityAndSystemTypes",
            description = "Reloads all entity types and systems"
        ) {
            engine.scene.registerSystemsAndEntityClasses()
            CommandResult("Reloaded entity types", showCommand = false)
        }

        ///////////////////////////////////////////// RELOAD ASSETS /////////////////////////////////////////////

        engine.console.registerCommand(
            template = "reloadAsset {filePath:String}",
            description = "Reloads a specific asset"
        ) {
            val filePath = getString("filePath")
            engine.asset.reloadAssetFromPath(filePath)
            CommandResult("Reloading asset: filePath", showCommand = false)
        }

        ///////////////////////////////////////////// WATCH FILE CHANGES /////////////////////////////////////////////

        engine.console.registerCommand(
            template = "watchFileChange {path:String} {triggerCommand:String} {intervalMillis:Int?} {fileTypes:String?} {maxDepth:Int?}",
            description = "Watches for file changes in a directory and triggers a command with the path of the changed file as argument"
        ) {
            val path = getString("path")
            val command = getString("triggerCommand")
            val interval = getOptionalInt("intervalMillis") ?: 5_000
            val fileTypes = getOptionalString("fileTypes")?.split(";") ?: emptyList()
            val maxDepth = getOptionalInt("maxDepth") ?: 5
            FileWatcher.setOnFileChanged(path, fileTypes, maxDepth, interval) { filePath ->
                engine.console.runLater("$command \"$filePath\"", showCommand = true)
            }
            CommandResult("Watching for file changes every $interval seconds in $path", showCommand = false)
        }
    }
}