package no.njoh.pulseengine.modules.gui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.CursorType.*
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.input.Input
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.modules.gui.elements.InputField.ContentType.*
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.input.Mouse
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.gui.*
import no.njoh.pulseengine.modules.gui.UiParams.UI_SCALE
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

class InputField (
    defaultText: String,
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
)  : UiElement(x, y, width, height)  {

    enum class ContentType  { TEXT, INTEGER, FLOAT, BOOLEAN, HEX_COLOR, INTEGER_ARRAY, FLOAT_ARRAY }

    var editable = true
    var bgColor = Color.WHITE
    var bgColorHover = Color.BLANK
    var strokeColor = Color(0.4f, 0.4f, 0.4f, 1f)
    var textColor = Color.WHITE
    var placeHolderTextColor = Color(0.7f, 0.7f, 0.7f, 1f)
    var selectionColor = Color(0.2f, 0.4f, 1f, 0.9f)
    var invalidTextColor = Color(227, 108, 60)
    var font = Font.DEFAULT
    var fontSize = ScaledValue.of(24f)
    var numberMinVal = Float.NEGATIVE_INFINITY
    var numberMaxVal = Float.POSITIVE_INFINITY
    var contentType = TEXT
    var leftTextPadding = ScaledValue.of(10f)
    var cornerRadius = ScaledValue.of(0f)
    var numberStepperWidth = ScaledValue.of(30f)

    var placeHolderText = ""
    var text: String
        get() = inputText.toString()
        set(value)
        {
            if (value != inputText.toString())
            {
                clear()
                inputText.set(value)
                inputCursor = value.length
                selectCursor = value.length
            }
        }

    private var inputText = StringBuilder(defaultText)
    private var suggestionBaseText = ""
    private var inputCursor = inputText.length
    private var selectCursor = inputText.length
    private var inputTextOffset = 0
    private var historyCursor = -1
    private var suggestionCursor = -1

    private var numberStepperMouseStart: Float? = null
    private var numberStepperStartValue: Float? = null
    private var isSteppingNumber = false

    private var requestFocusRelease = false
    private var hasFocus = false
    private var isMouseOver = false
    private var lastHasFocus = false

    private val hexColorRegex = "#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})".toRegex()
    private val intArrayRegex = "^\\s*(-?[0-9]+)+(\\s*,\\s*-?[0-9]+)*\\s*\$".toRegex()
    private val floatArrayRegex = "^\\s*(\\s*-?\\d+(\\.\\d+)?)(\\s*,\\s*-?\\d+(\\.\\d+)?)*\\s*\$".toRegex()

    private var onFocusLost: (InputField) -> Unit = {  }
    private var onEnterPressed: (InputField) -> Unit = { unfocus() }
    private var onTextChanged: (InputField) -> Unit = { }
    private var onValidTextChanged: (InputField) -> Unit = { }
    private var onGetHistory: (Int) -> String? = { _ -> null }
    private var onGetSuggestion: (String) -> List<String> = { _ -> emptyList() }

    var lastText = text
        private set
    var lastValidText = text
        private set
    var isValid = true
        private set

    override fun onMouseLeave(engine: PulseEngine)
    {
        engine.input.setCursor(ARROW)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        hasFocus = engine.input.hasFocus(area) && editable
        isMouseOver = engine.input.hasHoverFocus(area) && mouseInsideArea

        if (hasFocus != lastHasFocus)
        {
            if (!hasFocus)
            {
                selectCursor = inputCursor
                onFocusLost(this)
            }

            lastHasFocus = hasFocus
        }

        if (requestFocusRelease)
        {
            if (hasFocus)
                engine.input.releaseFocus(area)

            requestFocusRelease = false
        }


        val isMouseInsideNumberStepper =
            (contentType == INTEGER || contentType == FLOAT) &&
            mouseInsideArea &&
            engine.input.xMouse > x.value + width.value - numberStepperWidth.value

        if (engine.input.isPressed(Mouse.LEFT) && editable)
        {
            if (!isSteppingNumber && isMouseInsideNumberStepper)
            {
                numberStepperMouseStart = engine.input.yMouse
                numberStepperStartValue = text.toFloatOrNull()
                isSteppingNumber = true
            }
        }
        else if (isSteppingNumber)
        {
            engine.input.setCursor(ARROW)
            isSteppingNumber = false
        }

        when
        {
            !editable -> { }
            isSteppingNumber || isMouseInsideNumberStepper -> engine.input.setCursor(VERTICAL_RESIZE)
            mouseInsideArea -> engine.input.setCursor(IBEAM)
        }

        if (isSteppingNumber)
            handleNumberStepper(engine)

        if (hasFocus && editable && !isSteppingNumber)
            handleTextEditing(engine.input)

        if (text != lastText)
        {
            isValid = validateContent()
            if (isValid)
            {
                onValidTextChanged(this)
                lastValidText = text
            }
            onTextChanged(this)
            lastText = text
        }
    }

    private fun handleTextEditing(input: Input)
    {
        ///////////////////////////////// Add new text to text box /////////////////////////////////

        val newText = input.textInput
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

        if (input.wasClicked(Key.BACKSPACE) && inputCursor >= 0)
        {
            if (input.isPressed(Key.LEFT_CONTROL))
            {
                if (input.isPressed(Key.LEFT_SHIFT))
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

        if (input.wasClicked(Key.DELETE) && inputCursor <= inputText.length)
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

        if (input.isPressed(Key.LEFT_CONTROL) && input.wasClicked(Key.A))
        {
            inputCursor = inputText.length
            selectCursor = 0
            suggestionCursor = -1
        }

        ///////////////////////////////// Cut, Copy and Paste /////////////////////////////////

        if (input.isPressed(Key.LEFT_CONTROL))
        {
            if (isTextSelected())
            {
                if (input.wasClicked(Key.X))
                {
                    // Cut selected text and send to clipboard (CTRL + X)
                    input.setClipboard(getSelectedText())
                    removeSelectedText()
                }
                else if (input.wasClicked(Key.C))
                {
                    // Send selected text to clipboard (CTRL + C)
                    input.setClipboard(getSelectedText())
                }
            }

            if (input.wasClicked(Key.V))
            {
                // Past text from clipboard into text box. Replaces selected text. (CTRL + V)
                removeSelectedText()
                val text = input.getClipboard()
                inputText.insert(inputCursor, text)
                inputCursor += text.length
                selectCursor = inputCursor
                suggestionCursor = -1
            }
        }

        ///////////////////////////////// Navigate left in text /////////////////////////////////

        if (input.wasClicked(Key.LEFT))
        {
            if (!input.isPressed(Key.LEFT_SHIFT) && isTextSelected())
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
            else if (input.isPressed(Key.LEFT_CONTROL))
            {
                // Move cursor one word left (LEFT + CTRL)
                inputCursor = max(-1, inputText.substring(0, inputCursor).trim().lastIndexOf(" ")) + 1

                // Keep selectCursor at previous position to select leftmost word (LEFT + CTRL + SHIFT)
                if (!input.isPressed(Key.LEFT_SHIFT))
                    selectCursor = inputCursor
            }
            else
            {
                // Move cursor one character left (LEFT)
                inputCursor = max(0, inputCursor - 1)

                // Keep selectCursor at previous position to select leftmost character (LEFT + SHIFT)
                if (!input.isPressed(Key.LEFT_SHIFT))
                    selectCursor = inputCursor
            }
        }

        ///////////////////////////////// Navigate right in text /////////////////////////////////

        if (input.wasClicked(Key.RIGHT))
        {
            if (!input.isPressed(Key.LEFT_SHIFT) && isTextSelected())
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
            else if (input.isPressed(Key.LEFT_CONTROL))
            {
                // Move cursor one word right (RIGHT + CTRL)
                val text = inputText.substring(inputCursor).trimStart()
                val index = text.indexOf(" ")
                inputCursor = if (index != -1) inputText.length - text.length + index else inputText.length

                // Keep selectCursor at previous position to select rightmost word (RIGHT + CTRL + SHIFT)
                if (!input.isPressed(Key.LEFT_SHIFT))
                    selectCursor = inputCursor
            }
            else
            {
                // Move cursor one character right (RIGHT)
                inputCursor = min(inputText.length, inputCursor + 1)

                // Keep selectCursor at previous position to select rightmost character (RIGHT + SHIFT)
                if (!input.isPressed(Key.LEFT_SHIFT))
                    selectCursor = inputCursor
            }
        }

        ///////////////////////////////// Move history cursor up by one /////////////////////////////////

        if (input.wasClicked(Key.UP))
        {
            // Move history selector one position up and set input text to that command (UP)
            onGetHistory(historyCursor + 1)?.let {
                inputText.set(it)
                inputCursor = inputText.length
                selectCursor = inputCursor
                historyCursor++
            }
        }

        ///////////////////////////////// Move history cursor down by one /////////////////////////////////

        if (input.wasClicked(Key.DOWN))
        {
            // Move history selector one position down and set input text to that command (DOWN)
            onGetHistory(historyCursor - 1)?.let {
                inputText.set(it)
                inputCursor = inputText.length
                selectCursor = inputCursor
                historyCursor--
            }
        }

        ///////////////////////////////// Command suggestions /////////////////////////////////

        if (input.wasClicked(Key.TAB))
        {
            // Set text to be used for searching through suggestions
            if (suggestionCursor == -1)
                suggestionBaseText = inputText.toString()

            // Cycle through suggestions (TAB)
            val suggestions = onGetSuggestion(suggestionBaseText)
            if (suggestions.isNotEmpty())
            {
                suggestionCursor = (suggestionCursor + 1) % suggestions.size
                inputText.set(suggestions[suggestionCursor])
                if (suggestions.size == 1)
                    inputText.append(" ")
                inputCursor = inputText.length
                selectCursor = inputCursor
            }
        }

        ///////////////////////////////// ENTER pressed /////////////////////////////////

        if (input.wasClicked(Key.ENTER))
            onEnterPressed(this)

        if (input.wasClicked(Key.TAB))
            onEnterPressed(this)
    }

    private fun validateContent() = when (contentType)
    {
        TEXT -> true
        INTEGER -> { val num = text.toIntOrNull(); num != null && num >= numberMinVal && num <= numberMaxVal }
        FLOAT -> { val num = text.toFloatOrNull(); num != null && num >= numberMinVal && num <= numberMaxVal }
        BOOLEAN -> text == "true" || text == "false"
        HEX_COLOR -> hexColorRegex.matches(text)
        INTEGER_ARRAY -> intArrayRegex.matches(text)
        FLOAT_ARRAY -> floatArrayRegex.matches(text)
    }

    private fun handleNumberStepper(engine: PulseEngine)
    {
        numberStepperMouseStart?.let { mouseStart ->
            val diff = (mouseStart - engine.input.yMouse)
            val value = ((numberStepperStartValue ?: 0f) + (diff * diff * sign(diff) * 0.001f)).coerceIn(numberMinVal, numberMaxVal)
            text = when (contentType)
            {
                INTEGER -> (value).toInt().toString()
                FLOAT -> "%.2f".format(Locale.ENGLISH, value)
                else -> "n/a"
            }
        }
    }

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        val charsPerLine = getNumberOfChars(width.value - leftTextPadding.value)
        val hasText = inputText.isNotEmpty()
        var text = if (hasText) inputText.toString() else placeHolderText
        var inputCursor = inputCursor
        var inputTextOffset = inputTextOffset
        var selectCursor = selectCursor

        if (!hasFocus)
        {
            inputCursor = 0
            inputTextOffset = 0
            selectCursor = 0
        }

        // Determine what text is visible in input box
        while(inputCursor > inputTextOffset + charsPerLine - 1) inputTextOffset++
        while(inputCursor < inputTextOffset) inputTextOffset--
        text = text.substring(max(inputTextOffset, 0), min(inputTextOffset + charsPerLine, text.length))

        // Draw input box rectangle
        surface.setDrawColor(if (isMouseOver && !hasFocus) bgColorHover else bgColor)
        surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value, cornerRadius = cornerRadius.value)

        if (hasFocus && strokeColor.alpha > 0f)
        {
            surface.setDrawColor(strokeColor)
            surface.drawLine(x.value, y.value, x.value + width.value, y.value)
            surface.drawLine(x.value, y.value + height.value, x.value + width.value, y.value + height.value)
            surface.drawLine(x.value, y.value, x.value, y.value + height.value)
            surface.drawLine(x.value + width.value, y.value, x.value + width.value, y.value + height.value)
        }

        if (!isValid)
        {
            surface.setDrawColor(invalidTextColor)
            surface.drawTexture(Texture.BLANK, x.value + cornerRadius.value, y.value + height.value - 2, width.value - cornerRadius.value * 2, 2f)
        }

        // Draw selection rectangle
        val selectionDistance = selectCursor - inputCursor
        val inBoxCursor = inputCursor - inputTextOffset
        val cursorStart = getTextWidth(text.substring(0, inBoxCursor))
        if (selectionDistance != 0)
        {
            val length = selectionDistance.coerceIn(-inBoxCursor, charsPerLine - inBoxCursor - 1)
            val selectedOnScreenText = if (length >= 0)
                text.substring(inBoxCursor, inBoxCursor + length)
            else
                text.substring(inBoxCursor + length, inBoxCursor)
            val selectionWidth = getTextWidth(selectedOnScreenText) * sign(length.toFloat())

            surface.setDrawColor(selectionColor)
            surface.drawTexture(Texture.BLANK,x.value + leftTextPadding.value + cursorStart, y.value + height.value / 2, selectionWidth, fontSize.value, yOrigin = 0.5f)
        }

        // Draw cursor
        if (hasFocus && System.currentTimeMillis() % 1000 > 500)
        {
            val xCursor = x.value + leftTextPadding.value + cursorStart
            val yCursorCenter = y.value + height.value / 2f
            val cursorHeight = fontSize.value / 2.5f

            surface.setDrawColor(textColor)
            surface.drawLine(xCursor, yCursorCenter - cursorHeight, xCursor, yCursorCenter + cursorHeight)
        }

        // Draw input text
        surface.setDrawColor(if (hasText) textColor else placeHolderTextColor)
        surface.drawText(text, x.value + leftTextPadding.value, y.value + height.value / 2f, font, fontSize.value, yOrigin = 0.5f)

        if ((contentType == INTEGER || contentType == FLOAT) && width.value > 50f && editable)
        {
            val xArrow = x.value + width.value - numberStepperWidth.value / 2
            val yArrow = y.value + height.value / 2
            val size = 6f * UI_SCALE
            val offset = 5f * UI_SCALE
            drawArrow(xArrow, yArrow - offset, size, size, surface, textColor, -2.5f)
            drawArrow(xArrow, yArrow + offset, size, size, surface, textColor, 2.5f)
        }
    }

    private fun drawArrow(x: Float, y: Float, width: Float, height: Float, surface: Surface2D, color: Color, lengthFactor: Float = 2.5f)
    {
        surface.setDrawColor(color)
        surface.drawQuadVertex(x, y + height / lengthFactor)
        surface.drawQuadVertex(x, y + height / lengthFactor)
        surface.drawQuadVertex(x - width / 2, y - height / lengthFactor)
        surface.drawQuadVertex(x + width / 2, y - height / lengthFactor)
    }

    fun unfocus()
    {
        requestFocusRelease = true
    }

    fun clear()
    {
        inputText.clear()
        inputCursor = 0
        selectCursor = 0
        historyCursor = -1
        suggestionCursor = -1
    }

    /**
     * Sets the text without triggering an onTextChange Event
     */
    fun setTextQuiet(newText: String)
    {
        text = newText
        lastText = newText
        isValid = validateContent()
    }

    fun setOnFocusLost(callback: (InputField) -> Unit)
    {
        onFocusLost = callback
    }

    fun setOnTextChanged(callback: (InputField) -> Unit)
    {
        onTextChanged = callback
    }

    fun setOnValidTextChanged(callback: (InputField) -> Unit)
    {
        onValidTextChanged = callback
    }

    fun setOnEnterPressed(callback: (InputField) -> Unit)
    {
        onEnterPressed = callback
    }

    fun setOnGetHistory(callback: (Int) -> String?)
    {
        onGetHistory = callback
    }

    fun setGetSuggestion(callback: (String) -> List<String>)
    {
        onGetSuggestion = callback
    }

    private fun getTextWidth(text: String): Float
        = font.getWidth(text, fontSize.value)

    private fun getNumberOfChars(availableWidth: Float): Int
        = max(1f, availableWidth / (fontSize.value / 2f)).toInt()

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
}