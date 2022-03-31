package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.modules.lighting.NormalMapRenderPassTarget

open class Backdrop : SceneEntity(), NormalMapRenderPassTarget
{
    var textureName: String = "ball"
    override var normalMapName = ""

    init { setNot(DISCOVERABLE) }

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(engine.asset.getSafe(textureName) ?: Texture.BLANK, x, y, width, height, rotation, xOrigin = 0.5f, yOrigin = 0.5f)
    }
}