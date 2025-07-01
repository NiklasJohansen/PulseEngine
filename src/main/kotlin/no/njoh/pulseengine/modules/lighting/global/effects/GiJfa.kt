package no.njoh.pulseengine.modules.lighting.global.effects

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.api.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.CLAMP_TO_EDGE
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BaseEffect
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.modules.lighting.global.effects.GiJfa.*
import kotlin.math.*

class GiJfa(
    private val mode: JfaMode,
    override val name: String = "gi_jfa",
    override val order: Int = 2
) : BaseEffect(
    TextureDescriptor(mode.format, filter = NEAREST, wrapping = CLAMP_TO_EDGE),
    TextureDescriptor(mode.format, filter = NEAREST, wrapping = CLAMP_TO_EDGE)
) {
    private var outTextures: MutableList<RenderTexture> = mutableListOf()

    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/global/base.vert")),
        engine.asset.loadNow(FragmentShader("/pulseengine/shaders/lighting/global/jfa.frag"))
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        if (outTextures.isEmpty())
            outTextures = mutableListOf(inTextures[0])

        var seedTex = inTextures[0]
        val maxSize = max(seedTex.width, seedTex.height)
        val numPasses = ceil(log2(maxSize.toFloat())).toInt()

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("computeInternal", mode == JfaMode.EXTERNAL_INTERNAL)
        program.setUniform("resolution", seedTex.width, seedTex.height)

        for (i in 0 until numPasses)
        {
            val offset = 2f.pow(numPasses - i - 1).toInt()
            GpuProfiler.measure({ "PASS #" plus i plus " (" plus offset plus "px)" })
            {
                outTextures[0] = fbo.getTexture(i % 2)
                fbo.attachOutputTexture(outTextures[0])
                program.setUniform("offset", offset)
                program.setUniformSampler("seedTex", seedTex)
                renderer.draw()
                seedTex = outTextures[0]
            }
        }

        fbo.release()

        return outTextures
    }

    override fun getTexture(index: Int) = outTextures.getOrNull(index)

    enum class JfaMode(val format: TextureFormat)
    {
        EXTERNAL(RG16I),
        EXTERNAL_INTERNAL(RGBA16I),
    }
}

class GiJfaSeed(
    private val mode: JfaMode,
    private val sceneSurfaceName: String,
    override val name: String = "gi_jfa_seed",
    override val order: Int = 1,
) : BaseEffect(
    TextureDescriptor(format = mode.format, filter = NEAREST),
) {
    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/global/base.vert")),
        engine.asset.loadNow(FragmentShader("/pulseengine/shaders/lighting/global/jfa_seed.frag"))
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        val sceneSurface = engine.gfx.getSurface(sceneSurfaceName) ?: return inTextures
        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniformSampler("sceneTex", sceneSurface.getTexture())
        renderer.draw()
        fbo.release()
        return fbo.getTextures()
    }
}