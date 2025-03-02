package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.postprocessing.effects.ColorGradingEffect.ToneMapper.ACES

class ColorGradingEffect(
    override val name: String,
    override val order: Int,
    var toneMapper: ToneMapper = ACES,
    var exposure: Float = 1.0f,
    var contrast: Float = 1f,
    var saturation: Float = 0.5f
) : BaseEffect() {

    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/effects/color_grading.vert",
        fragmentShaderFileName = "/pulseengine/shaders/effects/color_grading.frag"
    )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniformSampler("tex", inTextures[0])
        program.setUniform("toneMapper", toneMapper.value)
        program.setUniform("exposure", exposure)
        program.setUniform("contrast", contrast)
        program.setUniform("saturation", saturation)
        renderer.draw()
        fbo.release()
        return fbo.getTextures()
    }

    enum class ToneMapper(val value: Int)
    {
        NONE(-1),
        ACES(0),
        FILMIC(1),
        REINHARD(2),
        UNCHARTED2(3),
        LOTTES(4)
    }
}