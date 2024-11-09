package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.postprocessing.BaseEffect
import kotlin.math.max

class BlurEffect(
    override val name: String,
    var radius: Float = 0.5f,
    var blurPasses: Int = 2
) : BaseEffect(numFrameBufferObjects = 2) {

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
        frameBuffers[0].bind()
        frameBuffers[0].clear()
        programs[0].bind()
        programs[0].setUniform("radius", radius)
        programs[0].setUniformSampler("s_texture", textures[0])
        renderers[0].draw()
        frameBuffers[0].release()

        val firstPassTexture = frameBuffers[0].getTextures()

        frameBuffers[1].bind()
        frameBuffers[1].clear()
        programs[1].bind()
        programs[1].setUniform("radius", radius)
        programs[1].setUniformSampler("s_texture", firstPassTexture[0])
        renderers[1].draw()
        frameBuffers[1].release()

        return frameBuffers[1].getTextures()
    }
}