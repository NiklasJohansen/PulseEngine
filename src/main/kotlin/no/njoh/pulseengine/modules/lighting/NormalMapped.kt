package no.njoh.pulseengine.modules.lighting

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.systems.CustomRenderPassTarget

/**
 * Rendered by the [LightingSystem] to a normal map [Surface2D].
 */
interface NormalMapped : CustomRenderPassTarget
{
    /** Name of the normal map [Texture] asset. */
    var normalMapName: String

    /** The intensity/scale of the normals in the map. */
    var normalMapIntensity: Float

    override fun renderCustomPass(engine: PulseEngine, surface: Surface2D)
    {
        if (this is SceneEntity && normalMapName.isNotBlank())
        {
            val normalMap = engine.asset.getOrNull<Texture>(normalMapName)
            val renderer = surface.getRenderer(NormalMapRenderer::class)
            renderer?.drawNormalMap(normalMap, x, y, width, height, rotation, 0.5f, 0.5f, normalScale = normalMapIntensity)
        }
    }
}