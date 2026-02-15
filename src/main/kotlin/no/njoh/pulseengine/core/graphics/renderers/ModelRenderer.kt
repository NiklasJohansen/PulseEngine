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
import no.njoh.pulseengine.core.shared.utils.Logger
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE
import org.lwjgl.opengl.GL14.glBlendFuncSeparate

class ModelRenderer : Renderer()
{
    private lateinit var program: ShaderProgram

    private var readDrawLists  = ArrayList<DrawList>()
    private var writeDrawLists = ArrayList<DrawList>()
    
    // 3=Position, 1=Radius, 3=Color, 1=Intensity
    private var readLightData   = BufferUtils.createFloatBuffer(MAX_POINT_LIGHTS * 8)
    private var writeLightData  = BufferUtils.createFloatBuffer(MAX_POINT_LIGHTS * 8)
    private var readLightCount  = 0
    private var writeLightCount = 0

    private var currentCullMode: CullMode? = null

    private var iblBrdfTexture = "ibl_brdf_lut"
    private val camPos         = Vector3f()
    private val tmpPos         = Vector3f()
    private var sunColor       = Color(1f, 1f, 1f)

    var shadowMapSurfaceName = ""
    var iblDiffuseTexture    = ""
    var iblSpecularTexture   = ""
    var iblIntensity         = 1f

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

        // Camera position
        surface.camera.invViewMatrix.getTranslation(camPos)

        val hasDepthPrepass = surface.config.hasDepthPrepass
        val texBank = engine.gfx.textureBank

        val aoRenderer = surface.getRenderer<GtaoRenderer>()
        val aoTex = aoRenderer?.getAoRenderTexture() ?: texBank.getOrCreateFallbackTexture(WHITE)
        
        val shadowMapSurface = engine.gfx.getSurface(shadowMapSurfaceName)
        val shadowMapRenderer = shadowMapSurface?.getRenderer<ShadowMapRenderer>()
        val shadowMapTex = shadowMapSurface?.getTexture() ?: texBank.getOrCreateFallbackTexture(WHITE)
        val sunIntensity = shadowMapRenderer?.lightIntensity ?: 0f
        val sunColor = sunColor.setFrom(shadowMapRenderer?.lightColor ?: WHITE).multiplyRgb(sunIntensity)
        val sunViewProjection = shadowMapRenderer?.getViewProjectionMatrix() ?: Matrix4f()

        val envSpecularMipCount = engine.asset.getOrNull<Texture>(iblSpecularTexture)
            ?.let { texBank.getTextureArray(it) }?.mipLevels?.toFloat() ?: 1f

        program.bind()
        
        // Textures
        program.setUniformSamplerArrays(texBank.getAllTextureArrays())
        
        // Ambient occlusion
        program.setUniformSampler("uGtaoTex", aoTex)
        program.setUniform("uAoIntensity", aoRenderer?.intensity ?: 0f)
        
        // Shadow mapping
        program.setUniformSampler("uShadowDepthTex", shadowMapTex, filter = NEAREST, wrapping = CLAMP_TO_BORDER, compare = TextureCompare.NONE, borderColor = WHITE)
        program.setUniformSampler("uShadowCompareTex", shadowMapTex, filter = LINEAR, wrapping = CLAMP_TO_BORDER, compare = TextureCompare.LEQUAL, borderColor = WHITE)
        program.setUniform("uShadowMapNear", shadowMapRenderer?.nearPlane ?: 0.1f)
        program.setUniform("uShadowMapFar", shadowMapRenderer?.farPlane ?: 300f)
        program.setUniform("uShadowMapSizeMeters", shadowMapRenderer?.shadowMapSizeMeters ?: 15f)
        program.setUniform("uShadowContactHardening", shadowMapRenderer?.shadowContactHardening ?: false)

        // Sunlight
        program.setUniform("uSunColor", sunColor)
        program.setUniform("uSunDirection", shadowMapRenderer?.lightDirection ?: Vector3f(0f, 1f, 0f))
        program.setUniform("uSunRadius", shadowMapRenderer?.lightRadius ?: 1f)
        program.setUniform("uSunViewProjection", sunViewProjection)

        // Lights
        program.setUniform("uLightCount", readLightCount)
        program.setUniformVec4Array("uLightData", readLightData)

        // Ambient lighting
        program.setUniform("uEnvSpecularMipCount", envSpecularMipCount)
        program.setUniform("uEnvIntensity", iblIntensity)
        program.setTexture("uEnvDiffuseTex",  engine.asset.getOrNull(iblDiffuseTexture))
        program.setTexture("uEnvSpecularTex", engine.asset.getOrNull(iblSpecularTexture))
        program.setTexture("uEnvBrdfLutTex",  engine.asset.getOrNull(iblBrdfTexture))

        // Camera
        program.setUniform("uScreenSize", surface.config.width.toFloat(), surface.config.height.toFloat())
        program.setUniform("uViewProjection", surface.camera.viewProjectionMatrix)
        program.setUniform("uCameraPos", camPos)
        
        // -------- OPAQUE --------

        glEnable(GL_DEPTH_TEST)
        glDisable(GL_BLEND)

        glDepthFunc(if (hasDepthPrepass) GL_LEQUAL else GL_LESS)
        glDepthMask(!hasDepthPrepass) // Disable depth writes if depth prepass exists

        readDrawLists.forEachFast { list -> list.opaqueItems.forEachFast { drawItem(it) } }

        // -------- MASK --------

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

        // -------- TRANSPARENT / BLEND --------

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

        readDrawLists.clear()
 
        glDepthMask(true) // Restore for later renderers
        setCullMode(CullMode.BACK)
    }

    private fun drawItem(item: RenderItem)
    {
        val vao      = item.model.vao ?: return
        val subMesh  = item.subMesh
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

    fun addLight(pos: Vector3f, radius: Float, color: Color, intensity: Float)
    {
        if (writeLightCount >= MAX_POINT_LIGHTS){
            Logger.info { "Too many point lights in scene, max is $MAX_POINT_LIGHTS" }
            return
        }

        val col = color.asLinear()
        writeLightCount++
        writeLightData
            .put(pos.x).put(pos.y).put(pos.z)
            .put(radius)
            .put(col.red).put(col.green).put(col.blue)
            .put(intensity)
    }
 
    companion object
    { 
        private const val MAX_POINT_LIGHTS = 32
    }
}