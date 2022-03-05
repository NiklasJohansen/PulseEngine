package no.njoh.pulseengine.modules.gui.elements

import no.njoh.pulseengine.core.PulseEngine
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
    var state = false

    var bgColor = Color(1f, 1f, 1f, 0f)
    var bgHoverColor = Color(1f, 1f, 1f, 0f)
    var color = Color(1f, 1f, 1f)
    var hoverColor = Color(1f, 1f, 1f)
    var activeColor = Color(1f, 1f, 1f)
    var texture: Texture? = null
    var textureScale = 1f
    var cornerRadius = 0f

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
        val color = if (toggleButton && state) activeColor else if (mouseInsideArea) hoverColor else color
        val bgColor = if (mouseInsideArea) bgHoverColor else bgColor

        surface.setDrawColor(bgColor.red, bgColor.green, bgColor.blue, bgColor.alpha)
        surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value, cornerRadius = cornerRadius)

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
            surface.drawTexture(tex, xCenter, yCenter, texWidth, texHeight, 0f, 0.5f, 0.5f, cornerRadius)
        } ?: run {
            surface.setDrawColor(color.red, color.green, color.blue, color.alpha)
            surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value, cornerRadius = cornerRadius)
        }
    }
}