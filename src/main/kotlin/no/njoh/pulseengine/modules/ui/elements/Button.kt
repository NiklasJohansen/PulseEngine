package no.njoh.pulseengine.modules.ui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.input.CursorType
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.modules.ui.*

open class Button(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    var toggleButton = false
    var isPressed = false
    var disabled = false

    var bgColor = Color.BLANK
    var bgHoverColor = Color.BLANK
    var color = Color.BLANK
    var hoverColor = Color.BLANK
    var activeColor = Color.BLANK
    var activeHoverColor: Color? = null
    var disabledColor = Color(0.5f, 0.5f, 0.5f, 1f)

    var textureAssetName: String? = null
    var textureScale = 1f
    var cornerRadius = ScaledValue.of(0f)
    var xOrigin = 0.5f
    var yOrigin = 0.5f

    var pressedIconCharacter: String? = null
    var iconFontName: String? = null
    var iconCharacter: String? = null
    var iconSize = ScaledValue.of(15f)

    private var onClickedCallback: (Button) -> Unit = { }
    private var onMouseEnterCallback: (Button) -> Unit = { }
    private var onMouseLeaveCallback: (Button) -> Unit = { }
    private var isMouseOver = false

    override fun onMouseEnter(engine: PulseEngine)
    {
        onMouseEnterCallback(this)
    }

    override fun onMouseLeave(engine: PulseEngine)
    {
        engine.input.setCursorType(CursorType.ARROW)
        onMouseLeaveCallback(this)
    }

    override fun onMouseClicked(engine: PulseEngine)
    {
        if (disabled) return

        if (toggleButton)
            isPressed = !isPressed

       onClickedCallback(this)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        isMouseOver = engine.input.hasHoverFocus(area) && mouseInsideArea

        if (!disabled && isMouseOver)
        {
            engine.input.setCursorType(CursorType.HAND)
        }
    }

    fun setOnClicked(callback: (Button) -> Unit)    { onClickedCallback = callback    }
    fun setOnMouseEnter(callback: (Button) -> Unit) { onMouseEnterCallback = callback }
    fun setOnMouseLeave(callback: (Button) -> Unit) { onMouseLeaveCallback = callback }

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        val bgColor = if (!disabled && isMouseOver) bgHoverColor else bgColor
        val color = when {
            disabled -> disabledColor
            isMouseOver && isPressed -> activeHoverColor ?: activeColor
            isMouseOver -> hoverColor
            isPressed -> activeColor
            else -> color
        }

        if (bgColor.alpha != 0f)
        {
            surface.setDrawColor(bgColor)
            surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value, cornerRadius = cornerRadius.value)
        }

        val character = if (isPressed) pressedIconCharacter ?: iconCharacter else iconCharacter
        if (iconFontName != null && character != null)
        {
            val iconFont = engine.asset.getOrNull<Font>(iconFontName!!) ?: return
            surface.setDrawColor(color)
            surface.drawText(
                text = character,
                x = x.value + width.value * xOrigin,
                y = y.value + height.value * yOrigin,
                font = iconFont,
                fontSize = iconSize.value,
                xOrigin = 0.5f,
                yOrigin = 0.5f
            )
            return
        }

        val texture = textureAssetName?.let { engine.asset.getOrNull<Texture>(it) }
        if (texture != null)
        {
            var texWidth = width.value
            var texHeight = height.value
            val xCenter = x.value + width.value * xOrigin
            val yCenter = y.value + height.value * yOrigin

            if (texture.width > texture.height)
                texHeight = (width.value / texture.width) * texture.height
            else
                texWidth = (height.value / texture.height) * texture.width

            texWidth *= textureScale
            texHeight *= textureScale

            surface.setDrawColor(color)
            surface.drawTexture(texture, xCenter, yCenter, texWidth, texHeight, 0f, 0.5f, 0.5f, cornerRadius.value)
            return
        }

        // Draw filled shape as fallback
        surface.setDrawColor(color)
        surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value, cornerRadius = cornerRadius.value)
    }
}