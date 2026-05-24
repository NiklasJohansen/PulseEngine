package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.graphics.api.DrawList
import no.njoh.pulseengine.core.graphics.api.DrawList.RenderItem
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureCompare
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.CLAMP_TO_BORDER
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.CLAMP_TO_EDGE
import no.njoh.pulseengine.core.graphics.api.addAllVisible
import no.njoh.pulseengine.core.graphics.api.objects.ModelBufferObject
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.BrdfLutBuilder
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawGpuCulledModelBatches
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawModelBatches
import no.njoh.pulseengine.core.graphics.util.GpuModelCuller
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.ModelBatcher
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.Color.Companion.WHITE
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.quickSort
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.utils.Logger
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE
import org.lwjgl.opengl.GL14.glBlendFuncSeparate
import kotlin.math.cos

class ModelRenderer(override val order: Int = 40) : Renderer()
{
    var iblDiffuseTexture  = ""
    var iblSpecularTexture = ""
    var iblIntensity       = 1f

    var sunColor                = Color(1f, 1f, 1f)
    var sunRadius               = 1f
    var sunShadowMapSurfaceName = ""

    private lateinit var staticProgram: ShaderProgram
    private lateinit var skinnedProgram: ShaderProgram
    private lateinit var opaqueBatcher: ModelBatcher
    private lateinit var maskedBatcher: ModelBatcher
    private lateinit var blendedBatcher: ModelBatcher

    private var opaqueCuller: GpuModelCuller? = null
    private var maskedCuller: GpuModelCuller? = null

    private val modelBuffer     = ModelBufferObject()
    private var readDrawLists   = ArrayList<DrawList>()
    private var writeDrawLists  = ArrayList<DrawList>()
    private var readLightData   = BufferUtils.createFloatBuffer(MAX_POINT_LIGHTS * 12)
    private var writeLightData  = BufferUtils.createFloatBuffer(MAX_POINT_LIGHTS * 12)
    private var readLightCount  = 0
    private var writeLightCount = 0
    private var iblBrdfTexture  = "ibl_brdf_lut"
    private val camPos          = Vector3f()
    private val tmpPos1         = Vector3f()
    private val tmpPos2         = Vector3f()
    private val opaqueItems     = ArrayList<RenderItem>(1024)
    private val maskedItems     = ArrayList<RenderItem>(512)
    private val blendedItems    = ArrayList<RenderItem>(256)
    private val blendComparator = Comparator<RenderItem> { a, b -> compareForTransparency(a, b) }
    private val frustum         = Frustum()

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::staticProgram.isInitialized)
        {
            staticProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_pbr.vert", ::transformModelVertexShader)),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_pbr.frag"))
            )
            skinnedProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_pbr_skinned.vert", ::transformModelVertexShader)),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_pbr.frag"))
            )
            opaqueBatcher = ModelBatcher(staticProgram, skinnedProgram)
            maskedBatcher = ModelBatcher(staticProgram, skinnedProgram)
            blendedBatcher = ModelBatcher(staticProgram, skinnedProgram)
            opaqueCuller = GpuModelCuller.createIfSupported()?.apply { init(engine) }
            maskedCuller = GpuModelCuller.createIfSupported()?.apply { init(engine) }
            modelBuffer.init()
        }

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
    }

    override fun onInitFrame()
    {
        writeDrawLists = readDrawLists.also { readDrawLists = writeDrawLists }
        writeDrawLists.clear()

        readLightData = writeLightData.also { writeLightData = readLightData }
        readLightCount = writeLightCount
        readLightData.flip()
        writeLightData.clear()
        writeLightCount = 0
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex > 0) return // Only once per frame

        configureProgram(staticProgram, engine, surface)
        configureProgram(skinnedProgram, engine, surface)

        glEnable(GL_DEPTH_TEST)
        glDisable(GL_BLEND)

        val hasDepthPrepass = surface.config.hasDepthPrepass
        glDepthFunc(if (hasDepthPrepass) GL_LEQUAL else GL_LESS)
        glDepthMask(!hasDepthPrepass)

        frustum.setForCamera(surface.camera)
        
        opaqueItems.clear()
        maskedItems.clear()
        blendedItems.clear()
        modelBuffer.clear()
        opaqueCuller?.clear()
        maskedCuller?.clear()

        readDrawLists.forEachFast()
        {
            opaqueItems.addAllVisible(it.opaqueItems, frustum = if (opaqueCuller == null) frustum else null)
            maskedItems.addAllVisible(it.maskedItems, frustum = if (maskedCuller == null) frustum else null)
            blendedItems.addAllVisible(it.blendedItems, frustum)
        }

        blendedItems.quickSort(blendComparator)

        val opaqueBatches  = opaqueBatcher.createBatchesAndFillBuffer(opaqueItems, modelBuffer, opaqueCuller, sortForBatching = true)
        val maskedBatches  = maskedBatcher.createBatchesAndFillBuffer(maskedItems, modelBuffer, maskedCuller, sortForBatching = true)
        val blendedBatches = blendedBatcher.createBatchesAndFillBuffer(blendedItems, modelBuffer, sortForBatching = false)

        modelBuffer.submit()

        val opaqueCount = opaqueBatches.totalInstanceCount()
        measure({"opaque (" plus opaqueCount plus "i, " plus opaqueBatches.size plus "b)"})
        {
            if (opaqueCuller != null)
            {
                opaqueCuller!!.submitAndCull(opaqueBatches, frustum)
                drawGpuCulledModelBatches(opaqueBatches, opaqueCuller!!)
            }
            else drawModelBatches(opaqueBatches, modelBuffer.instanceIndexMode, modelBuffer.instanceIndexBuffer)
        }

        staticProgram.bind()
        staticProgram.setUniformSampler("uGtaoTex", engine.gfx.textureBank.getOrCreateFallbackTexture(WHITE))
        skinnedProgram.bind()
        skinnedProgram.setUniformSampler("uGtaoTex", engine.gfx.textureBank.getOrCreateFallbackTexture(WHITE))

        val maskedCount = maskedBatches.totalInstanceCount()
        if (maskedCount > 0)
        {
            measure({"masked (" plus maskedCount plus "i, " plus maskedBatches.size plus "b)"})
            {
                glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE)
                glDepthFunc(GL_LEQUAL)
                glDepthMask(true)

                if (maskedCuller != null)
                {
                    maskedCuller!!.submitAndCull(maskedBatches, frustum)
                    drawGpuCulledModelBatches(maskedBatches, maskedCuller!!)
                }
                else drawModelBatches(maskedBatches, modelBuffer.instanceIndexMode, modelBuffer.instanceIndexBuffer)

                glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE)
            }
        }

        val blendedCount = blendedBatches.totalInstanceCount()
        if (blendedCount > 0)
        {
            measure({"blended (" plus blendedCount plus "i, " plus blendedBatches.size plus "b)"})
            {
                glEnable(GL_BLEND)
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
                glDepthFunc(GL_LEQUAL)
                glDepthMask(false)

                drawModelBatches(blendedBatches, modelBuffer.instanceIndexMode, modelBuffer.instanceIndexBuffer)
            }
        }

        opaqueCuller?.markSubmittedDataInUse()
        maskedCuller?.markSubmittedDataInUse()
        modelBuffer.markSubmittedDataInUse()
        glDepthMask(true)
    }

    private fun configureProgram(program: ShaderProgram, engine: PulseEngineInternal, surface: SurfaceInternal)
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

    private fun compareForTransparency(a: RenderItem, b: RenderItem): Int
    {
        val aDist = camPos.distanceSquared(a.transform.getTranslation(tmpPos1))
        val bDist = camPos.distanceSquared(b.transform.getTranslation(tmpPos2))
        return bDist.compareTo(aDist)
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
        modelBuffer.destroy()
        opaqueCuller?.destroy()
        maskedCuller?.destroy()
    }

    fun draw(drawList: DrawList)
    {
        writeDrawLists += drawList
        increaseBatchSize()
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
