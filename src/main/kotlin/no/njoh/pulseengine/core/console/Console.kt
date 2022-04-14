package no.njoh.pulseengine.core.console

import no.njoh.pulseengine.core.PulseEngine

interface Console
{
    fun log(text: String, type: MessageType = MessageType.INFO)
    fun run(commandString: String, showCommand: Boolean = true): List<CommandResult>
    fun runLater(commandString: String, showCommand: Boolean = true)
    fun runScript(filename: String)
    fun registerCommand(template: String, description: String = "", isAlias: Boolean = false, block: CommandArguments.() -> CommandResult)
    fun getHistory(index: Int, type: MessageType): ConsoleEntry?
    fun getHistory(): List<ConsoleEntry>
    fun clearHistory()
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
    fun getOptionalString(argName: String): String?     = args[argName]?.let { return it as String }
    fun getOptionalInt(argName: String): Int?           = args[argName]?.let { return it as Int }
    fun getOptionalFloat(argName: String): Float?       = args[argName]?.let { return it as Float }
    fun getOptionalDouble(argName: String): Double?     = args[argName]?.let { return it as Double }
    fun getOptionalLong(argName: String): Long?         = args[argName]?.let { return it as Long }
    fun getOptionalBoolean(argName: String): Boolean?   = args[argName]?.let { return it as Boolean }
}

enum class MessageType
{
    COMMAND, INFO, ERROR, WARN
}