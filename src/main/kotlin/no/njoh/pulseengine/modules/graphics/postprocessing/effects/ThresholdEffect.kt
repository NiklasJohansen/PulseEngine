package no.njoh.pulseengine.modules.graphics.postprocessing.effects

import no.njoh.pulseengine.data.Texture
import no.njoh.pulseengine.modules.graphics.ShaderProgram
import no.njoh.pulseengine.modules.graphics.postprocessing.SinglePassEffect

class ThresholdEffect(
    var brightnessThreshold: Float = 0.5f
) : SinglePassEffect() {

    override fun loadShaderProgram(): ShaderProgram =
        ShaderProgram.create(
            vertexShaderFileName = "/pulseengine/shaders/effects/brightnessThreshold.vert",
            fragmentShaderFileName = "/pulseengine/shaders/effects/brightnessThreshold.frag"
        )

    override fun applyEffect(texture: Texture): Texture
    {
        fbo.bind()
        program.bind()
        program.setUniform("threshold", brightnessThreshold)
        renderer.render(texture)
        fbo.release()

        return fbo.texture
    }
}