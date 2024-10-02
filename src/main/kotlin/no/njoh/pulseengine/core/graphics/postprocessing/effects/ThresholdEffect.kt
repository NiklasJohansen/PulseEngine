package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.postprocessing.SinglePassEffect

class ThresholdEffect(
    override val name: String,
    var brightnessThreshold: Float = 0.5f
) : SinglePassEffect() {

    override fun loadShaderProgram(): ShaderProgram =
        ShaderProgram.create(
            vertexShaderFileName = "/pulseengine/shaders/effects/brightnessThreshold.vert",
            fragmentShaderFileName = "/pulseengine/shaders/effects/brightnessThreshold.frag"
        )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("threshold", brightnessThreshold)
        renderer.drawTextures(inTextures)
        fbo.release()

        return fbo.getTextures()
    }
}