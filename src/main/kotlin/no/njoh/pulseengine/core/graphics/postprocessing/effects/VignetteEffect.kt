package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.postprocessing.BaseEffect

class VignetteEffect(
    override val name: String,
    var strength: Float = 0.2f
) : BaseEffect() {

    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/effects/vignette.vert",
        fragmentShaderFileName = "/pulseengine/shaders/effects/vignette.frag"
    )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("resolution", inTextures[0].width.toFloat(), inTextures[0].height.toFloat())
        program.setUniform("strength", strength)
        program.setUniformSampler("tex", inTextures[0])
        renderer.draw()
        fbo.release()
        return fbo.getTextures()
    }
}
