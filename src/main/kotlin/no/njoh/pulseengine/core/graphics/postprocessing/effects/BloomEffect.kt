package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.postprocessing.BaseEffect

class BloomEffect(
    override val name: String,
    var threshold: Float = 0.5f,
    var exposure: Float = 2.2f,
    var blurRadius: Float = 0.5f,
    var blurPasses: Int = 2
) : BaseEffect() {

    private val blurEffect = BlurEffect(name + "_blur", blurRadius, blurPasses)
    private val thresholdEffect = ThresholdEffect(name + "_threshold", threshold)

    override fun init()
    {
        super.init()
        blurEffect.init()
        thresholdEffect.init()
    }

    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/effects/textureAddBlend.vert",
        fragmentShaderFileName = "/pulseengine/shaders/effects/textureAddBlend.frag"
    )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        thresholdEffect.brightnessThreshold = threshold
        val brightTextures = thresholdEffect.process(engine, inTextures)

        blurEffect.blurPasses = blurPasses
        blurEffect.radius = blurRadius
        val blurredBrightPass = blurEffect.process(engine, brightTextures)

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("exposure", exposure)
        program.setUniformSampler("texture0", inTextures[0])
        program.setUniformSampler("texture1", blurredBrightPass[0])
        renderer.draw()
        fbo.release()

        return fbo.getTextures()
    }
}