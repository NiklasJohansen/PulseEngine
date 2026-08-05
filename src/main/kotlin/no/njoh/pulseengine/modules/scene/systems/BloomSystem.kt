package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.AssetHandle
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.postprocessing.BloomEffect
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

@Name("Bloom")
class BloomSystem : SceneSystem()
{
    @Prop(i=0, min=0f)          var intensity          = 0.7f
    @Prop(i=1, min=0f)          var threshold          = 1.3f
    @Prop(i=2, min=0f, max=1f)  var thresholdSoftness  = 0.7f
    @Prop(i=3, min=0f, max=1f)  var radius             = 1f
    @Prop(i=4, min=0f)          var lensDirtIntensity  = 1f
    @Prop(i=5)                  var lensDirtTexture    = AssetHandle<Texture>()
    @Prop(i=6)                  var targetSurfaces     = "scene3d"

    private var lastTargetSurfaces = ""
    private var targetSurfaceNames = emptyList<String>()

    override fun onUpdate(engine: PulseEngine)
    {
        if (targetSurfaces != lastTargetSurfaces)
        {
            onDestroy(engine)
            lastTargetSurfaces = targetSurfaces
            targetSurfaceNames = targetSurfaces.split(",").map { it.trim() }
        }

        targetSurfaceNames.forEachFast { updateEffect(engine, it) }
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (!enabled) onDestroy(engine)
    }

    override fun onDestroy(engine: PulseEngine)
    {
        targetSurfaceNames.forEachFast { engine.gfx.getSurface(it)?.deletePostProcessingEffect(EFFECT_NAME) }
    }

    private fun updateEffect(engine: PulseEngine, surfaceName: String)
    {
        val surface = engine.gfx.getSurface(surfaceName) ?: return
        val effect = surface.getPostProcessingEffect<BloomEffect>()
        if (effect == null)
        {
            surface.deletePostProcessingEffect(EFFECT_NAME)
            surface.addPostProcessingEffect(BloomEffect(EFFECT_NAME, EFFECT_ORDER))
            return
        }

        effect.threshold = threshold
        effect.thresholdSoftness = thresholdSoftness
        effect.intensity = intensity
        effect.radius = radius
        effect.lensDirtIntensity = lensDirtIntensity
        effect.lensDirtTexture = lensDirtTexture.name
    }

    companion object
    {
        var EFFECT_NAME  = "bloom"
        var EFFECT_ORDER = 50
    }
}