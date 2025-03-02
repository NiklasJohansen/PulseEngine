package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BloomEffect
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.annotations.TexRef

@Name("Bloom")
class BloomSystem : SceneSystem()
{
    @Prop(i=0, min=0f)          var intensity         = 0.7f
    @Prop(i=1, min=0f)          var threshold         = 1.3f
    @Prop(i=2, min=0f, max=1f)  var thresholdSoftness = 0.7f
    @Prop(i=3, min=0f, max=1f)  var radius            = 0.001f
    @Prop(i=4, min=0f)          var dirtIntensity     = 1f
    @Prop(i=5) @TexRef          var dirtTexture       = ""
    @Prop(i=6)                  var targetSurface     = "main"

    private var lastTargetSurface = targetSurface

    override fun onCreate(engine: PulseEngine)
    {
        val surface = engine.gfx.getSurface(targetSurface) ?: return
        surface.deletePostProcessingEffect(EFFECT_NAME)
        surface.addPostProcessingEffect(BloomEffect(EFFECT_NAME, EFFECT_ORDER))
    }

    override fun onUpdate(engine: PulseEngine)
    {
        if (targetSurface != lastTargetSurface)
        {
            engine.gfx.getSurface(lastTargetSurface)?.deletePostProcessingEffect(EFFECT_NAME)
            lastTargetSurface = targetSurface
        }

        val surface = engine.gfx.getSurface(targetSurface) ?: return
        val effect = surface.getPostProcessingEffect<BloomEffect>()
        if (effect == null)
        {
            onCreate(engine)
            return
        }

        effect.threshold = threshold
        effect.thresholdSoftness = thresholdSoftness
        effect.intensity = intensity
        effect.radius = radius
        effect.dirtIntensity = dirtIntensity
        effect.dirtTextureName = dirtTexture
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (enabled) onCreate(engine) else onDestroy(engine)
    }

    override fun onDestroy(engine: PulseEngine)
    {
        engine.gfx.getSurface(targetSurface)?.deletePostProcessingEffect(EFFECT_NAME)
    }

    companion object
    {
        var EFFECT_NAME  = "bloom"
        var EFFECT_ORDER = 50
    }
}