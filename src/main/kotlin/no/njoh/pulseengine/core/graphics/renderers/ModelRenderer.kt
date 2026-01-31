package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.MASK
import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureFilter
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.graphics.api.TextureWrapping
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.BrdfLutBuilder
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawTriangleIndices
import no.njoh.pulseengine.core.shared.primitives.Color.Companion.WHITE
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.graphics.api.DrawList
import no.njoh.pulseengine.core.graphics.api.DrawList.RenderItem
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE
import org.lwjgl.opengl.GL14.glBlendFuncSeparate

class ModelRenderer : Renderer()
{
    private lateinit var program: ShaderProgram

    private var readDrawLists  = ArrayList<DrawList>()
    private var writeDrawLists = ArrayList<DrawList>()

    private var currentCullMode: CullMode? = null

    private val invViewMatrix = Matrix4f()
    private val camPos        = Vector3f()
    private val tmpPos        = Vector3f()

    var iblBrdfTexture = "ibl_brdf_lut"
    var iblDiffuseTexture  = ""
    var iblSpecularTexture = ""
    var iblIntensity       = 1f

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
                wrapping = TextureWrapping.CLAMP_TO_EDGE,
                filter = TextureFilter.LINEAR,
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
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex > 0) return // Only once per frame

        // Camera position
        surface.camera.viewMatrix.invert(invViewMatrix)
        invViewMatrix.getTranslation(camPos)

        val hasDepthPrepass = surface.config.hasDepthPrepass
        val aoRenderer = surface.getRenderer<GtaoRenderer>()
        val aoTex = aoRenderer?.getAoRenderTexture()
        val texBank = engine.gfx.textureBank
        val envSpecularMipCount = engine.asset.getOrNull<Texture>(iblSpecularTexture)
            ?.let { texBank.getTextureArray(it) }?.mipLevels?.toFloat() ?: 1f

        program.bind()
        program.setUniformSamplerArrays(texBank.getAllTextureArrays())
        program.setUniformSampler("uGtaoTex", aoTex ?: texBank.getOrCreateFallbackTexture(WHITE))

        program.setUniform("uScreenSize", surface.config.width.toFloat(), surface.config.height.toFloat())
        program.setUniform("uViewProjection", surface.camera.viewProjectionMatrix)
        program.setUniform("uCameraPos", camPos)
        program.setUniform("uEnvSpecularMipCount", envSpecularMipCount)
        program.setUniform("uEnvIntensity", iblIntensity)
        program.setUniform("uAoIntensity", aoRenderer?.intensity ?: 0f)

        program.setTexture("uEnvDiffuseTex",  engine.asset.getOrNull(iblDiffuseTexture))
        program.setTexture("uEnvSpecularTex", engine.asset.getOrNull(iblSpecularTexture))
        program.setTexture("uEnvBrdfLutTex",  engine.asset.getOrNull(iblBrdfTexture))

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

    fun setCullMode(mode: CullMode)
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
}