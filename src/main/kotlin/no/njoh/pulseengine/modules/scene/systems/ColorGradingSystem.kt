package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.postprocessing.effects.ColorGradingEffect
import no.njoh.pulseengine.core.graphics.postprocessing.effects.ColorGradingEffect.ToneMapper.ACES
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop

@Name("Color Grading")
class ColorGradingSystem : SceneSystem()
{
    @Prop(i=0)         var toneMapper    = ACES
    @Prop(i=1, min=0f) var exposure      = 1f
    @Prop(i=2, min=0f) var contrast      = 1f
    @Prop(i=3, min=0f) var saturation    = 1f
    @Prop(i=4, min=0f) var vignette      = 0f
    @Prop(i=5)         var targetSurface = "main"

    private var lastTargetSurface = targetSurface

    override fun onCreate(engine: PulseEngine)
    {
        val surface = engine.gfx.getSurface(targetSurface) ?: return
        surface.deletePostProcessingEffect(EFFECT_NAME)
        surface.addPostProcessingEffect(ColorGradingEffect(EFFECT_NAME, EFFECT_ORDER))
    }

    override fun onUpdate(engine: PulseEngine)
    {
        if (targetSurface != lastTargetSurface)
        {
            engine.gfx.getSurface(lastTargetSurface)?.deletePostProcessingEffect(EFFECT_NAME)
            lastTargetSurface = targetSurface
        }

        val surface = engine.gfx.getSurface(targetSurface) ?: return
        val effect = surface.getPostProcessingEffect<ColorGradingEffect>()
        if (effect == null)
        {
            onCreate(engine)
            return
        }

        effect.toneMapper = toneMapper
        effect.exposure = exposure
        effect.contrast = contrast
        effect.saturation = saturation
        effect.vignette = vignette
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
        var EFFECT_NAME  = "color_grading"
        var EFFECT_ORDER = 100
    }
}