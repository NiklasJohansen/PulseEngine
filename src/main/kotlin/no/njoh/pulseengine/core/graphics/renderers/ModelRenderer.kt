package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.MASK
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.OPAQUE
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.TRANSPARENT
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
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE

class ModelRenderer : Renderer()
{
    private lateinit var program: ShaderProgram

    private var readDrawCommands   = ArrayList<DrawCommand>(256)
    private var writeDrawCommands  = ArrayList<DrawCommand>(256)
    private val opaqueMeshes       = ArrayList<RenderItem>(256)
    private val maskedMeshes       = ArrayList<RenderItem>(256)
    private val transparentMeshes  = ArrayList<RenderItem>(256)

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
        writeDrawCommands = readDrawCommands.also { readDrawCommands = writeDrawCommands }
        writeDrawCommands.clear()
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
        program.setUniformSampler("gtaoTex", aoTex ?: texBank.getOrCreateFallbackTexture(WHITE))
        program.setUniform("screenSize", surface.config.width.toFloat(), surface.config.height.toFloat())
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        program.setUniform("cameraPos", camPos)
        program.setUniform("envSpecularMipCount", envSpecularMipCount)
        program.setUniform("envIntensity", iblIntensity)
        program.setUniform("aoIntensity", aoRenderer?.intensity ?: 0f)

        program.setTexture("envDiffuseTex",  engine.asset.getOrNull(iblDiffuseTexture))
        program.setTexture("envSpecularTex", engine.asset.getOrNull(iblSpecularTexture))
        program.setTexture("envBrdfLutTex",  engine.asset.getOrNull(iblBrdfTexture))

        // Build lists
        for (cmd in readDrawCommands)
        {
            val mesh = cmd.mesh

            for (subMesh in mesh.subMeshes)
            {
                val matInfo  = mesh.materials.getOrNull(subMesh.materialIndex)
                val material = matInfo?.name?.let { engine.asset.getOrNull<Material>(it) }
                val mode     = material?.blendMode ?: OPAQUE

                cmd.transform.getTranslation(tmpPos)
                val distSq = camPos.distanceSquared(tmpPos)
                val item = RenderItem(mesh, subMesh, material, cmd.transform, distSq)

                when (mode)
                {
                    TRANSPARENT  -> transparentMeshes += item
                    OPAQUE -> opaqueMeshes += item
                    MASK -> maskedMeshes += item
                }
            }
        }

        // -------- OPAQUE --------

        glEnable(GL_DEPTH_TEST)
        glDisable(GL_BLEND)

        glDepthFunc(if (hasDepthPrepass) GL_LEQUAL else GL_LESS)
        glDepthMask(!hasDepthPrepass) // Disable depth writes if depth prepass exists

        opaqueMeshes.forEachFast { drawItem(it) }

        // -------- MASK --------
        // Disable AO for masked and transparent meshes
        program.setUniformSampler("gtaoTex", texBank.getOrCreateFallbackTexture(WHITE))
        
        glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE)
        glDepthFunc(GL_LEQUAL)
        glDepthMask(true)

        maskedMeshes.forEachFast { drawItem(it) }

        glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE)
        
        // -------- TRANSPARENT --------

        if (transparentMeshes.isNotEmpty())
        {
            val func = surface.config.blendFunction
            glEnable(GL_BLEND)
            glBlendFunc(func.src, func.dest)
            glDepthMask(true)

            transparentMeshes.sortByDescending { it.distanceToCamera }
            transparentMeshes.forEachFast { drawItem(it) }
        }

        glDepthMask(true) // Restore for later renderers
        setCullMode(CullMode.BACK)
        opaqueMeshes.clear()
        maskedMeshes.clear()
        transparentMeshes.clear()
    }

    private fun drawItem(item: RenderItem)
    {
        val vao      = item.mesh.vao ?: return
        val subMesh  = item.subMesh
        val material = item.material
        val alphaCutoff = if (material?.blendMode == MASK) material.alphaCutoff else 0.0f

        program.setUniform("model",           item.transform)
        program.setUniform("alphaCutoff",     alphaCutoff)
        program.setTexture("albedoTex",       material?.albedo)
        program.setTexture("normalTex",       material?.normal)
        program.setTexture("aoMetalRoughTex", material?.aoMetalRough)
        program.setTexture("emissiveTex",     material?.emissive)

        setCullMode(material?.cullMode ?: CullMode.BACK)

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

    fun draw(mesh: Mesh, transform: Matrix4f)
    {
        writeDrawCommands += DrawCommand(mesh, transform)
        increaseBatchSize()
    }

    private data class DrawCommand(val mesh: Mesh, val transform: Matrix4f)

    private data class RenderItem(
        val mesh: Mesh,
        val subMesh: Mesh.SubMesh,
        val material: Material?,
        val transform: Matrix4f,
        val distanceToCamera: Float
    )
}