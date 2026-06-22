package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.postprocessing.ColorGradingEffect
import no.njoh.pulseengine.core.graphics.postprocessing.ColorGradingEffect.ToneMapper.ACES
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.annotations.TexRef
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

@Name("Color Grading")
class ColorGradingSystem : SceneSystem()
{
    @Prop(i=0)                 var toneMapper     = ACES
    @Prop(i=1) @TexRef         var lutTexture     = ""
    @Prop(i=2, min=0f, max=1f) var lutIntensity   = 1f
    @Prop(i=3, min=0f)         var exposure       = 1f
    @Prop(i=4)                 var contrast       = 1f
    @Prop(i=5, min=0f)         var saturation     = 1f
    @Prop(i=6, min=0f)         var vignette       = 0f
    @Prop(i=7)                 var targetSurfaces = "scene3d"

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
        val effect = surface.getPostProcessingEffect<ColorGradingEffect>()
        if (effect == null)
        {
            surface.deletePostProcessingEffect(EFFECT_NAME)
            surface.addPostProcessingEffect(ColorGradingEffect(EFFECT_NAME, EFFECT_ORDER))
            return
        }

        effect.lutTexture   = lutTexture
        effect.lutIntensity = lutIntensity
        effect.toneMapper   = toneMapper
        effect.exposure     = exposure
        effect.contrast     = contrast
        effect.saturation   = saturation
        effect.vignette     = vignette
    }
    
    companion object
    {
        var EFFECT_NAME  = "color_grading"
        var EFFECT_ORDER = 100
    }
}