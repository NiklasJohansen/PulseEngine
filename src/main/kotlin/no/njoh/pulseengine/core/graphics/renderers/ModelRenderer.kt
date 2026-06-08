package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.graphics.api.world.views.WorldCameraRenderView
import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureCompare
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.CLAMP_TO_BORDER
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.CLAMP_TO_EDGE
import no.njoh.pulseengine.core.graphics.api.TransparencyMode.SORTED_BLEND
import no.njoh.pulseengine.core.graphics.api.TransparencyMode.WEIGHTED_BLENDED_OIT
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.BrdfLutBuilder
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawWorldRenderBucket
import no.njoh.pulseengine.core.graphics.api.world.views.ViewIds.MAIN_CAMERA_VIEW
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.Color.Companion.WHITE
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.utils.Logger
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE
import org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_ONE
import org.lwjgl.opengl.GL14.glBlendFuncSeparate
import kotlin.math.cos

class ModelRenderer(
    override val order: Int = 40,
    val viewId: Int = MAIN_CAMERA_VIEW
) : Renderer() {

    var iblDiffuseTexture  = ""
    var iblSpecularTexture = ""
    var iblIntensity       = 1f
    var iblBrdfTexture     = "ibl_brdf_lut"

    var sunColor                = Color(1f, 1f, 1f)
    var sunRadius               = 1f
    var sunShadowMapSurfaceName = ""

    var transparencyMode         = WEIGHTED_BLENDED_OIT
    var weightedBlendAlphaCutoff = 0.04f

    private lateinit var staticProgram: ShaderProgram
    private lateinit var skinnedProgram: ShaderProgram
    private lateinit var programs: ShaderProgramSet

    private val weightedBlendedRenderer = WeightedBlendedOitRenderer()
    private var readLightData           = BufferUtils.createFloatBuffer(MAX_POINT_LIGHTS * 12)
    private var writeLightData          = BufferUtils.createFloatBuffer(MAX_POINT_LIGHTS * 12)
    private var readLightCount          = 0
    private var writeLightCount         = 0
    private val camPos                  = Vector3f()

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::staticProgram.isInitialized)
        {
            val staticVertex = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_pbr.vert", ::transformModelVertexShader))
            val skinnedVertex = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_pbr_skinned.vert", ::transformModelVertexShader))
            val pbrFragment = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_pbr.frag"))

            staticProgram = ShaderProgram.create(staticVertex, pbrFragment)
            skinnedProgram = ShaderProgram.create(skinnedVertex, pbrFragment)
            programs = ShaderProgramSet(staticProgram, skinnedProgram)
        }

        weightedBlendedRenderer.init(engine, surface)

        if (engine.asset.getOrNull<Texture>(iblBrdfTexture) == null)
        {
            val brdfLutTex = Texture(
                name = iblBrdfTexture,
                filePath = "",
                initWidth = 512,
                initHeight = 512,
                format = TextureFormat.RGBA16F,
                wrapping = CLAMP_TO_EDGE,
                filter = LINEAR,
                maxMipLevels = 1
            )
            engine.asset.loadNow(brdfLutTex)
            BrdfLutBuilder.generate(engine, brdfLutTex)
        }

        if (engine.gfx.worldContext.getView<WorldCameraRenderView>(viewId) == null)
            engine.gfx.worldContext.addView(WorldCameraRenderView(viewId))
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        readLightData = writeLightData.also { writeLightData = readLightData }
        readLightCount = writeLightCount
        readLightData.flip()
        writeLightData.clear()
        writeLightCount = 0
        increaseBatchSize() // Ensure that the batch size is at least 1

        val view = engine.gfx.worldContext.getView<WorldCameraRenderView>(viewId)
        view?.setForCamera(camera = surface.camera)
        view?.transparencyMode = transparencyMode
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return // Only once per frame

        glEnable(GL_DEPTH_TEST)
        glDisable(GL_BLEND)

        val hasDepthPrepass = surface.config.hasDepthPrepass
        glDepthFunc(if (hasDepthPrepass) GL_LEQUAL else GL_LESS)
        glDepthMask(!hasDepthPrepass)

        configureProgram(staticProgram, engine, surface)
        configureProgram(skinnedProgram, engine, surface)

        engine.gfx.worldContext.getView<WorldCameraRenderView>(viewId)?.let { view -> render(engine, surface, view) }

        glDepthMask(true)
    }

    private fun render(engine: PulseEngineInternal, surface: SurfaceInternal, view: WorldCameraRenderView)
    {
        measure({"opaque (" plus view.opaqueBucket.totalInstanceCount() plus "i, " plus view.opaqueBucket.size plus "b)"})
        {
            drawWorldRenderBucket(view.opaqueBucket, view.preparedPass, programs)
        }

        staticProgram.bind()
        staticProgram.setUniformSampler("uGtaoTex", engine.gfx.textureBank.getOrCreateFallbackTexture(WHITE))
        skinnedProgram.bind()
        skinnedProgram.setUniformSampler("uGtaoTex", engine.gfx.textureBank.getOrCreateFallbackTexture(WHITE))

        val maskedCount = view.maskedBucket.totalInstanceCount()
        if (maskedCount > 0)
        {
            measure({"masked (" plus maskedCount plus "i, " plus view.maskedBucket.size plus "b)"})
            {
                glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE)
                glEnable(GL_SAMPLE_ALPHA_TO_ONE)
                glDepthFunc(GL_LEQUAL)
                glDepthMask(true)

                drawWorldRenderBucket(view.maskedBucket, view.preparedPass, programs)

                glDisable(GL_SAMPLE_ALPHA_TO_ONE)
                glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE)
            }
        }

        val blendedCount = view.blendedBucket.totalInstanceCount()
        if (blendedCount > 0)
        {
            when (transparencyMode)
            {
                SORTED_BLEND -> measure({"blended (" plus blendedCount plus "i, " plus view.blendedBucket.size plus "b)"})
                {
                    glEnable(GL_BLEND)
                    glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
                    glDepthFunc(GL_LEQUAL)
                    glDepthMask(false)

                    drawWorldRenderBucket(view.blendedBucket, view.preparedPass, programs)
                }
                WEIGHTED_BLENDED_OIT ->
                {
                    weightedBlendedRenderer.alphaCutoff = weightedBlendAlphaCutoff
                    weightedBlendedRenderer.render(
                        engine = engine,
                        surface = surface,
                        bucket = view.blendedBucket,
                        preparedPass = view.preparedPass,
                        configureAccumProgram = ::configureProgram
                    )
                }
            }
        }
    }

    private fun configureProgram(program: ShaderProgram, engine: PulseEngineInternal, surface: Surface)
    {
        // Textures

        val texBank = engine.gfx.textureBank
        program.bind()
        program.setUniformSamplerArrays(texBank.getAllTextureArrays())

        // Ambient occlusion

        val aoRenderer = surface.getRenderer<GtaoRenderer>()
        val aoTex = aoRenderer?.getAoRenderTexture() ?: texBank.getOrCreateFallbackTexture(WHITE)
        program.setUniformSampler("uGtaoTex", aoTex)
        program.setUniform("uAoIntensity", aoRenderer?.intensity ?: 0f)

        // Cascaded shadow mapping

        val shadowMapSurface = engine.gfx.getSurface(sunShadowMapSurfaceName)
        val shadowMapRenderer = shadowMapSurface?.getRenderer<CascadedShadowMapRenderer>()
        val shadowMapTex = shadowMapSurface?.getTexture() ?: texBank.getOrCreateFallbackTexture(WHITE)
        val sunViewProjections = shadowMapRenderer?.getViewProjectionMatrices() ?: fallbackShadowVPs
        val splitDist = shadowMapRenderer?.getCascadeSplitDistances() ?: fallbackSplitDists
        val cascadeSize = shadowMapRenderer?.getCascadeSizeMeters() ?: fallbackCascadeSizes
        program.setUniformSampler("uShadowMapTex", shadowMapTex, filter = LINEAR, wrapping = CLAMP_TO_BORDER, compare = TextureCompare.LEQUAL, borderColor = WHITE)
        program.setUniform("uShadowMapTexSize", shadowMapRenderer?.resolution?.toFloat() ?: 1024f)
        program.setUniform("uShadowViewProjections", sunViewProjections)
        program.setUniform("uShadowCascadeSplitDistances", splitDist[0], splitDist[1], splitDist[2], splitDist[3])
        program.setUniform("uShadowCascadeSizeMeters", cascadeSize[0], cascadeSize[1], cascadeSize[2], cascadeSize[3])

        // Sunlight

        program.setUniform("uSunColor", sunColor)
        program.setUniform("uSunDirection", shadowMapRenderer?.getDirection() ?: Vector3f(0f, 1f, 0f))
        program.setUniform("uSunRadius", sunRadius)

        // Local lights

        program.setUniform("uLightCount", readLightCount)
        program.setUniformVec4Array("uLightData", readLightData)

        // Ambient lighting

        val envSpecularMipCount = engine.asset.getOrNull<Texture>(iblSpecularTexture)?.let { texBank.getTextureArray(it) }?.mipLevels?.toFloat() ?: 1f
        program.setUniform("uEnvSpecularMipCount", envSpecularMipCount)
        program.setUniform("uEnvIntensity", iblIntensity)
        program.setTexture("uEnvDiffuseTex", engine.asset.getOrNull(iblDiffuseTexture))
        program.setTexture("uEnvSpecularTex", engine.asset.getOrNull(iblSpecularTexture))
        program.setTexture("uEnvBrdfLutTex", engine.asset.getOrNull(iblBrdfTexture))

        // Camera

        surface.camera.invViewMatrix.getTranslation(camPos)
        program.setUniform("uScreenSize", surface.config.width.toFloat(), surface.config.height.toFloat())
        program.setUniform("uViewProjection", surface.camera.viewProjectionMatrix)
        program.setUniform("uView", surface.camera.viewMatrix)
        program.setUniform("uCameraPos", camPos)
    }

    private fun ShaderProgram.setTexture(name: String, tex: Texture?)
    {
        if (tex != null)
            setUniform(name, tex.handle.samplerIndex.toFloat(), tex.handle.textureIndex.toFloat(), tex.uMax, tex.vMax)
        else
            setUniform(name, -1f, 0f, 0f, 0f)
    }

    override fun destroy()
    {
        staticProgram.destroy()
        skinnedProgram.destroy()
        weightedBlendedRenderer.destroy()
    }

    fun addLight(pos: Vector3f, dir: Vector3f, radius: Float, color: Color, intensity: Float, outerConeDegrees: Float, innerConeDegrees: Float)
    {
        if (writeLightCount >= MAX_POINT_LIGHTS)
        {
            Logger.warn { "Too many lights in scene, max is $MAX_POINT_LIGHTS" }
            return
        }

        val col = color.asLinear()
        val isSpotLight = if (outerConeDegrees < 180f) 1f else 0f

        writeLightCount++
        writeLightData
            .put(pos.x).put(pos.y).put(pos.z)
            .put(radius)
            .put(col.red).put(col.green).put(col.blue)
            .put(intensity)
            .put(dir.x).put(dir.y).put(dir.z)
            .put(cos(outerConeDegrees.toRadians()))
            .put(cos(innerConeDegrees.toRadians()))
            .put(isSpotLight)
            .put(0f).put(0f) // Padding to 16 floats for alignment
    }

    companion object
    {
        private const val MAX_POINT_LIGHTS = 32

        val fallbackShadowVPs    = Array(CascadedShadowMapRenderer.CASCADE_COUNT) { Matrix4f() }
        val fallbackSplitDists   = FloatArray(CascadedShadowMapRenderer.CASCADE_COUNT)
        val fallbackCascadeSizes = FloatArray(CascadedShadowMapRenderer.CASCADE_COUNT) { 15f }
    }
}