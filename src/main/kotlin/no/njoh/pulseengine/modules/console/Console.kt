package no.njoh.pulseengine.modules.console

import no.njoh.pulseengine.PulseEngine
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

interface Console
{
    fun registerCommand(template: String, description: String = "", isAlias: Boolean = false, block: CommandArguments.() -> CommandResult)
    fun run(commandString: String, showCommand: Boolean = true): List<CommandResult>
    fun runScript(filename: String)
    fun log(text: String, type: MessageType = MessageType.INFO)
    fun getHistory(index: Int, type: MessageType): ConsoleEntry?
    fun getHistory(): List<ConsoleEntry>
    fun clearHistory()
    fun getSuggestions(command: String): List<Command>
}

interface ConsoleEngineInterface : Console
{
    fun init(engine: PulseEngine)
}

class ConsoleImpl : ConsoleEngineInterface
{
    private val commandMap = ConcurrentHashMap<String, Command>()
    private val history = mutableListOf<ConsoleEntry>()

    override fun init(engine: PulseEngine)
    {
        // Register console commands and functions marked with @ConsoleTarget
        GlobalScope.launch {
            CommandRegistry.registerEngineCommands(engine)
            ConsoleUtil.registerConsoleFunctions(engine)
        }

        // Help command
        registerCommand(
            template = "help {command:String?}",
            description = "Lists all available commands if no specific command name is given."
        ) {
            val commandName = getOptionalString("command")
            if (commandName != null)
            {
                val command = commandMap[commandName.toLowerCase()]
                    ?: return@registerCommand CommandResult("No command with name: $commandName", MessageType.ERROR)
                CommandResult("${command.template}${if (command.description.isNotEmpty()) " - " else ""}${command.description}")
            }
            else
            {
                val commands = commandMap.values.filter { !it.isAlias }.sortedBy { it.base }
                val aliases = commandMap.values.filter { it.isAlias }.sortedBy { it.base }
                val commandsString = "\n------- Commands -------\n\n" + commands.joinToString("\n\n") { it.template }
                val aliasesString = if (aliases.isNotEmpty())
                    "\n\n------- Aliases -------\n\n" + aliases.joinToString("\n\n") { it.template }
                else ""

                CommandResult(commandsString + aliasesString)
            }
        }
    }

    override fun registerCommand(template: String, description: String, isAlias: Boolean, block: CommandArguments.() -> CommandResult)
    {
        val baseCommand = template.getCleanBaseCommand()
        val arguments = getTemplateArguments(template)

        if (commandMap.containsKey(baseCommand))
            log("Overwriting already existing command with name $baseCommand", MessageType.WARN)

        commandMap[baseCommand] = Command(baseCommand, template, description, arguments, block, isAlias)
    }

    override fun run(commandString: String, showCommand: Boolean): List<CommandResult>
    {
        return commandString
            .splitIgnoreLiterals(";".toRegex())
            .map { it.trimStart().trimEnd() }
            .map { command ->

                // Add command to history
                val commandEntry = ConsoleEntry(command, showCommand, MessageType.COMMAND)
                history.add(commandEntry)

                // Run command
                val result = runCommand(command)

                // Set visibility of command based on result
                commandEntry.visible = showCommand && result.showCommand

                // Add result to history
                if (result.message.isNotEmpty())
                    history.add(ConsoleEntry(result.message, true, result.type))

                return@map result
            }
    }

    override fun runScript(filename: String)
    {
        run("run $filename")
    }

    override fun log(text: String, type: MessageType)
    {
        history.add(ConsoleEntry(text, true, type))
    }

    private fun runCommand(command: String): CommandResult
    {
        if (command.isBlank())
            return CommandResult("")

        // Clean command string
        val commandString = command.trim()

        // Find registered command
        val registeredCommand = commandMap[commandString.getCleanBaseCommand()]
            ?: return CommandResult("$commandString is not a registered command", MessageType.ERROR)

        // Get arguments from command string
        val arguments = parseCommandArguments(commandString, registeredCommand)

        // Validate that all required arguments are present
        registeredCommand.validateArguments(arguments)
            ?.let { return CommandResult(it, MessageType.ERROR) }

        // Run command code with parsed arguments
        return registeredCommand.codeBlock.invoke(arguments)
    }

    override fun getHistory(index: Int, type: MessageType): ConsoleEntry?
    {
        var foundIndex = 0
        for (i in history.lastIndex downTo  0)
        {
            if (history[i].type == type && foundIndex++ == index)
                return history[i]
        }
        return null
    }

    override fun getHistory() = history

    override fun clearHistory() = history.replaceAll { it.copy(visible = false) }

    override fun getSuggestions(command: String): List<Command> =
        commandMap.keys
            .filter { it.startsWith(command) }
            .mapNotNull { commandMap[it] }

    private fun parseCommandArguments(commandString: String, command: Command): CommandArguments
    {
        val argMap = mutableMapOf<String, Any?>()
        val commandWords = commandString.splitIgnoreLiterals("\\s".toRegex())

        // Find verbose arguments
        commandWords
            .filter { !it.isStringLiteral() && it.isVerboseArgument(command) }
            .forEach { word ->
                val (argName, stringValue) = word.splitIgnoreLiterals("=".toRegex())
                val type = command.arguments.first { it.name == argName }.type
                argMap[argName] = stringValue.getTypedValue(type, argName)
            }

        // Find rest of missing arguments among non verbose values
        command.arguments
            .filter { it.index < commandWords.size && !argMap.contains(it.name)}
            .forEach { argTemplate ->
                val word = commandWords[argTemplate.index]
                if (!word.isVerboseArgument(command))
                    argMap[argTemplate.name] = word.getTypedValue(argTemplate.type, argTemplate.name)
            }

        return CommandArguments(argMap)
    }

    private fun getTemplateArguments(commandTemplate: String): List<ArgumentTemplate>
    {
        return commandTemplate
            .split("\\s".toRegex())
            .mapIndexedNotNull { index, templateWord ->
                if (templateWord.isArgumentTemplate())
                {
                    val (name, type) = templateWord
                        .replace("{", "")
                        .replace("}", "")
                        .split(":")
                    val optional = type.endsWith("?")
                    val cleanType = type.replace("?", "")
                    ArgumentTemplate(index, cleanType, name, optional)
                }
                else null
            }
    }

    private fun String.getTypedValue(type: String, argumentName: String): Any
    {
        try
        {
            if (this.isBlank())
                return ArgumentParseError("Argument $argumentName cannot be blank, expecting type: $type")

            val value = this.cleanStringLiteral().trim()

            return when (type.toLowerCase())
            {
                "string" -> value
                "int" -> value.toInt()
                "float" -> value.toFloat()
                "double" -> value.toDouble()
                "long" -> value.toLong()
                "boolean" -> value.toLowerCase() == "true" || value == "1"
                else -> ArgumentParseError("$type not recognised as a Kotlin type")
            }
        }
        catch (e: Exception) { return ArgumentParseError("Argument $this is not of type $type") }
    }

    private fun Command.validateArguments(commandArguments: CommandArguments): String?
    {
        // Check for missing arguments
        this.arguments
            .firstOrNull { !it.optional && !commandArguments.args.containsKey(it.name) }
            ?.let { return "Missing argument ${it.name} in command: ${this.template}" }

        // Check to se if any arguments has parsing errors
        commandArguments.args.values
            .filterIsInstance<ArgumentParseError>()
            .firstOrNull()
            ?.let { return it.message }

        return null
    }

    private fun String.splitIgnoreLiterals(regex: Regex): List<String>
    {
        // Find all string literals and create map with temp strings
        val literalMap= Regex("[^\\s\"]+|\"([^\"]*)\"")
            .findAll(this)
            .map { it.groupValues }
            .flatten()
            .filter { !it.isBlank() && it.startsWith("\"") }
            .mapIndexed { i, text -> "TEMP$i" to text }
            .toMap()

        // Replace literals with temp strings
        var string = this
        literalMap.forEach { (tempValue, literal) ->
            string = string.replace(literal, tempValue)
        }

        // Split string with given regex and for each word replace temp string with correct literal
        return string
            .split(regex)
            .map {
                var word = it
                literalMap.forEach { (tempValue, literal) ->
                    word = word.replace(tempValue, literal)
                }
                word
            }
    }

    private fun String.getCleanBaseCommand(): String =
        this.trim().split("\\s".toRegex()).first().toLowerCase()

    private fun String.isStringLiteral(): Boolean =
        this.length >= 2 && this.startsWith("\"") && this.endsWith("\"")

    private fun String.isVerboseArgument(command: Command): Boolean =
        command.arguments.any { this.startsWith("${it.name}=") }

    private fun String.isArgumentTemplate(): Boolean =
        this.startsWith("{") && this.endsWith("}") && this.split(":").size == 2

    private fun String.cleanStringLiteral(): String =
        if (this.isStringLiteral())
            this.substring(1, this.lastIndex).replace("'","\"")
        else
            this
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
    fun getString(argName: String): String   = args[argName] as String
    fun getInt(argName: String): Int         = args[argName] as Int
    fun getFloat(argName: String): Float     = args[argName] as Float
    fun getDouble(argName: String): Double   = args[argName] as Double
    fun getLong(argName: String): Long       = args[argName] as Long
    fun getBoolean(argName: String): Boolean = args[argName] as Boolean
    fun getOptionalString(argName: String): String?   = args[argName]?.let { return it as String }
    fun getOptionalInt(argName: String): Int?         = args[argName]?.let { return it as Int }
    fun getOptionalFloat(argName: String): Float?     = args[argName]?.let { return it as Float }
    fun getOptionalDouble(argName: String): Double?   = args[argName]?.let { return it as Double }
    fun getOptionalLong(argName: String): Long?       = args[argName]?.let { return it as Long }
    fun getOptionalBoolean(argName: String): Boolean? = args[argName]?.let { return it as Boolean }
}

enum class MessageType
{
    COMMAND, INFO, ERROR, WARN
}
