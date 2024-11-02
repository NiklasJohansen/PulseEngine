package no.njoh.pulseengine.modules.lighting.globalillumination.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.postprocessing.MultiPassEffect
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.lighting.globalillumination.GlobalIlluminationSystem
import kotlin.math.*

class RadianceCascades(
    private val screenSurfaceName: String,
    private val screenDistanceFieldSurfaceName: String,
    private val worldSurfaceName: String,
    private val worldDistanceFieldSurfaceName: String,
    override val name: String = "rc"
) : MultiPassEffect(
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
        val screenSurface = engine.gfx.getSurface(screenSurfaceName) ?: return inTextures
        val screenDistanceFieldSurface = engine.gfx.getSurface(screenDistanceFieldSurfaceName) ?: return inTextures
        val worldSurface = engine.gfx.getSurface(worldSurfaceName) ?: return inTextures
        val worldDistanceFieldSurface = engine.gfx.getSurface(worldDistanceFieldSurfaceName) ?: return inTextures

        val width = inTextures[0].width.toFloat()
        val height = inTextures[0].height.toFloat()
        val baseRayCount = 4f
        val diagonalSize = sqrt(width * width + height * height)
        val cascadeCount = min(ceil(log2(diagonalSize) / log2(baseRayCount)).toInt() + 1, lightSystem.maxCascades)
        var cascadeIndex = cascadeCount - 1
        val minStepSize = min(1f / width, 1f / height) * 0.5f
        val program = programs[0]
        val skyLight = if (lightSystem.ambientLight) lightSystem.skyIntensity else 0f
        val sunLight = if (lightSystem.ambientLight) lightSystem.sunIntensity else 0f

        textureFilter = lightSystem.textureFilter
        skyColor.setFrom(lightSystem.skyColor).multiplyRgb(skyLight)
        sunColor.setFrom(lightSystem.sunColor).multiplyRgb(sunLight)
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
        program.setUniform("worldScale", lightSystem.worldScale)
        program.setUniform("traceWorldRays", lightSystem.traceWorldRays)

        while (cascadeIndex >= lightSystem.drawCascade)
        {
            val fbo = fbo[cascadeIndex % 2]
            fbo.bind()
            fbo.clear()
            program.setUniform("cascadeIndex", cascadeIndex.toFloat())

            renderers[0].drawTextures(
                screenSurface.getTexture(0),             // Screen radiance
                screenSurface.getTexture(1),             // Screen metadata
                screenDistanceFieldSurface.getTexture(), // Screen Distance field
                worldSurface.getTexture(0),              // World radiance
                worldSurface.getTexture(1),              // World metadata
                worldDistanceFieldSurface.getTexture(),  // World Distance field
                outTextures[0]                           // Last cascade
            )

            fbo.release()
            outTextures = fbo.getTextures()
            cascadeIndex--
        }

        return outTextures
    }
}