package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.postprocessing.SinglePassEffect

class VignetteEffect(
    override val name: String,
    var strength: Float = 0.2f
) : SinglePassEffect() {

    override fun loadShaderProgram(): ShaderProgram =
         ShaderProgram.create(
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
        renderer.drawTextures(inTextures)
        fbo.release()

        return fbo.getTextures()
    }
}
