package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram

class ThresholdEffect(
    override val name: String,
    override val order: Int,
    var threshold: Float = 0.5f
) : BaseEffect() {

    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/effects/brightness_threshold.vert",
        fragmentShaderFileName = "/pulseengine/shaders/effects/brightness_threshold.frag"
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<Texture>): List<Texture>
    {
        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("threshold", threshold)
        program.setUniformSampler("tex", inTextures[0])
        renderer.draw()
        fbo.release()
        return fbo.getTextures()
    }
}