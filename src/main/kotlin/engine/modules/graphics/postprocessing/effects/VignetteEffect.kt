package engine.modules.graphics.postprocessing.effects

import engine.data.Texture
import engine.modules.graphics.ShaderProgram
import engine.modules.graphics.postprocessing.SinglePassEffect

class VignetteEffect : SinglePassEffect()
{
    override fun acquireShaderProgram(): ShaderProgram =
         ShaderProgram.create(
            vertexShaderFileName = "/engine/shaders/effects/vignette.vert",
            fragmentShaderFileName = "/engine/shaders/effects/vignette.frag"
        )

    override fun applyEffect(texture: Texture): Texture
    {
        fbo.bind()
        shaderProgram.use()
        shaderProgram.setUniform("resolution", texture.width.toFloat(), texture.height.toFloat())
        renderer.render(texture)
        fbo.release()

        return fbo.texture
    }
}
