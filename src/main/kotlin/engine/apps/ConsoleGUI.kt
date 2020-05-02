package engine.apps

import engine.GameEngine
import engine.data.Font
import engine.data.Key
import engine.data.Mouse
import engine.modules.console.CommandResult
import engine.modules.console.MessageType
import kotlin.math.max
import kotlin.math.min


class ConsoleGUI : EngineApp
{
    private var active: Boolean = false
    private var widthFraction = 0.3f
    private var heightFraction = 1f

    private var inputText = StringBuilder()
    private var suggestionBaseText = ""
    private var inputCursor = 0
    private var selectCursor = 0
    private var inputTextOffset = 0
    private var historyCursor = -1
    private var suggestionCursor = -1

    override fun init(engine: GameEngine)
    {
        engine.asset.loadFont("/clacon.ttf", "cli_font", floatArrayOf(FONT_SIZE))
        engine.console.registerCommand("clear") {
            engine.console.clearHistory()
            CommandResult("", printCommand = false)
        }
    }

    override fun update(engine: GameEngine)
    {
        ///////////////////////////////// Open/close terminal /////////////////////////////////

        if (engine.input.wasClicked(Key.F1))
            active = !active

        if(!active)
            return

        ///////////////////////////////// Add new text to text box /////////////////////////////////

        val newText = engine.input.textInput
        if (newText.isNotEmpty())
        {
            if (isTextSelected())
            {
                // Removes selected text (CHARACTER)
                removeSelectedText()
            }

            // Add written text and increase cursor (CHARACTER)
            inputText.insert(inputCursor, newText)
            inputCursor += newText.length
            selectCursor = inputCursor
            suggestionCursor = -1
        }

        ///////////////////////////////// Remove text with backspace /////////////////////////////////

        if(engine.input.wasClicked(Key.BACKSPACE) && inputCursor >= 0)
        {
            if(engine.input.isPressed(Key.LEFT_CONTROL))
            {
                if(engine.input.isPressed(Key.LEFT_SHIFT))
                {
                    // Remove all text (BACKSPACE + CTRL + SHIFT)
                    inputText.setLength(0)
                    inputCursor = inputText.length
                    selectCursor = inputCursor
                    suggestionCursor = -1
                }
                else
                {
                    // Remove leftmost word (BACKSPACE + CTRL)
                    inputText.set(inputText.substring(0, inputCursor).substringBeforeLast(" ", "").trimEnd().plus(inputText.substring(inputCursor)))
                    inputCursor = inputText.length
                    selectCursor = inputCursor
                    suggestionCursor = -1
                }
            }
            else
            {
                if (isTextSelected())
                {
                    // Removes selected text (BACKSPACE)
                    removeSelectedText()
                }
                else if (inputCursor > 0)
                {
                    // Remove single character to the left of cursor (BACKSPACE)
                    inputCursor--
                    selectCursor = inputCursor
                    inputText.remove(inputCursor)
                    suggestionCursor = -1
                }
            }
        }

        ///////////////////////////////// Remove text with delete key /////////////////////////////////

        if (engine.input.wasClicked(Key.DELETE) && inputCursor <= inputText.length)
        {
            if (isTextSelected())
            {
                // Removes selected text (DELETE)
                removeSelectedText()
            }
            else if(inputCursor < inputText.length)
            {
                // Remove one single character to the right of cursor (DELETE)
                inputText.remove(inputCursor)
                selectCursor = inputCursor
                suggestionCursor = -1
            }
        }

        ///////////////////////////////// Select all text /////////////////////////////////

        if(engine.input.isPressed(Key.LEFT_CONTROL) && engine.input.wasClicked(Key.A))
        {
            inputCursor = inputText.length
            selectCursor = 0
            suggestionCursor = -1
        }

        ///////////////////////////////// Cut, Copy and Paste /////////////////////////////////

        if(engine.input.isPressed(Key.LEFT_CONTROL))
        {
            if(isTextSelected())
            {
                if(engine.input.wasClicked(Key.X))
                {
                    // Cut selected text and send to clipboard (CTRL + X)
                    engine.input.setClipboard(getSelectedText())
                    removeSelectedText()
                }
                else if(engine.input.wasClicked(Key.C))
                {
                    // Send selected text to clipboard (CTRL + C)
                    engine.input.setClipboard(getSelectedText())
                }
            }

            if(engine.input.wasClicked(Key.V))
            {
                // Past text from clipboard into text box. Replaces selected text. (CTRL + V)
                removeSelectedText()
                val text = engine.input.getClipboard()
                inputText.insert(inputCursor, text)
                inputCursor += text.length
                selectCursor = inputCursor
                suggestionCursor = -1
            }
        }

        ///////////////////////////////// Navigate left in text /////////////////////////////////

        if (engine.input.wasClicked(Key.LEFT))
        {
            if (!engine.input.isPressed(Key.LEFT_SHIFT) && isTextSelected())
            {
                if(hasLeftToRightSelection())
                {
                    // Deselect text by moving cursor to left side of selection (LEFT)
                    inputCursor = selectCursor
                }
                else
                {
                    // Deselect text by moving selectCursor to left side of selection (RIGHT)
                    selectCursor = inputCursor
                }
            }
            else if (engine.input.isPressed(Key.LEFT_CONTROL))
            {
                // Move cursor one word left (LEFT + CTRL)
                inputCursor = max(-1, inputText.substring(0, inputCursor).trim().lastIndexOf(" ")) + 1

                // Keep selectCursor at previous position to select leftmost word (LEFT + CTRL + SHIFT)
                if (!engine.input.isPressed(Key.LEFT_SHIFT))
                    selectCursor = inputCursor
            }
            else
            {
                // Move cursor one character left (LEFT)
                inputCursor = max(0, inputCursor - 1)

                // Keep selectCursor at previous position to select leftmost character (LEFT + SHIFT)
                if (!engine.input.isPressed(Key.LEFT_SHIFT))
                    selectCursor = inputCursor
            }
        }

        ///////////////////////////////// Navigate right in text /////////////////////////////////

        if (engine.input.wasClicked(Key.RIGHT))
        {
            if (!engine.input.isPressed(Key.LEFT_SHIFT) && isTextSelected())
            {
                if(hasRightToLeftSelection())
                {
                    // Deselect text by moving cursor to right side of selection (RIGHT)
                    inputCursor = selectCursor
                }
                else
                {
                    // Deselect text by moving selectCursor to right side of selection (RIGHT)
                    selectCursor = inputCursor
                }
            }
            else if (engine.input.isPressed(Key.LEFT_CONTROL))
            {
                // Move cursor one word right (RIGHT + CTRL)
                val text = inputText.substring(inputCursor).trimStart()
                val index = text.indexOf(" ")
                inputCursor = if (index != -1) inputText.length - text.length + index else inputText.length

                // Keep selectCursor at previous position to select rightmost word (RIGHT + CTRL + SHIFT)
                if (!engine.input.isPressed(Key.LEFT_SHIFT))
                    selectCursor = inputCursor
            }
            else
            {
                // Move cursor one character right (RIGHT)
                inputCursor = min(inputText.length, inputCursor + 1)

                // Keep selectCursor at previous position to select rightmost character (RIGHT + SHIFT)
                if (!engine.input.isPressed(Key.LEFT_SHIFT))
                    selectCursor = inputCursor
            }
        }

        ///////////////////////////////// Move history cursor up by one /////////////////////////////////

        if (engine.input.wasClicked(Key.UP))
        {
            // Move history selector one position up and set input text to that command (UP)
            engine.console.getHistory(historyCursor + 1, MessageType.COMMAND)?.let {
                inputText.set(it.message)
                inputCursor = inputText.length
                selectCursor = inputCursor
                historyCursor++
            }
        }

        ///////////////////////////////// Move history cursor down by one /////////////////////////////////

        if (engine.input.wasClicked(Key.DOWN))
        {
            // Move history selector one position down and set input text to that command (DOWN)
            engine.console.getHistory(historyCursor - 1, MessageType.COMMAND)?.let {
                inputText.set(it.message)
                inputCursor = inputText.length
                selectCursor = inputCursor
                historyCursor--
            }
        }

        ///////////////////////////////// Command suggestions /////////////////////////////////

        if (engine.input.wasClicked(Key.TAB))
        {
            // Set text to be used for searching through suggestions
            if(suggestionCursor == -1)
                suggestionBaseText = inputText.toString()

            // Cycle through suggestions (TAB)
            val suggestions = engine.console.getSuggestions(suggestionBaseText)
            if(suggestions.isNotEmpty())
            {
                suggestionCursor = (suggestionCursor + 1) % suggestions.size
                inputText.set(suggestions[suggestionCursor].base + " ")
                inputCursor = inputText.length
                selectCursor = inputCursor
            }
        }

        ///////////////////////////////// Run command /////////////////////////////////

        if (engine.input.wasClicked(Key.ENTER))
        {
            // Submit input text to console (ENTER)
            engine.console.run(inputText.toString())

            inputText.clear()
            inputCursor = 0
            selectCursor = 0
            historyCursor = -1
            suggestionCursor = -1
        }

        // Resize width of console window (MOUSE LEFT)
        if(engine.input.isPressed(Mouse.LEFT))
        {
            widthFraction = max(0f, min(1f, widthFraction + engine.input.xdMouse / engine.window.width))
            heightFraction = max(0f, min(1f, heightFraction + engine.input.ydMouse / engine.window.height))
        }
    }

    override fun render(engine: GameEngine)
    {
        // Dont render if console is not active
        if(!active)
            return

        val cliFont = engine.asset.get<Font>("cli_font")
        val height = engine.window.height * heightFraction
        val width = engine.window.width * widthFraction
        val availableWidth = width - TEXT_PADDING_X - INPUT_BOX_PADDING
        val charsPerLine = getNumberOfChars(availableWidth)
        val cursorCar = if (System.currentTimeMillis() % 1000 > 500) "|" else " "
        var text = StringBuilder(inputText).insert(inputCursor, cursorCar).toString()

        // Determine what text is visible in input box
        while(inputCursor > inputTextOffset + charsPerLine - 1) inputTextOffset++
        while(inputCursor < inputTextOffset) inputTextOffset--
        text = text.substring(max(inputTextOffset, 0), min(inputTextOffset + charsPerLine, text.length))

        // Disable camera for UI
        engine.gfx.camera.disable()

        // Draw console rectangle
        engine.gfx.setColor(0.1f, 0.1f, 0.1f, 0.9f)
        engine.gfx.drawQuad(0f, 0f, width, height)

        // Draw input box rectangle
        engine.gfx.setColor(0f, 0f, 0f, 0.3f)
        engine.gfx.drawQuad(INPUT_BOX_PADDING, height - INPUT_BOX_HEIGHT, width-INPUT_BOX_PADDING * 2, INPUT_BOX_HEIGHT - INPUT_BOX_PADDING)

        // Draw selection rectangle
        val selectionDistance = selectCursor - inputCursor
        val inBoxCursor = inputCursor - inputTextOffset
        if(selectionDistance != 0)
        {
            val selectionStart = getTextWidth(inBoxCursor + if(selectionDistance > 0) 1 else 0)
            val selectionWidth = getTextWidth(selectionDistance.coerceIn(-inBoxCursor, charsPerLine - inBoxCursor - 1))
            engine.gfx.setColor(0.2f, 0.4f, 1f, 0.9f)
            engine.gfx.drawQuad(TEXT_PADDING_X + selectionStart, height - INPUT_BOX_HEIGHT + INPUT_BOX_PADDING, selectionWidth, FONT_SIZE)
        }

        // Draw input text
        engine.gfx.setColor(1f, 1f, 1f, 0.95f)
        engine.gfx.drawText(text, TEXT_PADDING_X, height - INPUT_BOX_HEIGHT / 2 + INPUT_BOX_PADDING / 2, cliFont)

        // Draw console history
        var yPos = height - INPUT_BOX_HEIGHT + FONT_SIZE / 2
        engine.console.getHistory()
            .reversed()
            .filter { it.visible }
            .forEach { consoleEntry ->
                val prefix = if (consoleEntry.type == MessageType.COMMAND) "> " else ""
                val suffix = if (consoleEntry.type != MessageType.COMMAND && consoleEntry.message.isNotEmpty()) "\n" else ""
                val message = prefix + consoleEntry.message + suffix
                val lines = breakIntoLines(message, availableWidth)
                val color = MessageColor.from(consoleEntry.type)

                yPos -= lines.size * FONT_SIZE
                engine.gfx.setColor(color.red, color.green, color.blue)
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

    private fun getSelectedText(): String = when
    {
        hasLeftToRightSelection() -> inputText.substring(selectCursor, inputCursor)
        hasRightToLeftSelection() -> inputText.substring(inputCursor, selectCursor)
        else -> ""
    }

    private fun removeSelectedText()
    {
        if (hasLeftToRightSelection())
        {
            // Remove left-to-right selection
            inputText.remove(selectCursor, inputCursor - 1)
            inputCursor -= inputCursor - selectCursor
            selectCursor = inputCursor
            suggestionCursor = -1
        }
        else if (hasRightToLeftSelection())
        {
            // Remove right-to-left selection
            inputText.remove(inputCursor, selectCursor - 1)
            selectCursor = inputCursor
            suggestionCursor = -1
        }
    }

    private fun isTextSelected(): Boolean =
        inputCursor != selectCursor

    private fun hasLeftToRightSelection(): Boolean =
         selectCursor < inputCursor

    private fun hasRightToLeftSelection(): Boolean =
        inputCursor < selectCursor

    private fun StringBuilder.set(text: CharSequence) =
        this.clear().append(text)

    private fun StringBuilder.remove(startIndex: Int, endIndex: Int) =
        this.set(this.removeRange(IntRange(startIndex, endIndex)))

    private fun StringBuilder.remove(index: Int) =
        this.set(this.removeRange(IntRange(index, index)))

    override fun cleanup(engine: GameEngine) { }

    companion object
    {
        private const val FONT_SIZE = 20f
        private const val TEXT_PADDING_X = 15f
        private const val INPUT_BOX_PADDING = 7f
        private const val INPUT_BOX_HEIGHT = 40f
    }
}

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
            MessageType.COMMAND -> WHITE
            MessageType.INFO -> WHITE
            MessageType.WARN -> YELLOW
            MessageType.ERROR -> RED
        }
    }
}
