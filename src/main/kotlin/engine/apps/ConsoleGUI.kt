package engine.apps

import engine.GameEngine
import engine.data.Font
import engine.data.Key
import java.lang.Integer.max
import java.lang.Math.min

class ConsoleGUI : EngineApp
{
    private var active: Boolean = false
    private var textBoxContent = StringBuilder()
    private var cursorPos = 0
    private val commandLog = mutableListOf<String>()

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
            textBoxContent.insert(cursorPos, newText)
            cursorPos += newText.length
        }

        // Remove text with backspace
        if(cursorPos > 0 && engine.input.wasClicked(Key.BACKSPACE))
        {
            if(engine.input.isPressed(Key.LEFT_CONTROL))
            {
                val words = textBoxContent.split("\\s".toRegex())
                val text = textBoxContent.toString().replace(words.last(), "").trimEnd()
                textBoxContent.clear().append(text)
                cursorPos = textBoxContent.length
            }
            else
            {
                val remainingText = textBoxContent.removeRange(IntRange(--cursorPos, cursorPos))
                textBoxContent.clear().append(remainingText)
            }
        }

        // Navigate left in text
        if (engine.input.wasClicked(Key.LEFT))
            cursorPos = max(0, cursorPos - 1)

        // Navigate right in text
        if (engine.input.wasClicked(Key.RIGHT))
            cursorPos = min(textBoxContent.length, cursorPos + 1)

        // Run command
        if (engine.input.wasClicked(Key.ENTER))
        {
            commandLog.add(textBoxContent.toString())
            textBoxContent.clear()
            cursorPos = 0
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
        var text = "> " + StringBuilder(textBoxContent).insert(cursorPos, cursorCar).toString()
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
        commandLog.reversed().forEach { commandText ->
            val lines = breakIntoLines("> $commandText", availableWidth)
            yPos -= lines.size * FONT_SIZE
            lines.forEachIndexed { i, line -> engine.gfx.drawText(line, TEXT_PADDING_X, yPos + i * FONT_SIZE, cliFont) }
        }

        engine.gfx.camera.enable()
    }

    private fun getTextWidth(nChars: Int): Float
        = nChars * (FONT_SIZE / 2f)

    private fun getNumberOfChars(availableWidth: Float): Int
        = (availableWidth / (FONT_SIZE / 2f)).toInt()
    
    private fun breakIntoLines(line: String, availableWidth: Float): List<String>
    {
        val charsPerLine = getNumberOfChars(availableWidth)
        val nLines = (getTextWidth(line.length) / availableWidth).toInt() + 1
        return 0.until(nLines).map { line.substring(it * charsPerLine, min((it + 1) * charsPerLine, line.length)) }
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