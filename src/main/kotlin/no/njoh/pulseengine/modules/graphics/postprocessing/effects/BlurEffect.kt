package no.njoh.pulseengine.modules.graphics.postprocessing.effects

import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.api.ShaderProgram
import no.njoh.pulseengine.modules.graphics.postprocessing.MultiPassEffect
import kotlin.math.max

class BlurEffect(
    var radius: Float = 0.5f,
    var blurPasses: Int = 2
) : MultiPassEffect(2) {

    override fun loadShaderPrograms(): List<ShaderProgram> =
        listOf(
            ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/effects/blurVertical.vert",
                fragmentShaderFileName = "/pulseengine/shaders/effects/blur.frag"
            ),
            ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/effects/blurHorizontal.vert",
                fragmentShaderFileName = "/pulseengine/shaders/effects/blur.frag"
            )
        )

    override fun applyEffect(texture: Texture): Texture
    {
        blurPasses = max(0,  blurPasses)
        var tex = texture
        for (i in 0 until blurPasses)
            tex = applyBlurPass(tex,radius * (1f + i))
        return tex
    }

    private fun applyBlurPass(texture: Texture, radius: Float): Texture
    {
        fbo[0].bind()
        fbo[0].clear()
        programs[0].bind()
        programs[0].setUniform("radius", radius)
        renderers[0].render(texture)
        fbo[0].release()

        val firstPassTexture = fbo[0].getTexture() ?: return texture

        fbo[1].bind()
        fbo[1].clear()
        programs[1].bind()
        programs[1].setUniform("radius", radius)
        renderers[1].render(firstPassTexture)
        fbo[1].release()

        return fbo[1].getTexture() ?: texture
    }
}