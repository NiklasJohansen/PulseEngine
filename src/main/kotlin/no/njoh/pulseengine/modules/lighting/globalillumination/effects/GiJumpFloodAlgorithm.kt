package no.njoh.pulseengine.modules.lighting.globalillumination.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.api.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BaseEffect
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow

class GiJfa(override val name: String = "jfa") : BaseEffect(
    TextureDescriptor(filter = NEAREST, format = RGBA32F, attachment = COLOR_TEXTURE_0),
    numFrameBufferObjects = 2
) {
    private var outputTextures: List<Texture> = emptyList()

    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/gi/default.vert",
        fragmentShaderFileName = "/pulseengine/shaders/gi/jfa.frag"
    )

    override fun getTexture(index: Int) = outputTextures.getOrNull(index)

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        outputTextures = inTextures
        val width = inTextures[0].width.toFloat()
        val height = inTextures[0].height.toFloat()
        val passes = ceil(log2(max(width, height))).toInt()
        val program = programs[0]
        program.bind()
        program.setUniform("resolution", width, height)

        for (i in 0 until passes)
        {
            GpuProfiler.beginMeasure { "Pass #" plus i }

            val fbo = frameBuffers[i % 2]
            fbo.bind()
            fbo.clear()
            program.setUniform("uOffset", 2f.pow(passes - i - 1f))
            program.setUniformSampler("seedTex", outputTextures[0])
            renderer.draw()
            fbo.release()
            outputTextures = fbo.getTextures()

            GpuProfiler.endMeasure()
        }

        return outputTextures
    }
}

class GiJfaSeed(
    private val sceneSurfaceName: String,
    override val name: String = "jfa_seed"
) : BaseEffect(
    TextureDescriptor(filter = NEAREST, format = RGBA32F, attachment = COLOR_TEXTURE_0),
) {
    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/gi/default.vert",
        fragmentShaderFileName = "/pulseengine/shaders/gi/jfa_seed.frag"
    )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
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