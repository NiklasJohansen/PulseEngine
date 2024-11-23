package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram

class ThresholdEffect(
    override val name: String,
    var brightnessThreshold: Float = 0.5f
) : BaseEffect() {

    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/effects/brightnessThreshold.vert",
        fragmentShaderFileName = "/pulseengine/shaders/effects/brightnessThreshold.frag"
    )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("threshold", brightnessThreshold)
        program.setUniformSampler("tex", inTextures[0])
        renderer.draw()
        fbo.release()
        return fbo.getTextures()
    }
}