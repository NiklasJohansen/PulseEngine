package no.njoh.pulseengine.modules.graphics.postprocessing.effects

import no.njoh.pulseengine.data.Texture
import no.njoh.pulseengine.modules.graphics.ShaderProgram
import no.njoh.pulseengine.modules.graphics.postprocessing.SinglePassEffect

class VignetteEffect(
    var strength: Float = 0.2f
) : SinglePassEffect() {

    override fun loadShaderProgram(): ShaderProgram =
         ShaderProgram.create(
            vertexShaderFileName = "/pulseengine/shaders/effects/vignette.vert",
            fragmentShaderFileName = "/pulseengine/shaders/effects/vignette.frag"
        )

    override fun applyEffect(texture: Texture): Texture
    {
        fbo.bind()
        program.bind()
        program.setUniform("resolution", texture.width.toFloat(), texture.height.toFloat())
        program.setUniform("strength", strength)
        renderer.render(texture)
        fbo.release()

        return fbo.texture
    }
}
