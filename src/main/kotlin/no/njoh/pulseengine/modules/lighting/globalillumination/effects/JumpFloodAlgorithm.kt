package no.njoh.pulseengine.modules.lighting.globalillumination.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA32F
import no.njoh.pulseengine.core.graphics.postprocessing.MultiPassEffect
import no.njoh.pulseengine.core.graphics.postprocessing.SinglePassEffect
import no.njoh.pulseengine.modules.lighting.globalillumination.GlobalIlluminationSystem
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow

class Jfa(override val name: String = "jfa") : MultiPassEffect(
    numberOfRenderPasses = 2,
    textureFilter = NEAREST,
    textureFormat = RGBA32F
) {
    private var outputTextures: List<Texture> = emptyList()

    override fun loadShaderPrograms() = listOf(ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/gi/default.vert",
        fragmentShaderFileName = "/pulseengine/shaders/gi/jfa.frag"
    ))

    override fun getTexture(index: Int) = outputTextures.getOrNull(index)

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        outputTextures = inTextures
        val width = inTextures[0].width.toFloat()
        val height = inTextures[0].height.toFloat()
        val passes = ceil(log2(max(width, height))).toInt()
        val program = programs[0]

        for (i in 0 until passes)
        {
            val fbo = fbo[i % 2]
            fbo.bind()
            fbo.clear()
            program.bind()
            program.setUniform("uOffset", 2f.pow(passes - i - 1f))
            program.setUniform("index", i)
            program.setUniform("resolution", width, height)
            renderers[0].drawTextures(outputTextures)
            program.unbind()
            fbo.release()
            outputTextures = fbo.getTextures()
        }

        return outputTextures
    }
}

class JfaSeed(override val name: String = "jfa_seed") : SinglePassEffect(
    textureFilter = NEAREST,
    textureFormat = RGBA32F
) {
    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/gi/default.vert",
        fragmentShaderFileName = "/pulseengine/shaders/gi/jfa_seed.frag"
    )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        val lightSystem = engine.scene.getSystemOfType<GlobalIlluminationSystem>() ?: return inTextures
        val sceneSurface = lightSystem.getSceneSurface(engine) ?: return inTextures

        fbo.bind()
        fbo.clear()
        program.bind()
        renderer.drawTexture(sceneSurface.getTexture())
        fbo.release()
        return fbo.getTextures()
    }
}