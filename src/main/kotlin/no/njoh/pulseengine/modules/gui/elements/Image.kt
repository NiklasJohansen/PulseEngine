package no.njoh.pulseengine.modules.gui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.modules.gui.Position
import no.njoh.pulseengine.modules.gui.Size
import no.njoh.pulseengine.modules.gui.UiElement

open class Image(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    init { focusable = false }

    var bgColor = Color(1f, 1f, 1f, 0f)
    var tint = Color(1f, 1f, 1f)
    var texture: Texture? = null
    var textureAssetName: String? = null
    var textureScale = 1f

    override fun onUpdate(engine: PulseEngine) { }

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        val texture = texture ?: textureAssetName?.let { engine.asset.getOrNull(it) } ?: return
        var texWidth = width.value
        var texHeight = height.value
        val xCenter = x.value + width.value / 2f
        val yCenter = y.value + height.value / 2f

        // Calculate texture dimensions to fit inside image size
        if (texture.width > texture.height)
        {
            texHeight = (width.value / texture.width) * texture.height
            if (texHeight > height.value)
            {
                texHeight = height.value
                texWidth = (height.value / texture.height) * texture.width
            }
        }
        else
        {
            texWidth = (height.value / texture.height) * texture.width
            if (texWidth > width.value)
            {
                texWidth = width.value
                texHeight = (width.value / texture.width) * texture.height
            }
        }

        // Scale texture size
        texWidth *= textureScale
        texHeight *= textureScale

        // Draw background texture
        val bgTexture = if (texture.isBindless) Texture.BLANK else Texture.BLANK_BINDABLE
        surface.setDrawColor(bgColor)
        surface.drawTexture(bgTexture, x.value, y.value, width.value, height.value)

        // Draw image
        surface.setDrawColor(tint)
        surface.drawTexture(texture, xCenter, yCenter, texWidth, texHeight, xOrigin = 0.5f, yOrigin = 0.5f)
    }
}