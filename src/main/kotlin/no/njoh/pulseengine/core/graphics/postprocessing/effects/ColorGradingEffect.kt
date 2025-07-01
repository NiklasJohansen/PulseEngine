package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.CLAMP_TO_EDGE
import no.njoh.pulseengine.core.graphics.postprocessing.effects.ColorGradingEffect.ToneMapper.ACES

class ColorGradingEffect(
    override val name: String = "color_grading",
    override val order: Int = 100,
    var toneMapper: ToneMapper = ACES,
    var lutTexture: String = "",
    var lutIntensity: Float = 1.0f,
    var exposure: Float = 1.0f,
    var contrast: Float = 1f,
    var saturation: Float = 1f,
    var vignette: Float = 0f
) : BaseEffect() {

    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/effects/color_grading.vert")),
        engine.asset.loadNow(FragmentShader("/pulseengine/shaders/effects/color_grading.frag"))
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        val lutTex = engine.asset.getOrNull<Texture>(lutTexture)
        val lutTexIndex = lutTex?.handle?.textureIndex?.toFloat() ?: -1f
        val lutTexArray = engine.gfx.textureBank.getTextureArrayOrDefault(lutTex)

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniformSampler("baseTex", inTextures[0])
        program.setUniformSamplerArray("lutTexArray", lutTexArray, filter = NEAREST, wrapping = CLAMP_TO_EDGE)
        program.setUniform("lutTexCoord", lutTex?.uMax ?: 0f, lutTex?.vMax ?: 0f, lutTexIndex)
        program.setUniform("lutIntensity", lutIntensity)
        program.setUniform("lutSize", lutTex?.height?.toFloat() ?: 0f)
        program.setUniform("toneMapper", toneMapper.value)
        program.setUniform("exposure", exposure)
        program.setUniform("contrast", contrast)
        program.setUniform("saturation", saturation)
        program.setUniform("vignette", vignette)
        renderer.draw()
        fbo.release()
        return fbo.getTextures()
    }

    enum class ToneMapper(val value: Int)
    {
        NONE(-1),
        ACES(0),
        FILMIC(1),
        REINHARD(2),
        UNCHARTED2(3),
        LOTTES(4)
    }
}