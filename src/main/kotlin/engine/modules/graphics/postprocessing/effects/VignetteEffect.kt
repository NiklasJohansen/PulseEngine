package engine.modules.graphics.postprocessing.effects

import engine.data.Texture
import engine.modules.graphics.ShaderProgram
import engine.modules.graphics.postprocessing.SinglePassEffect

class VignetteEffect(
    var strength: Float = 0.2f
) : SinglePassEffect() {

    override fun loadShaderProgram(): ShaderProgram =
         ShaderProgram.create(
            vertexShaderFileName = "/engine/shaders/effects/vignette.vert",
            fragmentShaderFileName = "/engine/shaders/effects/vignette.frag"
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
