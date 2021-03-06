package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.Surface2D

open class Backdrop : SceneEntity()
{
    var textureName: String = "ball"

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(engine.asset.getSafe(textureName) ?: Texture.BLANK, x, y, width, height, rotation, xOrigin = 0.5f, yOrigin = 0.5f)
    }
}