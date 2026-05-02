package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Attachment.DEPTH_TEXTURE
import no.njoh.pulseengine.core.graphics.api.DrawList
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawTriangleIndices
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL11.*

class DepthPrepassRenderer : Renderer()
{
    private lateinit var staticProgram: ShaderProgram
    private lateinit var skinnedProgram: ShaderProgram
    private var currentProgram = null as ShaderProgram?
    private var currentCullMode = null as CullMode?

    private var readDrawLists  = ArrayList<DrawList>()
    private var writeDrawLists = ArrayList<DrawList>()
    private val warnedBoneLimitModels = HashSet<String>()

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::staticProgram.isInitialized)
        {
            staticProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_depth.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_depth.frag"))
            )
            skinnedProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_depth_skinned.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_depth.frag"))
            )
        }
    }

    override fun onInitFrame()
    {
        writeDrawLists = readDrawLists.also { readDrawLists = writeDrawLists }
        writeDrawLists.clear()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        surface.config.hasDepthPrepass = true

        glColorMask(false, false, false, false)
        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LESS)
        glViewport(0, 0, surface.config.width, surface.config.height)

        staticProgram.bind()
        staticProgram.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        staticProgram.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        skinnedProgram.bind()
        skinnedProgram.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        skinnedProgram.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        currentProgram = null

        for (list in readDrawLists)
        {
            for (item in list.opaqueItems) drawItem(item)
            for (item in list.maskedItems) drawItem(item)
        }

        readDrawLists.clear()
        currentProgram = null
        currentCullMode = null

        glColorMask(true, true, true, true)
        glDisable(GL_CULL_FACE)
        surface.renderTarget.resolveDepth(engine)
        surface.getTextures().firstOrNullFast { it.attachment == DEPTH_TEXTURE }?.generateMips(engine)
        surface.renderTarget.begin()
    }

    override fun destroy()
    {
        staticProgram.destroy()
        skinnedProgram.destroy()
    }

    fun draw(drawList: DrawList)
    {
        writeDrawLists += drawList
        increaseBatchSize()
    }

    private fun drawItem(item: DrawList.RenderItem)
    {
        val vao = item.model.vao ?: return
        val program = getProgramFor(item.model)
        val alphaCutoff = if (item.material?.blendMode == no.njoh.pulseengine.core.asset.types.Material.BlendMode.MASK) item.material.alphaCutoff else 0f

        if (program != currentProgram)
        {
            program.bind()
            currentProgram = program
        }

        if (program === skinnedProgram)
            skinnedProgram.setUniform("uBoneMatrices", item.boneMatrices ?: emptyArray())

        program.setUniform("model", item.transform)
        program.setUniform("uAlphaCutoff", alphaCutoff)
        program.setTexture("uAlbedoTex", item.material?.albedo)
        setCullMode(item.material?.cullMode ?: CullMode.BACK)
        drawTriangleIndices(vao, item.subMesh.indexStart, item.subMesh.indexCount)
    }

    private fun getProgramFor(model: Model): ShaderProgram
    {
        val canSkin = model.hasBones && model.bones.isNotEmpty() && model.bones.size <= ModelRenderer.MAX_SKINNING_BONES

        if (!canSkin)
        {
            if (model.hasBones && model.bones.size > ModelRenderer.MAX_SKINNING_BONES && warnedBoneLimitModels.add(model.name))
                Logger.warn { "Model '${model.name}' has ${model.bones.size} bones, but the shader limit is ${ModelRenderer.MAX_SKINNING_BONES}. Rendering depth without skinning." }
            return staticProgram
        }

        return skinnedProgram
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

    private fun ShaderProgram.setTexture(name: String, tex: Texture?)
    {
        if (tex != null)
            setUniform(name, tex.handle.samplerIndex.toFloat(), tex.handle.textureIndex.toFloat(), tex.uMax, tex.vMax)
        else
            setUniform(name, -1f, 0f, 0f, 0f)
    }
}
