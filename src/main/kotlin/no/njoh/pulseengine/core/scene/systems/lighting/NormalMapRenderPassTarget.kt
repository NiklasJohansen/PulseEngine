package no.njoh.pulseengine.core.scene.systems.lighting

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.entities.SceneEntity
import no.njoh.pulseengine.core.scene.systems.rendering.CustomRenderPassTarget

interface NormalMapRenderPassTarget : CustomRenderPassTarget
{
    var normalMapName: String

    override fun renderCustomPass(engine: PulseEngine, surface: Surface2D)
    {
        if (this is SceneEntity)
        {
            val normalMap = engine.asset.getSafe<Texture>(normalMapName)
            val dir = if (normalMap != null) 1.0f else 0.5f
            surface.setDrawColor(dir, dir, 1f)
            surface.drawTexture(normalMap ?: Texture.BLANK, x, y, width, height, rotation, 0.5f, 0.5f)
        }
    }
}