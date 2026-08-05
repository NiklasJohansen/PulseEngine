package no.njoh.pulseengine.modules.lighting.shared

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.AssetHandle
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Spatial2D
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.modules.lighting.shared.NormalMapRenderer.Orientation

/**
 * Rendered by the [DirectLightingSystem] to a separate normal map [Surface] for deferred lighting calculations.
 */
interface NormalMapped2D
{
    @get:Prop("Lighting", 8, desc = "Name of the normal map [Texture] asset.")
    var normalMapTexture: AssetHandle<Texture>

    @get:Prop("Lighting", 9, desc = "The intensity/scale of the normals in the map.")
    var normalMapIntensity: Float

    @get:Prop("Lighting", 10, desc = "The orientation of the normals in the map.")
    var normalMapOrientation: Orientation

    fun onRenderNormalMap(engine: PulseEngine, surface: Surface)
    {
        if (this is SceneEntity && this is Spatial2D && normalMapTexture.name.isNotBlank())
        {
            surface.getRenderer<NormalMapRenderer>()?.drawNormalMap(
                texture = engine.asset.getOrNull(normalMapTexture),
                x = x,
                y = y,
                w = width,
                h = height,
                rot = zRotation,
                xOrigin = 0.5f,
                yOrigin = 0.5f,
                normalScale = normalMapIntensity,
                orientation = normalMapOrientation
            )
        }
    }
}