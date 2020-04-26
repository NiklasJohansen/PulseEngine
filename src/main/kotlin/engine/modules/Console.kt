package engine.modules

import engine.GameEngine

interface ConsoleInterface
{
    fun registerCommand(template: String, description: String = "", block: (CommandArguments) -> CommandResult)
    fun run(command: String): CommandResult
}

interface ConsoleEngineInterface : ConsoleInterface
{
    fun init(engine: GameEngine)
}

class Console : ConsoleEngineInterface
{
    private val commandMap = mutableMapOf<String, Command>()

    override fun init(engine: GameEngine)
    {
        registerCommand(
            "help {command:String?}",
            "Lists all available commands if name no specific command is given."
        ) {
            val command = commandMap[it.getOptionalString("command") ?: ""]
            if (command != null)
                CommandResult("${command.template}${if(command.description.isNotEmpty())" - " else ""}${command.description}")
            else
                CommandResult(commandMap.values.joinToString("") { "${it.template}\n"})
        }
    }

    override fun registerCommand(template: String, description: String, block: (CommandArguments) -> CommandResult)
    {
        val baseCommand = template.getCleanBaseCommand()
        val arguments = getTemplateArguments(template)

        if(commandMap.containsKey(baseCommand))
            println("Overwriting already existing command with name $baseCommand")

        commandMap[baseCommand] = Command(template, description, arguments, block)
    }

    override fun run(command: String): CommandResult
    {
        if(command.isBlank())
            return CommandResult("")

        // Clean command string and find registered command code
        val commandString = command.trim()
        val baseCommand = commandString.getCleanBaseCommand()
        val registeredCommand = commandMap[baseCommand]
            ?: return CommandResult("$commandString is not a registered command", MessageType.ERROR)

        // Get arguments from command string
        val arguments = parseCommandArguments(commandString, registeredCommand)

        // Validate that all required arguments are present
        registeredCommand.validateArguments(arguments)
            ?.let { return CommandResult(it, MessageType.ERROR) }

        // Run command code with parsed arguments
        return registeredCommand.codeBlock.invoke(arguments)
    }

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
                if(!word.isVerboseArgument(command))
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

            return when(type)
            {
                "String" -> value
                "Int" -> value.toInt()
                "Float" -> value.toFloat()
                "Double" -> value.toDouble()
                "Long" -> value.toLong()
                "Boolean" -> value.toLowerCase() == "true" || value == "1"
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
        val literalMap= Regex("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'")
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
            this.substring(1, this.lastIndex)
        else
            this
}

data class Command(
    val template: String,
    val description: String,
    val arguments: List<ArgumentTemplate>,
    val codeBlock: (CommandArguments) -> CommandResult
)

data class ArgumentTemplate(
    val index: Int,
    val type: String,
    val name: String,
    val optional: Boolean
)

data class CommandResult(
    val message: String,
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
    INFO, ERROR, WARN
}
