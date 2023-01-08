package no.njoh.pulseengine.modules.gui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.input.CursorType
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.modules.gui.Position
import no.njoh.pulseengine.modules.gui.Size
import no.njoh.pulseengine.modules.gui.UiElement

open class Button(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    var toggleButton = false
    var isPressed = false

    var bgColor = Color.BLANK
    var bgHoverColor = Color.BLANK
    var color = Color.BLANK
    var hoverColor = Color.BLANK
    var activeColor = Color.BLANK
    var activeHoverColor: Color? = null

    var texture: Texture? = null
    var textureScale = 1f
    var cornerRadius = 0f
    var xOrigin = 0.5f
    var yOrigin = 0.5f

    var iconFontName: String? = null
    var iconSize = 15f
    var iconCharacter: String? = null
    var pressedIconCharacter: String? = null

    private var onClickedCallback: (Button) -> Unit = { }
    private var isMouseOver = false

    override fun onMouseLeave(engine: PulseEngine)
    {
        engine.input.setCursor(CursorType.ARROW)
    }

    override fun onMouseClicked(engine: PulseEngine)
    {
        if (toggleButton)
            isPressed = !isPressed

        onClickedCallback(this)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        isMouseOver = engine.input.hasHoverFocus(area) && mouseInsideArea

        if (isMouseOver)
        {
            engine.input.setCursor(CursorType.HAND)
        }
    }

    fun setOnClicked(callback: (Button) -> Unit)
    {
        this.onClickedCallback = callback
    }

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        val bgColor = if (isMouseOver) bgHoverColor else bgColor
        val color = when {
            isMouseOver && isPressed -> activeHoverColor ?: activeColor
            isMouseOver -> hoverColor
            isPressed -> activeColor
            else -> color
        }

        if (bgColor.alpha != 0f)
        {
            surface.setDrawColor(bgColor.red, bgColor.green, bgColor.blue, bgColor.alpha)
            surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value, cornerRadius = cornerRadius)
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
                fontSize = iconSize,
                xOrigin = 0.5f,
                yOrigin = 0.5f
            )
        }
        else if (texture != null)
        {
            val tex = texture!!
            var texWidth = width.value
            var texHeight = height.value
            val xCenter = x.value + width.value * xOrigin
            val yCenter = y.value + height.value * yOrigin

            if (tex.width > tex.height)
                texHeight = (width.value / tex.width) * tex.height
            else
                texWidth = (height.value / tex.height) * tex.width

            texWidth *= textureScale
            texHeight *= textureScale

            surface.setDrawColor(color.red, color.green, color.blue, color.alpha)
            surface.drawTexture(tex, xCenter, yCenter, texWidth, texHeight, 0f, 0.5f, 0.5f, cornerRadius)
        }
        else
        {
            surface.setDrawColor(color.red, color.green, color.blue, color.alpha)
            surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value, cornerRadius = cornerRadius)
        }
    }
}