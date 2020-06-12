package engine.modules.graphics.postprocessing.effects

import engine.data.Texture
import engine.modules.graphics.ShaderProgram
import engine.modules.graphics.Surface
import engine.modules.graphics.postprocessing.SinglePassEffect

class MultiplyEffect(
    private val baseSurface: Surface
) : SinglePassEffect() {
    override fun loadShaderProgram(): ShaderProgram =
        ShaderProgram.create(
            vertexShaderFileName = "/engine/shaders/effects/textureMultiplyBlend.vert",
            fragmentShaderFileName = "/engine/shaders/effects/textureMultiplyBlend.frag"
        )

    override fun applyEffect(texture: Texture): Texture
    {
        fbo.bind()
        program.bind()
        renderer.render(baseSurface.getTexture(), texture)
        fbo.release()

        return fbo.texture
    }
}