package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.MASK
import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.BrdfLutBuilder
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawTriangleIndices
import no.njoh.pulseengine.core.shared.primitives.Color.Companion.WHITE
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.graphics.api.DrawList
import no.njoh.pulseengine.core.graphics.api.DrawList.RenderItem
import no.njoh.pulseengine.core.graphics.api.TextureCompare
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.*
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.utils.Logger
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE
import org.lwjgl.opengl.GL14.glBlendFuncSeparate
import kotlin.math.cos

class ModelRenderer : Renderer()
{
    // IBL parameters
    var iblDiffuseTexture  = ""
    var iblSpecularTexture = ""
    var iblIntensity       = 1f

    // Sun parameters
    var sunColor                = Color(1f, 1f, 1f)
    var sunRadius               = 1f
    var sunShadowMapSurfaceName = ""

    private lateinit var program: ShaderProgram

    private var readDrawLists  = ArrayList<DrawList>()
    private var writeDrawLists = ArrayList<DrawList>()

    // 3=Position, 1=Radius, 3=Color, 1=Intensity, 3=Direction, 1=CosOuterCone, 1=CosInnerCone, 1=IsSpotLight, 2=Padding
    private var readLightData   = BufferUtils.createFloatBuffer(MAX_POINT_LIGHTS * 12)
    private var writeLightData  = BufferUtils.createFloatBuffer(MAX_POINT_LIGHTS * 12)
    private var readLightCount  = 0
    private var writeLightCount = 0

    private var iblBrdfTexture  = "ibl_brdf_lut"
    private val camPos          = Vector3f()
    private val tmpPos          = Vector3f()
    private var currentCullMode = null as CullMode?

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::program.isInitialized)
        {
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_pbr.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_pbr.frag"))
            )
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
        program.setTexture("uEnvDiffuseTex",  engine.asset.getOrNull(iblDiffuseTexture))
        program.setTexture("uEnvSpecularTex", engine.asset.getOrNull(iblSpecularTexture))
        program.setTexture("uEnvBrdfLutTex",  engine.asset.getOrNull(iblBrdfTexture))

        // Camera

        surface.camera.invViewMatrix.getTranslation(camPos)
        program.setUniform("uScreenSize", surface.config.width.toFloat(), surface.config.height.toFloat())
        program.setUniform("uViewProjection", surface.camera.viewProjectionMatrix)
        program.setUniform("uView", surface.camera.viewMatrix)
        program.setUniform("uCameraPos", camPos)

        // Draw opaque meshes  

        glEnable(GL_DEPTH_TEST)
        glDisable(GL_BLEND)

        val hasDepthPrepass = surface.config.hasDepthPrepass
        glDepthFunc(if (hasDepthPrepass) GL_LEQUAL else GL_LESS)
        glDepthMask(!hasDepthPrepass) // Disable depth writes if depth prepass exists

        readDrawLists.forEachFast { list -> list.opaqueItems.forEachFast { drawItem(it) } }

        // Draw masked meshes

        // Disable AO for masked and transparent meshes
        program.setUniformSampler("uGtaoTex", texBank.getOrCreateFallbackTexture(WHITE))

        if (readDrawLists.anyMatches { it.maskedItems.isNotEmpty() })
        {
            glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE)
            glDepthFunc(GL_LEQUAL)
            glDepthMask(true)

            readDrawLists.forEachFast { list -> list.maskedItems.forEachFast { drawItem(it) } }

            glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE)
        }

        // Draw transparent/blended meshes

        if (readDrawLists.anyMatches { it.transparentItems.isNotEmpty() })
        {
            glEnable(GL_BLEND)
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
            glDepthFunc(GL_LEQUAL)
            glDepthMask(false)

            readDrawLists.forEachFast { list ->
                list.transparentItems.sortByDescending { camPos.distanceSquared(it.transform.getTranslation(tmpPos)) }
                list.transparentItems.forEachFast { drawItem(it) } 
            }
        }

        // Restore for later renderers
        
        glDepthMask(true)
        setCullMode(CullMode.BACK)
        readDrawLists.clear()
    }

    private fun drawItem(item: RenderItem)
    {
        val vao = item.model.vao ?: return
        val subMesh = item.subMesh
        val mat = item.material
        val alphaCutoff = if (mat?.blendMode == MASK) mat.alphaCutoff else 0.0f

        program.setUniform("uModel",           item.transform)
        program.setUniform("uAlphaCutoff",     alphaCutoff)
        program.setUniform("uBaseColor",       mat?.baseColor ?: WHITE, false)
        program.setUniform("uEmissiveFactor",  mat?.emissiveFactor ?: WHITE, false)
        program.setUniform(
            name = "uAoMetalRoughNormalFactor",
            value1 = mat?.occlusionStrength ?: 1f,
            value2 = mat?.roughnessFactor ?: 1f,
            value3 = mat?.metallicFactor ?: 1f,
            value4 = mat?.normalScale ?: 1f
        )
        program.setTexture("uAlbedoTex",       mat?.albedo)
        program.setTexture("uNormalTex",       mat?.normal)
        program.setTexture("uAoMetalRoughTex", mat?.aoMetalRough)
        program.setTexture("uEmissiveTex",     mat?.emissive)

        setCullMode(mat?.cullMode ?: CullMode.BACK)

        drawTriangleIndices(vao, subMesh.indexStart, subMesh.indexCount)
    }

    private fun ShaderProgram.setTexture(name: String, tex: Texture?)
    {
        if (tex != null)
            setUniform(name, tex.handle.samplerIndex.toFloat(), tex.handle.textureIndex.toFloat(), tex.uMax, tex.vMax)
        else
            setUniform(name, -1f, 0f, 0f, 0f)
    }

    private fun setCullMode(mode: CullMode)
    {
        if (mode == currentCullMode) return
        currentCullMode = mode

        when (mode)
        {
            CullMode.NONE -> glDisable(GL_CULL_FACE)
            CullMode.BACK -> { glEnable(GL_CULL_FACE); glCullFace(GL_BACK) }
        }
    }

    override fun destroy()
    {
        program.destroy()
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