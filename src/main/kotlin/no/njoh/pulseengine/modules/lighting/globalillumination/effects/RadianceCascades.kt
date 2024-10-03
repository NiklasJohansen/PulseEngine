package no.njoh.pulseengine.modules.lighting.globalillumination.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.postprocessing.MultiPassEffect
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.lighting.globalillumination.GlobalIlluminationSystem
import kotlin.math.*

class RadianceCascades(override val name: String = "rc") : MultiPassEffect(
    numberOfRenderPasses = 2,
    textureFilter = LINEAR,
    textureFormat = RGBA16F
) {
    private var skyColor = Color()
    private var sunColor = Color()
    private var outTextures = emptyList<Texture>()

    override fun loadShaderPrograms() = listOf(ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/gi/default.vert",
        fragmentShaderFileName = "/pulseengine/shaders/gi/radiance_cascades.frag"
    ))

    override fun getTexture(index: Int) = outTextures.getOrNull(index)

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        val lightSystem = engine.scene.getSystemOfType<GlobalIlluminationSystem>() ?: return inTextures
        val sceneSurface = lightSystem.getSceneSurface(engine) ?: return inTextures
        val sdfSurface = lightSystem.getSdfSurface(engine) ?: return inTextures

        val width = inTextures[0].width.toFloat()
        val height = inTextures[0].height.toFloat()
        val baseRayCount = 4f
        val diagonalSize = sqrt(width * width + height * height)
        val cascadeCount = min(ceil(log2(diagonalSize) / log2(baseRayCount)).toInt() + 1, lightSystem.maxCascades)
        var cascadeIndex = cascadeCount - 1
        val minStepSize = min(1f / width, 1f / height) * 0.5f
        val program = programs[0]

        skyColor.setFrom(lightSystem.skyColor).multiplyRgb(lightSystem.skyIntensity)
        sunColor.setFrom(lightSystem.sunColor).multiplyRgb(lightSystem.sunIntensity)
        outTextures = inTextures

        program.bind()
        program.setUniform("resolution", width, height)
        program.setUniform("skyColor", skyColor)
        program.setUniform("sunColor", sunColor)
        program.setUniform("sunAngle", lightSystem.sunAngle)
        program.setUniform("sunDistance", lightSystem.sunDistance)
        program.setUniform("minStepSize", minStepSize)
        program.setUniform("bilinearFix", lightSystem.bilinearFix)
        program.setUniform("forkFix", lightSystem.forkFix)
        program.setUniform("intervalLength", lightSystem.intervalLength)
        program.setUniform("cascadeCount", cascadeCount.toFloat())

        while (cascadeIndex >= lightSystem.drawCascade)
        {
            val fbo = fbo[cascadeIndex % 2]
            fbo.bind()
            fbo.clear()
            program.setUniform("cascadeIndex", cascadeIndex.toFloat())

            renderers[0].drawTextures(
                sceneSurface.getTexture(0), // Scene
                sceneSurface.getTexture(1), // Scene metadata
                sdfSurface.getTexture(),    // SDF
                outTextures[0]              // Last cascade
            )

            fbo.release()
            outTextures = fbo.getTextures()
            cascadeIndex--
        }

        return outTextures
    }
}