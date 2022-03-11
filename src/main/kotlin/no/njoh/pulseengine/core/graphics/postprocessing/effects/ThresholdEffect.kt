package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.postprocessing.SinglePassEffect

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
        fbo.clear()
        program.bind()
        program.setUniform("threshold", brightnessThreshold)
        renderer.render(texture)
        fbo.release()

        return fbo.getTexture() ?: texture
    }
}