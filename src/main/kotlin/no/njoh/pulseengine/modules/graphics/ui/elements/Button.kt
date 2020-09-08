package no.njoh.pulseengine.modules.graphics.ui.elements

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.CursorType
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Size


open class Button(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    var toggleButton = false
    var state = false

    var bgColor = Color(1f, 1f, 1f, 0f)
    var bgColorHover = Color(1f, 1f, 1f, 0f)
    var color = Color(1f, 1f, 1f)
    var colorHover = Color(1f, 1f, 1f)
    var colorActive = Color(1f, 1f, 1f)
    var texture: Texture? = null
    var textureScale = 1f

    private var onClickedCallback: (Button) -> Unit = { }

    override fun onMouseLeave(engine: PulseEngine)
    {
        engine.input.setCursor(CursorType.ARROW)
    }

    override fun onMouseClicked(engine: PulseEngine)
    {
        if (toggleButton)
            state = !state

        onClickedCallback(this)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        if (mouseInsideArea)
        {
            engine.input.setCursor(CursorType.HAND)
        }
    }

    fun setOnClicked(callback: (Button) -> Unit)
    {
        this.onClickedCallback = callback
    }

    override fun onRender(surface: Surface2D)
    {
        val color = if (toggleButton && state) colorActive else if (mouseInsideArea) colorHover else color
        val bgColor = if (mouseInsideArea) bgColorHover else bgColor

        surface.setDrawColor(bgColor.red, bgColor.green, bgColor.blue, bgColor.alpha)
        surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value)

        texture?.let { tex ->
            var texWidth = width.value
            var texHeight = height.value
            val xCenter = x.value + width.value / 2f
            val yCenter = y.value + height.value / 2f

            if (tex.width > tex.height)
                texHeight = (width.value / tex.width) * tex.height
            else
                texWidth = (height.value / tex.height) * tex.width

            texWidth *= textureScale
            texHeight *= textureScale

            surface.setDrawColor(color.red, color.green, color.blue, color.alpha)
            surface.drawTexture(tex, xCenter, yCenter, texWidth, texHeight, xOrigin = 0.5f, yOrigin = 0.5f)
        } ?: run {
            surface.setDrawColor(color.red, color.green, color.blue, color.alpha)
            surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value)
        }
    }
}