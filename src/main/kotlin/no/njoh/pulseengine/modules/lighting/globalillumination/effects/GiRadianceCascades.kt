package no.njoh.pulseengine.modules.lighting.globalillumination.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BaseEffect
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.lighting.globalillumination.GlobalIlluminationSystem
import kotlin.math.*

class GiRadianceCascades(
    private val localSceneSurfaceName: String,
    private val globalSceneSurfaceName: String,
    private val localSdfSurfaceName: String,
    private val globalSdfSurfaceName: String,
    override val name: String = "radiance_cascades"
) : BaseEffect(
    TextureDescriptor(filter = LINEAR, format = RGBA16F),
    numFrameBufferObjects = 2
) {
    private var skyColor = Color()
    private var sunColor = Color()
    private var outTextures = emptyList<Texture>()

    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/gi/default.vert",
        fragmentShaderFileName = "/pulseengine/shaders/gi/radiance_cascades.frag"
    )

    override fun getTexture(index: Int) = outTextures.getOrNull(index)

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        val lightSystem = engine.scene.getSystemOfType<GlobalIlluminationSystem>() ?: return inTextures
        val localSceneSurface = engine.gfx.getSurface(localSceneSurfaceName) ?: return inTextures
        val globalSceneSurface = engine.gfx.getSurface(globalSceneSurfaceName) ?: return inTextures
        val localSdfSurface = engine.gfx.getSurface(localSdfSurfaceName) ?: return inTextures
        val globalSdfSurface = engine.gfx.getSurface(globalSdfSurfaceName) ?: return inTextures

        val width = inTextures[0].width.toFloat()
        val height = inTextures[0].height.toFloat()
        val baseRayCount = 4f
        val diagonalSize = sqrt(width * width + height * height)
        val cascadeCount = min(ceil(log2(diagonalSize) / log2(baseRayCount)).toInt() + 1, lightSystem.maxCascades)
        var cascadeIndex = cascadeCount - 1
        val sceneWidth = max(localSceneSurface.config.width, globalSceneSurface.config.width)
        val sceneHeight = max(localSceneSurface.config.height, globalSceneSurface.config.height)
        val minStepSize = min(1f / sceneWidth, 1f / sceneHeight) * 0.5f
        val worldScale = max(1f, lightSystem.worldScale)
        val xCamOrigin = globalSceneSurface.camera.origin.x / globalSceneSurface.config.width
        val yCamOrigin = 1f - (globalSceneSurface.camera.origin.y / globalSceneSurface.config.height)

        val program = programs[0]
        val skyLight = if (lightSystem.skyLight) lightSystem.skyIntensity else 0f
        val sunLight = if (lightSystem.skyLight) lightSystem.sunIntensity else 0f

        textureDescriptors[0].filter = lightSystem.textureFilter

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
        program.setUniform("intervalOverlap", lightSystem.intervalOverlap)
        program.setUniform("cascadeCount", cascadeCount.toFloat())
        program.setUniform("worldScale", worldScale)
        program.setUniform("invWorldScale", 1f / worldScale)
        program.setUniform("traceWorldRays", lightSystem.traceWorldRays)
        program.setUniform("mergeCascades", lightSystem.mergeCascades)
        program.setUniform("maxSteps", lightSystem.maxSteps)
        program.setUniform("camAngle", localSceneSurface.camera.rotation.z)
        program.setUniform("camScale", localSceneSurface.camera.scale.x)
        program.setUniform("camOrigin", xCamOrigin, yCamOrigin)
        program.setUniformSampler("localSceneTex", localSceneSurface.getTexture(0))
        program.setUniformSampler("localMetadataTex", localSceneSurface.getTexture(1))
        program.setUniformSampler("globalSceneTex", globalSceneSurface.getTexture(0))
        program.setUniformSampler("globalMetadataTex", globalSceneSurface.getTexture(1))
        program.setUniformSampler("localSdfTex", localSdfSurface.getTexture())
        program.setUniformSampler("globalSdfTex", globalSdfSurface.getTexture())

        while (cascadeIndex >= lightSystem.drawCascade)
        {
            GpuProfiler.beginMeasure { "Cascade #" plus cascadeIndex }

            val fbo = frameBuffers[cascadeIndex % 2]
            fbo.bind()
            fbo.clear()
            program.setUniform("cascadeIndex", cascadeIndex.toFloat())
            program.setUniformSampler("upperCascadeTex", outTextures[0])
            renderer.draw()
            fbo.release()
            outTextures = fbo.getTextures()
            cascadeIndex--

            GpuProfiler.endMeasure()
        }

        return outTextures
    }
}