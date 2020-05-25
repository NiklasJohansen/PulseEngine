package engine.modules.graphics.postprocessing.effects

import engine.data.Texture
import engine.modules.graphics.ShaderProgram
import engine.modules.graphics.postprocessing.MultiPassEffect
import kotlin.math.max

class BlurEffect : MultiPassEffect(2)
{
    var radius = 0.5f
    var blurPasses = 1
        set(value) { field = max(0, value) }

    override fun acquireShaderPrograms(): List<ShaderProgram> =
        listOf(
            ShaderProgram.create(
                vertexShaderFileName = "/engine/shaders/effects/blurVertical.vert",
                fragmentShaderFileName = "/engine/shaders/effects/blur.frag"
            ),
            ShaderProgram.create(
                vertexShaderFileName = "/engine/shaders/effects/blurHorizontal.vert",
                fragmentShaderFileName = "/engine/shaders/effects/blur.frag"
            )
        )

    override fun applyEffect(texture: Texture): Texture
    {
        var tex = texture
        for(i in 0 until blurPasses)
            tex = applyBlurPass(tex)
        return tex
    }

    private fun applyBlurPass(texture: Texture): Texture
    {
        fbo[0].bind()
        program[0].use()
        program[0].setUniform("radius", radius)
        render[0].render(texture)
        fbo[0].release()

        fbo[1].bind()
        program[1].use()
        program[1].setUniform("radius", radius)
        render[1].render(fbo[0].texture)
        fbo[1].release()

        return fbo[1].texture
    }
}