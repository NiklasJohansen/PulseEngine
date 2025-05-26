package no.njoh.pulseengine.modules.gui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.surface.Surface
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

    var bgColor = Color.BLANK
    var tint = Color.WHITE
    var textureAssetName: String? = null
    var texture: Texture? = null
    var renderTexture: RenderTexture? = null
    var textureScale = 1f

    override fun onUpdate(engine: PulseEngine) { }

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        var texWidth = 0
        var texHeight = 0
        var finalWidth = width.value
        var finalHeight = height.value
        val xCenter = x.value + width.value / 2f
        val yCenter = y.value + height.value / 2f

        if (renderTexture != null) 
        {
            texWidth = renderTexture!!.width
            texHeight = renderTexture!!.height
        } 
        else if (texture != null || textureAssetName != null) 
        {
            texture = texture ?: textureAssetName?.let { engine.asset.getOrNull<Texture>(it) } ?: return
            texWidth = texture!!.width
            texHeight = texture!!.height
        }

        // Calculate texture dimensions to fit inside image size
        if (texWidth > texHeight)
        {
            finalHeight = (width.value / texWidth) * texHeight
            if (finalHeight > height.value)
            {
                finalHeight = height.value
                finalWidth = (height.value / texHeight) * texWidth
            }
        }
        else
        {
            finalWidth = (height.value / texHeight) * texWidth
            if (finalWidth > width.value)
            {
                finalWidth = width.value
                finalHeight = (width.value / texWidth) * texHeight
            }
        }

        // Scale texture size
        finalWidth *= textureScale
        finalHeight *= textureScale

        // Draw background and image texture
        if (renderTexture != null)
        {
            surface.setDrawColor(bgColor)
            surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value)
            surface.setDrawColor(tint)
            surface.drawTexture(renderTexture!!, xCenter, yCenter, finalWidth, finalHeight, xOrigin = 0.5f, yOrigin = 0.5f)
        }
        else if (texture != null)
        {
            surface.setDrawColor(bgColor)
            surface.drawTexture(RenderTexture.BLANK, x.value, y.value, width.value, height.value)
            surface.setDrawColor(tint)
            surface.drawTexture(texture!!, xCenter, yCenter, finalWidth, finalHeight, xOrigin = 0.5f, yOrigin = 0.5f)
        }
    }
}