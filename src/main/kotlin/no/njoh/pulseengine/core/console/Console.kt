package no.njoh.pulseengine.core.console

import no.njoh.pulseengine.core.PulseEngine

interface Console
{
    /**
     * Logs a [String] message with the given [MessageType] to the console.
     */
    fun log(text: String, type: MessageType = MessageType.INFO)

    /**
     * Runs the given command and returns the result as a list of [CommandResult].
     * Will add the command and the result to the command history if [showCommand] is true.
     */
    fun run(commandString: String, showCommand: Boolean = true): List<CommandResult>

    /**
     * Runs the given command on the next game tick.
     * Will add the command and the result to the command history if [showCommand] is true.
     */
    fun runLater(commandString: String, showCommand: Boolean = true)

    /**
     * Runs the commands in the given script file.
     */
    fun runScript(filename: String)

    /**
     * Registers a new command with the given [template] and [description].
     * @param template The command template. Example: "bind {key:String} {command:String}"
     * @param description A description of the command
     * @param isAlias If true, the command will not be shown in the help list
     * @param block The code block that will be executed when the command is run
     */
    fun registerCommand(template: String, description: String = "", isAlias: Boolean = false, block: CommandArguments.() -> CommandResult)

    /**
     * Returns a specific entry of the given [type] from the command history based on an [index].
     * Index 0 is the most recent entry.
     */
    fun getHistory(index: Int, type: MessageType): ConsoleEntry?

    /**
     * Returns the command history as a list of [ConsoleEntry].
     */
    fun getHistory(): List<ConsoleEntry>

    /**
     * Clears the whole console history.
     */
    fun clearHistory()

    /**
     * Returns a list of suggestions based on the given command string.
     */
    fun getSuggestions(command: String): List<Command>
}

interface ConsoleInternal : Console
{
    fun init(engine: PulseEngine)
    fun update()
}

data class Command(
    val base: String,
    val template: String,
    val description: String,
    val arguments: List<ArgumentTemplate>,
    val codeBlock: (CommandArguments) -> CommandResult,
    val isAlias: Boolean
)

data class ArgumentTemplate(
    val index: Int,
    val type: String,
    val name: String,
    val optional: Boolean
)

data class CommandResult(
    val message: String,
    val type: MessageType = MessageType.INFO,
    val showCommand: Boolean = true
)

data class ConsoleEntry(
    val message: String,
    var visible: Boolean = true,
    val type: MessageType = MessageType.INFO
)

data class ArgumentParseError(
    val message: String
)

data class CommandArguments(
    val args: Map<String, Any?>
) {
    fun getString(argName: String): String              = args[argName] as String
    fun getInt(argName: String): Int                    = args[argName] as Int
    fun getFloat(argName: String): Float                = args[argName] as Float
    fun getDouble(argName: String): Double              = args[argName] as Double
    fun getLong(argName: String): Long                  = args[argName] as Long
    fun getBoolean(argName: String): Boolean            = args[argName] as Boolean
    fun getOptionalString(argName: String): String?     = args[argName] as? String?
    fun getOptionalInt(argName: String): Int?           = args[argName] as? Int?
    fun getOptionalFloat(argName: String): Float?       = args[argName] as? Float?
    fun getOptionalDouble(argName: String): Double?     = args[argName] as? Double?
    fun getOptionalLong(argName: String): Long?         = args[argName] as? Long?
    fun getOptionalBoolean(argName: String): Boolean?   = args[argName] as? Boolean?
}

enum class MessageType
{
    COMMAND, INFO, ERROR, WARN
}