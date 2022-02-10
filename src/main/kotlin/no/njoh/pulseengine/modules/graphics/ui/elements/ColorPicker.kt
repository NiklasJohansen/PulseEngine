package no.njoh.pulseengine.modules.graphics.ui.elements

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.shared.primitives.Color
import no.njoh.pulseengine.modules.input.CursorType
import no.njoh.pulseengine.modules.input.Mouse
import no.njoh.pulseengine.modules.asset.types.Texture
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Size
import no.njoh.pulseengine.modules.graphics.ui.UiElement
import no.njoh.pulseengine.modules.graphics.ui.layout.HorizontalPanel
import no.njoh.pulseengine.modules.graphics.ui.layout.VerticalPanel
import no.njoh.pulseengine.modules.graphics.ui.layout.WindowPanel

class ColorPicker(
    private val outputColor: Color,
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {
    var bgColor = Color.BLANK

    val colorEditor: WindowPanel
    val hexInput: InputField
    val colorPreviewButton: Button

    val saturationBrightnessPicker: SaturationBrightensPicker
    val huePicker: HuePicker
    val redInput: InputField
    val greenInput: InputField
    val blueInput: InputField
    val alphaInput: InputField

    val hsbSection: HorizontalPanel
    val rgbaSection: VerticalPanel

    init
    {
        val hueValue = outputColor.toHue()
        val hueColor = hueValue.toColor()

        /////////////////////////////////////////// HSB Section

        huePicker = HuePicker(
            hueColor,
            width = Size.absolute(30f)
        ).apply {
            padding.right = 5f
            padding.top = 5f
            padding.bottom = 5f
            hue = hueValue
        }

        saturationBrightnessPicker = SaturationBrightensPicker(
            outputColor,
            hueColor,
            width = Size.absolute(140f),
            height = Size.absolute(140f)
        ).apply {
            strokeColor = Color(0f, 0f, 0f)
            padding.left = 5f
            padding.top = 5f
            padding.bottom = 5f
            updateFrom(outputColor)
        }

        hsbSection = HorizontalPanel(
            height = Size.absolute(150f)
        ).apply {
            padding.setAll(5f)
            addChildren(saturationBrightnessPicker, huePicker)
        }

        /////////////////////////////////////////// RGBA

        redInput = InputField((outputColor.red * 255f).toInt().toString(), width = Size.absolute(100f))
        greenInput = InputField((outputColor.green * 255f).toInt().toString(), width = Size.absolute(100f))
        blueInput = InputField((outputColor.blue * 255f).toInt().toString(), width = Size.absolute(100f))
        alphaInput = InputField((outputColor.alpha * 255f).toInt().toString(), width = Size.absolute(100f))

        rgbaSection = VerticalPanel().apply {
            padding.left = 5f
            padding.right = 5f
            padding.bottom = 5f
            addChildren(
                create("Red", redInput),
                create("Green", greenInput),
                create("Blue", blueInput),
                create("Alpha", alphaInput).apply { padding.setAll(5f) }
            )
        }

        /////////////////////////////////////////// Editor

        colorEditor = WindowPanel(width = Size.absolute(190f), height = Size.absolute(300f)).apply {
            color = Color(1f, 1f, 1f)
            hidden = true
            resizable = false
            minWidth = 100f
            minHeight = 100f
            addChildren(
                VerticalPanel().apply {
                    addChildren(hsbSection, rgbaSection)
                }
            )
        }

        /////////////////////////////////////////// Button

        hexInput = InputField(outputColor.toHexString()).apply {
            contentType = InputField.ContentType.HEX_COLOR
            bgColor = Color.BLANK
        }

        colorPreviewButton = Button(
            width = Size.absolute(10f),
            height = Size.absolute(10f)
        ).apply {
            bgColor = outputColor
            bgHoverColor = outputColor
            color = Color.BLANK
            hoverColor = Color(1f, 1f, 1f, 0.5f)
            padding.setAll(5f)
            setOnClicked {
                colorEditor.hidden = !colorEditor.hidden
            }
        }

        val hPanelButton = HorizontalPanel().apply {
            addChildren(hexInput, colorPreviewButton)
        }

        addChildren(hPanelButton)
        addPopup(colorEditor)
        createChangeHandlers()
    }

    private fun create(labelText: String, inputField: InputField) =
        HorizontalPanel().apply {
            padding.top = 5f
            padding.left = 5f
            padding.right = 5f
            addChildren(
                Label(labelText).apply {
                    fontSize = 20f
                    padding.left = 5f
                },
                inputField.apply {
                    contentType = InputField.ContentType.INTEGER
                    numberMinVal = 0f
                    numberMaxVal = 255f
                }
            )
        }

    private fun createChangeHandlers()
    {
        hexInput.setOnValidTextChanged {
            val color = it.text.toColor()
            saturationBrightnessPicker.updateFrom(color)
            huePicker.updateFromColor(color)
            redInput.setTextQuiet((outputColor.red * 255f).toInt().toString())
            greenInput.setTextQuiet((outputColor.green * 255f).toInt().toString())
            blueInput.setTextQuiet((outputColor.blue * 255f).toInt().toString())
            alphaInput.setTextQuiet((outputColor.alpha * 255f).toInt().toString())
            outputColor.red = color.red
            outputColor.green = color.green
            outputColor.blue = color.blue
        }

        saturationBrightnessPicker.setOnChanged {
            redInput.setTextQuiet((outputColor.red * 255f).toInt().toString())
            greenInput.setTextQuiet((outputColor.green * 255f).toInt().toString())
            blueInput.setTextQuiet((outputColor.blue * 255f).toInt().toString())
            hexInput.setTextQuiet(it.toHexString())
        }

        huePicker.setOnHueChanged {
            saturationBrightnessPicker.calculateOutputColor()
            redInput.setTextQuiet((outputColor.red * 255f).toInt().toString())
            greenInput.setTextQuiet((outputColor.green * 255f).toInt().toString())
            blueInput.setTextQuiet((outputColor.blue * 255f).toInt().toString())
            hexInput.setTextQuiet(outputColor.toHexString())
        }

        redInput.setOnValidTextChanged {
            val red = it.text.toInt() / 255f
            outputColor.red = red
            saturationBrightnessPicker.updateFrom(outputColor)
            huePicker.updateFromColor(outputColor)
            hexInput.setTextQuiet(outputColor.toHexString())
        }

        greenInput.setOnValidTextChanged {
            val green = it.text.toInt() / 255f
            outputColor.green = green
            saturationBrightnessPicker.updateFrom(outputColor)
            huePicker.updateFromColor(outputColor)
            hexInput.setTextQuiet(outputColor.toHexString())
        }

        blueInput.setOnValidTextChanged {
            val blue = it.text.toInt() / 255f
            outputColor.blue = blue
            saturationBrightnessPicker.updateFrom(outputColor)
            huePicker.updateFromColor(outputColor)
            hexInput.setTextQuiet(outputColor.toHexString())
        }

        alphaInput.setOnValidTextChanged {
            val alpha = it.text.toInt() / 255f
            outputColor.alpha = alpha
            saturationBrightnessPicker.updateFrom(outputColor)
            huePicker.updateFromColor(outputColor)
            hexInput.setTextQuiet(outputColor.toHexString())
        }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        if (colorEditor.isVisible() && !hasFocus(engine))
            colorEditor.hidden = true
    }

    override fun updateChildLayout()
    {
        val size = height.value - colorPreviewButton.padding.top - colorPreviewButton.padding.bottom
        colorPreviewButton.width.setQuiet(size)
        colorPreviewButton.height.setQuiet(size)
        super.updateChildLayout()
    }

    override fun updatePopupLayout()
    {
        updateColorEditorAlignment()
        super.updatePopupLayout()
    }

    private fun updateColorEditorAlignment()
    {
        var root: UiElement = this
        while (root.parent != null)
            root = root.parent!!

        val isOnRightSide = x.value > root.x.value + root.width.value * 0.5f
        val isOnBottomSide = y.value > root.y.value + root.height.value * 0.5f

        colorEditor.padding.left = if (isOnRightSide) -colorEditor.width.value + width.value else 0f
        colorEditor.padding.top = if (isOnBottomSide) -colorEditor.height.value else height.value
    }

    override fun onRender(surface: Surface2D)
    {
        surface.setDrawColor(bgColor)
        surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value)
    }

    private fun UiElement.hasFocus(engine: PulseEngine): Boolean =
        if (engine.input.hasFocus(this.area)) true
        else popup?.hasFocus(engine) ?: false || children.any { it.hasFocus(engine) }


    private fun String.toColor() =
        java.awt.Color.decode(this).let { Color(it.red, it.green, it.blue, it.alpha) }

    private fun Color.toHexString() =
        String.format(
            "#%02X%02X%02X",
            (red * 255f).toInt(),
            (green * 255f).toInt(),
            (blue * 255f).toInt()
        )

    private fun Color.toHue(): Float
    {
        val values = FloatArray(3)
        java.awt.Color.RGBtoHSB((red * 255f).toInt(), (green * 255f).toInt(), (blue * 255f).toInt(), values)
        return values[0]
    }

    private fun Float.toColor(): Color
    {
        val color = java.awt.Color(java.awt.Color.HSBtoRGB(this, 1f, 1f))
        return Color(color.red / 255f, color.green / 255f, color.blue / 255f)
    }

    class HuePicker(
        var hueColor: Color,
        x: Position = Position.auto(),
        y: Position = Position.auto(),
        width: Size = Size.auto(),
        height: Size = Size.auto()
    ) : UiElement(x, y, width, height) {
        var strokeColor: Color? = null
        var hue = 0f

        private var isSelecting = false
        private var onHueChanged = { c: Color -> }
        private var colorSpace = Array(10) {
            java.awt.Color.getHSBColor(it / 9f, 1f, 1f).let {
                Color(it.red, it.green, it.blue)
            }
        }

        override fun onUpdate(engine: PulseEngine)
        {
            if (mouseInsideArea && engine.input.isPressed(Mouse.LEFT))
                isSelecting = true

            if (isSelecting)
            {
                engine.input.setCursor(CursorType.CROSSHAIR)

                hue = ((engine.input.yMouse - y.value) / height.value).coerceIn(0f, 1f)

                setColorFromHue(hue)
                onHueChanged(hueColor)

                if (!engine.input.isPressed(Mouse.LEFT))
                    isSelecting = false
            }
        }

        private fun setColorFromHue(hue: Float)
        {
            val color = java.awt.Color(java.awt.Color.HSBtoRGB(hue, 1f, 1f))
            hueColor.red = color.red / 255f
            hueColor.green = color.green / 255f
            hueColor.blue = color.blue / 255f
        }

        override fun onRender(surface: Surface2D)
        {
            val xOffset = 5f
            val xBox = x.value + xOffset
            val yBox = y.value
            val boxWidth = width.value - xOffset
            val boxHeight = height.value / (colorSpace.size - 1)
            for (i in 0 until colorSpace.size - 1)
            {
                surface.setDrawColor(colorSpace[i]) // Top left
                surface.drawQuadVertex(xBox, y.value + boxHeight * i)

                surface.setDrawColor(colorSpace[i])  // Top right
                surface.drawQuadVertex(xBox + boxWidth, y.value + boxHeight * i)

                surface.setDrawColor(colorSpace[i + 1]) // Bottom right
                surface.drawQuadVertex(xBox + boxWidth, y.value + boxHeight * (i + 1))

                surface.setDrawColor(colorSpace[i + 1]) // Bottom left
                surface.drawQuadVertex(xBox, y.value + boxHeight * (i + 1))
            }

            // Left arrow
            val arrowHeight = 3
            val yArrow = y.value + arrowHeight + hue * (height.value - arrowHeight * 2f)
            surface.setDrawColor(hueColor)
            surface.drawQuadVertex(x.value + 1, yArrow - 3f)
            surface.drawQuadVertex(x.value + 1, yArrow + 3f)
            surface.drawQuadVertex(x.value + xOffset, yArrow)
            surface.drawQuadVertex(x.value + xOffset, yArrow)

            // Right arrow
            surface.setDrawColor(hueColor)
            surface.drawQuadVertex(x.value + width.value + xOffset - 2, yArrow - arrowHeight)
            surface.drawQuadVertex(x.value + width.value + xOffset - 2, yArrow + arrowHeight)
            surface.drawQuadVertex(x.value + width.value, yArrow)
            surface.drawQuadVertex(x.value + width.value, yArrow)

            if (strokeColor != null && strokeColor!!.alpha != 0f)
            {
                surface.setDrawColor(strokeColor!!)
                surface.drawLine(xBox, yBox, xBox + boxWidth, yBox)
                surface.drawLine(xBox, yBox, xBox, yBox + height.value)
                surface.drawLine(xBox + boxWidth, yBox, xBox + boxWidth, yBox + height.value)
                surface.drawLine(xBox, yBox + height.value, xBox + boxWidth, yBox + height.value)
            }
        }

        fun updateFromColor(color: Color)
        {
            val values = FloatArray(3)
            java.awt.Color.RGBtoHSB((color.red * 255f).toInt(), (color.green * 255f).toInt(), (color.blue * 255f).toInt(), values)
            hue = values[0]
            setColorFromHue(hue)
        }

        fun setOnHueChanged(onHueChanged: (Color) -> Unit)
        {
            this.onHueChanged = onHueChanged
        }
    }

    class SaturationBrightensPicker(
        private val outputColor: Color,
        private val hueColor: Color,
        x: Position = Position.auto(),
        y: Position = Position.auto(),
        width: Size = Size.auto(),
        height: Size = Size.auto()
    ) : UiElement(x, y, width, height) {
        var strokeColor: Color? = null
        var markerSize = 8f
        var saturation = 0f
        var luminance = 0f

        private var isSelecting = false
        private var onChanged = { c: Color -> }

        override fun onMouseLeave(engine: PulseEngine)
        {
            engine.input.setCursor(CursorType.ARROW)
        }

        override fun onUpdate(engine: PulseEngine)
        {
            if (isSelecting)
            {
                engine.input.setCursor(CursorType.CROSSHAIR)

                val border = markerSize * 0.5f
                val xMouse = engine.input.xMouse.coerceIn(x.value + border, x.value + width.value - border)
                val yMouse = engine.input.yMouse.coerceIn(y.value + border, y.value + height.value - border)
                saturation = (xMouse - x.value) / width.value
                luminance = 1f - ((yMouse - y.value) / height.value)

                calculateOutputColor()
                onChanged(outputColor)

                if (!engine.input.isPressed(Mouse.LEFT))
                    isSelecting = false
            }
            else if (mouseInsideArea && engine.input.isPressed(Mouse.LEFT))
                isSelecting = true
        }

        fun calculateOutputColor()
        {
            outputColor.red = ((1 - saturation) + saturation * hueColor.red) * (luminance)
            outputColor.green = ((1 - saturation) + saturation * hueColor.green) * (luminance)
            outputColor.blue = ((1 - saturation) + saturation * hueColor.blue) * (luminance)
        }

        override fun onRender(surface: Surface2D)
        {
            surface.setDrawColor(Color(1f, 1f, 1f)) // White
            surface.drawQuadVertex(x.value, y.value)
            surface.setDrawColor(hueColor)
            surface.drawQuadVertex(x.value + width.value, y.value)
            surface.setDrawColor(Color(0f, 0f, 0f)) // Black
            surface.drawQuadVertex(x.value + width.value, y.value + height.value)
            surface.setDrawColor(Color(0f, 0f, 0f)) // Black
            surface.drawQuadVertex(x.value, y.value + height.value)

            if (strokeColor != null && strokeColor!!.alpha != 0f)
            {
                surface.setDrawColor(strokeColor!!)
                surface.drawLine(x.value, y.value, x.value + width.value, y.value)
                surface.drawLine(x.value, y.value, x.value, y.value + height.value)
                surface.drawLine(x.value + width.value, y.value, x.value + width.value, y.value + height.value)
                surface.drawLine(x.value, y.value + height.value, x.value + width.value, y.value + height.value)
            }

            val xPoint = x.value + saturation * width.value
            val yPoint = y.value + (1f - luminance) * height.value
            surface.setDrawColor(0f, 0f, 0f)
            surface.drawTexture(Texture.BLANK, xPoint, yPoint, markerSize, markerSize, 0f, 0.5f, 0.5f)
            surface.setDrawColor(outputColor)
            surface.drawTexture(Texture.BLANK, xPoint, yPoint, markerSize - 2f, markerSize - 2f, 0f, 0.5f, 0.5f)
        }

        fun setOnChanged(onColorChanged: (Color) -> Unit)
        {
            this.onChanged = onColorChanged
        }

        fun updateFrom(color: Color)
        {
            val values = FloatArray(3)
            java.awt.Color.RGBtoHSB((color.red * 255f).toInt(), (color.green * 255f).toInt(), (color.blue * 255f).toInt(), values)
            val (hue, saturation, luminance) = values
            this.saturation = saturation
            this.luminance = luminance
        }
    }
}