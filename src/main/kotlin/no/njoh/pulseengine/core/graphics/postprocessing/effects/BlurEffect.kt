package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.postprocessing.MultiPassEffect
import kotlin.math.max

class BlurEffect(
    override val name: String,
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

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        blurPasses = max(0,  blurPasses)
        var tex = inTextures
        for (i in 0 until blurPasses)
            tex = applyBlurPass(tex, radius * (1f + i))
        return tex
    }

    private fun applyBlurPass(textures: List<Texture>, radius: Float): List<Texture>
    {
        fbo[0].bind()
        fbo[0].clear()
        programs[0].bind()
        programs[0].setUniform("radius", radius)
        renderers[0].drawTextures(textures)
        fbo[0].release()

        val firstPassTexture = fbo[0].getTextures()

        fbo[1].bind()
        fbo[1].clear()
        programs[1].bind()
        programs[1].setUniform("radius", radius)
        renderers[1].drawTextures(firstPassTexture)
        fbo[1].release()

        return fbo[1].getTextures()
    }
}