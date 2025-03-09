package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import kotlin.math.max

class BlurEffect(
    override val name: String,
    override val order: Int,
    var radius: Float = 0.5f,
    var blurPasses: Int = 2
) : BaseEffect(
    TextureDescriptor(), // Horizontal blur
    TextureDescriptor()  // Vertical blur
) {
    override fun loadShaderPrograms(engine: PulseEngineInternal) = listOf(
        ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/effects/blur_vertical.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/effects/blur.frag"))
        ),
        ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/effects/blur_horizontal.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/effects/blur.frag"))
        )
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<Texture>): List<Texture>
    {
        val vProgram = programs[0]
        val hProgram = programs[1]
        val vTex = fbo.getTexture(1)!!
        val hTex = fbo.getTexture(0)!!
        var currentTex = inTextures[0]

        fbo.bind()
        fbo.clear()

        for (i in 0 until max(0,  blurPasses))
        {
            val radius = radius * (1f + i)

            vProgram.bind()
            vProgram.setUniform("radius", radius)
            vProgram.setUniformSampler("tex", currentTex)
            fbo.attachOutputTexture(vTex)
            renderer.draw()

            hProgram.bind()
            hProgram.setUniform("radius", radius)
            hProgram.setUniformSampler("tex", vTex)
            fbo.attachOutputTexture(hTex)
            renderer.draw()

            currentTex = hTex
        }

        fbo.release()

        return fbo.getTextures()
    }
}