package engine.apps

import engine.GameEngine
import engine.data.Font
import engine.data.Key
import engine.modules.CommandResult
import engine.modules.MessageType
import java.lang.Integer.max
import java.lang.Math.min

class ConsoleGUI : EngineApp
{
    private var active: Boolean = false
    private var textBoxContent = StringBuilder()
    private var inputCursor = 0
    private var historyCursor = -1
    private var suggestionCursor = 0
    private var suggestionBaseText = ""
    private val commandLog = mutableListOf<ConsoleEntry>()

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
            textBoxContent.insert(inputCursor, newText)
            inputCursor += newText.length
            suggestionCursor = -1
        }

        // Remove text with backspace
        if(inputCursor > 0 && engine.input.wasClicked(Key.BACKSPACE))
        {
            if(engine.input.isPressed(Key.LEFT_CONTROL))
            {
                val words = textBoxContent.split("\\s".toRegex())
                val text = textBoxContent.toString().replace(words.last(), "").trimEnd()
                textBoxContent.clear().append(text)
                inputCursor = textBoxContent.length
                suggestionCursor = -1
            }
            else
            {
                val remainingText = textBoxContent.removeRange(IntRange(--inputCursor, inputCursor))
                textBoxContent.clear().append(remainingText)
                suggestionCursor = -1
            }
        }

        // Navigate left in text
        if (engine.input.wasClicked(Key.LEFT))
            inputCursor = max(0, inputCursor - 1)

        // Navigate right in text
        if (engine.input.wasClicked(Key.RIGHT))
            inputCursor = min(textBoxContent.length, inputCursor + 1)

        // Move history cursor up by one
        if (engine.input.wasClicked(Key.UP))
        {
            engine.console.getHistory(historyCursor + 1)?.let {
                textBoxContent.clear().append(it)
                inputCursor = textBoxContent.length
                historyCursor++
            }
        }

        // Move history cursor down by one
        if (engine.input.wasClicked(Key.DOWN))
        {
            engine.console.getHistory(historyCursor - 1)?.let {
                textBoxContent.clear().append(it)
                inputCursor = textBoxContent.length
                historyCursor--
            }
        }

        // Command suggestions
        if (engine.input.wasClicked(Key.TAB))
        {
            if(suggestionCursor == -1)
                suggestionBaseText = textBoxContent.toString()

            val suggestions = engine.console.getSuggestions(suggestionBaseText)
            if(suggestions.isNotEmpty())
            {
                suggestionCursor = (suggestionCursor + 1) % suggestions.size
                textBoxContent.clear().append(suggestions[suggestionCursor].base)
                inputCursor = textBoxContent.length
            }
        }

        // Run command
        if (engine.input.wasClicked(Key.ENTER))
        {
            val commandString = textBoxContent.toString()
            val result = engine.console.run(commandString)

            commandLog.add(ConsoleEntry("> $commandString", MessageType.INFO))
            if (result.message.isNotBlank())
            {
                commandLog.add(ConsoleEntry(result.message, result.type))
                commandLog.add(ConsoleEntry("", MessageType.INFO))
            }

            textBoxContent.clear()
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
        var text = StringBuilder(textBoxContent).insert(inputCursor, cursorCar).toString()
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
        = (availableWidth / (FONT_SIZE / 2f)).toInt()
    
    private fun breakIntoLines(textLine: String, availableWidth: Float): List<String>
    {
        return textLine
            .split("\n")
            .flatMap { line ->
                val charsPerLine = getNumberOfChars(availableWidth)
                val nLines = (getTextWidth(line.length) / availableWidth).toInt() + 1
                0.until(nLines).map { line.substring(it * charsPerLine, min((it + 1) * charsPerLine, line.length)) }
            }
    }

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
