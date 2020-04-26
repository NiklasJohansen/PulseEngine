package engine.apps

import engine.GameEngine
import engine.data.Font
import engine.data.Key
import engine.modules.MessageType
import java.lang.Float.max
import java.lang.Integer.max
import java.lang.Math.min

class ConsoleGUI : EngineApp
{
    private var active: Boolean = false
    private var inputText = StringBuilder()
    private val commandLog = mutableListOf<ConsoleEntry>()

    private var inputCursor = 0
    private var historyCursor = -1
    private var suggestionCursor = -1
    private var suggestionBaseText = ""

    override fun init(engine: GameEngine)
    {
        engine.asset.loadFont("/clacon.ttf", "cli_font", floatArrayOf(FONT_SIZE))
    }

    override fun update(engine: GameEngine)
    {
        // Open/close terminal
        if (engine.input.wasClicked(Key.F1))
            active = !active

        // Add new text to text box
        val newText = engine.input.textInput
        if (newText.isNotEmpty())
        {
            inputText.insert(inputCursor, newText)
            inputCursor += newText.length
            suggestionCursor = -1
        }

        // Remove text with backspace
        if(inputCursor > 0 && engine.input.wasClicked(Key.BACKSPACE))
        {
            if(engine.input.isPressed(Key.LEFT_CONTROL))
            {
                val words = inputText.split("\\s".toRegex())
                val text = inputText.substring(0, inputText.length - words.last().length).trimEnd()
                inputText.set(text)
                inputCursor = inputText.length
                suggestionCursor = -1
            }
            else
            {
                val remainingText = inputText.removeRange(IntRange(--inputCursor, inputCursor))
                inputText.set(remainingText)
                suggestionCursor = -1
            }
        }

        // Remove text with delete key
        if (inputCursor < inputText.length && engine.input.wasClicked(Key.DELETE))
        {
            val remainingText = inputText.removeRange(IntRange(inputCursor, inputCursor))
            inputText.set(remainingText)
            suggestionCursor = -1
        }

        // Navigate left in text
        if (engine.input.wasClicked(Key.LEFT))
            inputCursor = max(0, inputCursor - 1)

        // Navigate right in text
        if (engine.input.wasClicked(Key.RIGHT))
            inputCursor = min(inputText.length, inputCursor + 1)

        // Move history cursor up by one
        if (engine.input.wasClicked(Key.UP))
        {
            engine.console.getHistory(historyCursor + 1)?.let {
                inputText.set(it)
                inputCursor = inputText.length
                historyCursor++
            }
        }

        // Move history cursor down by one
        if (engine.input.wasClicked(Key.DOWN))
        {
            engine.console.getHistory(historyCursor - 1)?.let {
                inputText.set(it)
                inputCursor = inputText.length
                historyCursor--
            }
        }

        // Command suggestions
        if (engine.input.wasClicked(Key.TAB))
        {
            if(suggestionCursor == -1)
                suggestionBaseText = inputText.toString()

            val suggestions = engine.console.getSuggestions(suggestionBaseText)
            if(suggestions.isNotEmpty())
            {
                suggestionCursor = (suggestionCursor + 1) % suggestions.size
                inputText.set(suggestions[suggestionCursor].base)
                inputCursor = inputText.length
            }
        }

        // Run command
        if (engine.input.wasClicked(Key.ENTER))
        {
            val commandString = inputText.toString()
            val result = engine.console.run(commandString)

            commandLog.add(ConsoleEntry("> $commandString", MessageType.INFO))
            if (result.message.isNotBlank())
            {
                commandLog.add(ConsoleEntry(result.message, result.type))
                commandLog.add(ConsoleEntry("", MessageType.INFO))
            }

            inputText.clear()
            inputCursor = 0
            historyCursor = -1
            suggestionCursor = -1
        }
    }

    override fun render(engine: GameEngine)
    {
        if(!active)
            return

        val cliFont = engine.asset.get<Font>("cli_font")
        val height = engine.window.height.toFloat()
        val width = engine.window.width * 0.3f
        val availableWidth = width - TEXT_PADDING_X - INPUT_BOX_PADDING
        val charsPerLine = getNumberOfChars(availableWidth)
        val cursorCar = if (System.currentTimeMillis() % 1000 > 500) "|" else " "
        var text = StringBuilder(inputText).insert(inputCursor, cursorCar).toString()
        val textWidth = getTextWidth(text.length)
        if (textWidth > availableWidth)
            text = text.substring(text.length - charsPerLine)

        engine.gfx.camera.disable()
        engine.gfx.setColor(0.1f, 0.1f, 0.1f, 0.9f)
        engine.gfx.drawQuad(0f, 0f, width, height)

        engine.gfx.setColor(0f, 0f, 0f, 0.3f)
        engine.gfx.drawQuad(INPUT_BOX_PADDING, height - INPUT_BOX_HEIGHT, width-INPUT_BOX_PADDING * 2, INPUT_BOX_HEIGHT - INPUT_BOX_PADDING)

        engine.gfx.setColor(1f, 1f, 1f, 0.95f)
        engine.gfx.drawText(text, TEXT_PADDING_X, height - INPUT_BOX_HEIGHT / 2 + INPUT_BOX_PADDING / 2, cliFont)

        var yPos = height - INPUT_BOX_HEIGHT + FONT_SIZE / 2
        commandLog.reversed().forEach { commandEntry ->
            val color = MessageColor.from(commandEntry.type)
            engine.gfx.setColor(color.red, color.green, color.blue)

            val lines = breakIntoLines(commandEntry.message, availableWidth)
            yPos -= lines.size * FONT_SIZE
            lines.forEachIndexed { i, line -> engine.gfx.drawText(line, TEXT_PADDING_X, yPos + i * FONT_SIZE, cliFont) }
        }

        engine.gfx.camera.enable()
    }

    private fun getTextWidth(nChars: Int): Float
        = nChars * (FONT_SIZE / 2f)

    private fun getNumberOfChars(availableWidth: Float): Int
        = max(1f, availableWidth / (FONT_SIZE / 2f)).toInt()

    private fun breakIntoLines(textLine: String, availableWidth: Float): List<String>
    {
        val charsPerLine = getNumberOfChars(availableWidth)
        return textLine
            .split("\n")
            .flatMap {
                val subStrings = mutableListOf<String>()
                var string = it
                while (string.length > charsPerLine)
                {
                    val line = string
                        .substring(0, charsPerLine)
                        .substringBeforeLast(" ")
                        .trim()
                    subStrings.add(line)
                    string = string.removePrefix(line).trim()
                }
                subStrings.add(string)
                subStrings
            }
    }

    private fun StringBuilder.set(text: CharSequence) =
        this.clear().append(text)

    override fun cleanup(engine: GameEngine) {}

    companion object
    {
        private const val FONT_SIZE = 20f
        private const val TEXT_PADDING_X = 15f
        private const val INPUT_BOX_PADDING = 7f
        private const val INPUT_BOX_HEIGHT = 40f
    }
}

data class ConsoleEntry(
    val message: String,
    val type: MessageType
)

data class MessageColor(
    val red: Float,
    val green: Float,
    val blue: Float
) {
    companion object
    {
        private val RED = MessageColor(1f, 0.2f, 0.2f)
        private val WHITE = MessageColor(1f, 1f, 1f)
        private val YELLOW = MessageColor(1f, 1f, 0.2f)
        fun from(type: MessageType) = when(type)
        {
            MessageType.INFO -> WHITE
            MessageType.WARN -> YELLOW
            MessageType.ERROR -> RED
        }
    }
}
