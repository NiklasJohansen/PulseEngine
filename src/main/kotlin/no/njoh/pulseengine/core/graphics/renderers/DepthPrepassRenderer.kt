package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawTriangleIndices
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.graphics.api.DrawList
import org.lwjgl.opengl.GL11.*

class DepthPrepassRenderer : Renderer()
{
    private lateinit var program: ShaderProgram

    private var readDrawLists  = ArrayList<DrawList>()
    private var writeDrawLists = ArrayList<DrawList>()

    override fun init(engine: PulseEngineInternal, surface: Surface) 
    {
        if (!this::program.isInitialized)
        {
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_depth.vert")),
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
        
        // Disable color writes and enable depth writes
        glColorMask(false, false, false, false)
        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)
        glDisable(GL_CULL_FACE)
        glViewport(0, 0, surface.config.width, surface.config.height)

        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)

        for (list in readDrawLists)
        {
            for (item in list.opaqueItems)
            {
                val vao = item.model.vao ?: continue
                val subMesh = item.subMesh

                program.setUniform("model", item.transform)

                drawTriangleIndices(vao, subMesh.indexStart, subMesh.indexCount)
            }
        }

        readDrawLists.clear()
        
        glColorMask(true, true, true, true)

        // Resolve depth to single sampled texture (if surface is using MSAA)
        surface.renderTarget.resolveDepth(engine)

        // Generate depth pyramid
        surface.getTextures().firstOrNullFast { it.attachment == DEPTH_TEXTURE }?.generateMips(engine)

        // Rebind render target for further rendering
        surface.renderTarget.begin()
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