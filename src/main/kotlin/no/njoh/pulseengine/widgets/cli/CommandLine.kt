package no.njoh.pulseengine.widgets.cli

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.console.CommandResult
import no.njoh.pulseengine.core.console.MessageType
import no.njoh.pulseengine.core.input.FocusArea
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.input.MouseButton
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.widget.Widget
import kotlin.math.max
import kotlin.math.min

class CommandLine : Widget
{
    override var isRunning = false

    private var widthFraction = 0.5f
    private var heightFraction = 0.5f

    private var inputText = StringBuilder()
    private var suggestionBaseText = ""
    private var inputCursor = 0
    private var selectCursor = 0
    private var inputTextOffset = 0
    private var historyCursor = -1
    private var suggestionCursor = -1
    private var area = FocusArea(0f, 0f, widthFraction, heightFraction)

    override fun onCreate(engine: PulseEngine)
    {
        engine.gfx.createSurface("cli", zOrder = -100, backgroundColor = Color.BLANK)
        engine.asset.load(Font("/pulseengine/assets/clacon.ttf", "cli_font"))
        engine.console.registerCommand("showConsole") {
            isRunning = !isRunning
            if (isRunning)
                engine.input.acquireFocus(area)
            else
                engine.input.releaseFocus(area)
            CommandResult("", showCommand = false)
        }

        area.update(0f, 0f, widthFraction * engine.window.width, heightFraction * engine.window.height)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        engine.input.requestFocus(area)

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

        if (engine.input.wasClicked(Key.BACKSPACE) && inputCursor >= 0)
        {
            if (engine.input.isPressed(Key.LEFT_CONTROL))
            {
                if (engine.input.isPressed(Key.LEFT_SHIFT))
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
            else if (inputCursor < inputText.length)
            {
                // Remove one single character to the right of cursor (DELETE)
                inputText.remove(inputCursor)
                selectCursor = inputCursor
                suggestionCursor = -1
            }
        }

        ///////////////////////////////// Select all text /////////////////////////////////

        if (engine.input.isPressed(Key.LEFT_CONTROL) && engine.input.wasClicked(Key.A))
        {
            inputCursor = inputText.length
            selectCursor = 0
            suggestionCursor = -1
        }

        ///////////////////////////////// Cut, Copy and Paste /////////////////////////////////

        if (engine.input.isPressed(Key.LEFT_CONTROL))
        {
            if (isTextSelected())
            {
                if (engine.input.wasClicked(Key.X))
                {
                    // Cut selected text and send to clipboard (CTRL + X)
                    engine.input.setClipboard(getSelectedText())
                    removeSelectedText()
                }
                else if (engine.input.wasClicked(Key.C))
                {
                    // Send selected text to clipboard (CTRL + C)
                    engine.input.setClipboard(getSelectedText())
                }
            }

            if (engine.input.wasClicked(Key.V))
            {
                // Past text from clipboard into text box. Replaces selected text. (CTRL + V)
                removeSelectedText()
                engine.input.getClipboard { content ->
                    inputText.insert(inputCursor, content)
                    inputCursor += content.length
                    selectCursor = inputCursor
                    suggestionCursor = -1
                }
            }
        }

        ///////////////////////////////// Navigate left in text /////////////////////////////////

        if (engine.input.wasClicked(Key.LEFT))
        {
            if (!engine.input.isPressed(Key.LEFT_SHIFT) && isTextSelected())
            {
                if (hasLeftToRightSelection())
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
                if (hasRightToLeftSelection())
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
            if (suggestionCursor == -1)
                suggestionBaseText = inputText.toString()

            // Cycle through suggestions (TAB)
            val suggestions = engine.console.getSuggestions(suggestionBaseText)
            if (suggestions.isNotEmpty())
            {
                suggestionCursor = (suggestionCursor + 1) % suggestions.size
                inputText.set(suggestions[suggestionCursor].base)
                if (suggestions.size == 1)
                    inputText.append(" ")
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

        // Resize width of console window (MOUSE RIGHT)
        if (engine.input.isPressed(MouseButton.RIGHT))
        {
            widthFraction = max(0f, min(1f, widthFraction + engine.input.xdMouse / engine.window.width))
            heightFraction = max(0f, min(1f, heightFraction + engine.input.ydMouse / engine.window.height))
            area.update(0f, 0f, widthFraction * engine.window.width, heightFraction * engine.window.height)
        }
    }

    override fun onRender(engine: PulseEngine)
    {
        val cliFont = engine.asset.getOrNull("cli_font") ?: Font.DEFAULT
        val height = engine.window.height * heightFraction
        val width = engine.window.width * widthFraction
        val availableWidth = width - TEXT_PADDING_X - INPUT_BOX_PADDING
        val charsPerLine = getNumberOfChars(availableWidth)
        val cursorCar = if (System.currentTimeMillis() % 1000 > 500 && engine.input.hasFocus(area)) "|" else " "
        var text = StringBuilder(inputText).insert(inputCursor, cursorCar).toString()

        // Determine what text is visible in input box
        while(inputCursor > inputTextOffset + charsPerLine - 1) inputTextOffset++
        while(inputCursor < inputTextOffset) inputTextOffset--
        text = text.substring(max(inputTextOffset, 0), min(inputTextOffset + charsPerLine, text.length))

        // Render to the overlay layer
        val surface = engine.gfx.getSurfaceOrDefault("cli")

        // Draw console rectangle
        surface.setDrawColor(0.0f, 0.0f, 0.0f, 0.98f)
        surface.drawTexture(Texture.BLANK, -5f, -5f, width + 5, height + 5, cornerRadius = 10f)

        // Draw input box rectangle
        surface.setDrawColor(0.01f, 0.01f, 0.01f, 0.92f)
        surface.drawTexture(Texture.BLANK, INPUT_BOX_PADDING, height - INPUT_BOX_HEIGHT, width - INPUT_BOX_PADDING * 2, INPUT_BOX_HEIGHT - INPUT_BOX_PADDING, cornerRadius = 5f)

        // Draw selection rectangle
        val selectionDistance = selectCursor - inputCursor
        val inBoxCursor = inputCursor - inputTextOffset
        if (selectionDistance != 0)
        {
            val selectionStart = getTextWidth(inBoxCursor + if (selectionDistance > 0) 1 else 0)
            val selectionWidth = getTextWidth(selectionDistance.coerceIn(-inBoxCursor, charsPerLine - inBoxCursor - 1))
            surface.setDrawColor(0.2f, 0.4f, 1f, 0.9f)
            surface.drawQuad(TEXT_PADDING_X + selectionStart, height - INPUT_BOX_HEIGHT + INPUT_BOX_PADDING, selectionWidth, FONT_SIZE)
        }

        // Draw input text
        surface.setDrawColor(1f, 1f, 1f, 0.95f)
        surface.drawText(text, TEXT_PADDING_X, height - INPUT_BOX_HEIGHT / 2, cliFont, yOrigin = 0.7f, fontSize = FONT_SIZE)

        // Draw console history
        var yPos = height - INPUT_BOX_HEIGHT
        engine.console.getHistory()
            .reversed()
            .filter { it.visible }
            .forEach { consoleEntry ->
                val prefix = if (consoleEntry.type == MessageType.COMMAND) "\n> " else ""
                val message = prefix + consoleEntry.message
                val lines = breakIntoLines(message, availableWidth)
                val color = MessageColor.from(consoleEntry.type)

                yPos -= lines.size * FONT_SIZE
                surface.setDrawColor(color.red, color.green, color.blue, 0.9f)
                lines.forEachIndexed { i, line -> surface.drawText(line, TEXT_PADDING_X, yPos + i * FONT_SIZE, cliFont, yOrigin = 0.5f, fontSize = FONT_SIZE) }
            }
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

    override fun onDestroy(engine: PulseEngine) { }

    companion object
    {
        private const val FONT_SIZE = 18f
        private const val TEXT_PADDING_X = 15f
        private const val INPUT_BOX_PADDING = 7f
        private const val INPUT_BOX_HEIGHT = FONT_SIZE + 20
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
        fun from(type: MessageType) = when (type)
        {
            MessageType.COMMAND -> WHITE
            MessageType.INFO -> WHITE
            MessageType.WARN -> YELLOW
            MessageType.ERROR -> RED
        }
    }
}
