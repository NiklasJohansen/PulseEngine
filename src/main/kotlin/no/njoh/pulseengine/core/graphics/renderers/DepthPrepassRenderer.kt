package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.OPAQUE
import no.njoh.pulseengine.core.asset.types.Mesh
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawTriangleIndices
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.*

class DepthPrepassRenderer : Renderer()
{
    private lateinit var program: ShaderProgram

    private var readCommands  = ArrayList<DrawCommand>(256)
    private var writeCommands = ArrayList<DrawCommand>(256)

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
        writeCommands = readCommands.also { readCommands = writeCommands }
        writeCommands.clear()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int) 
    {
        if (startIndex != 0) return

        surface.config.hasDepthPrepass = true
        
        // Disable color writes and enable depth writes
        glColorMask(false, false, false, false)
        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)

        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)

        for (cmd in readCommands)
        {
            val vao = cmd.mesh.vao ?: continue

            for (subMesh in cmd.mesh.subMeshes)
            {
                val matInfo = cmd.mesh.materials.getOrNull(subMesh.materialIndex)
                val material = matInfo?.name?.let { engine.asset.getOrNull<Material>(it) }
                val blendMode = material?.blendMode ?: OPAQUE

                if (blendMode != OPAQUE)
                    continue // skip MASK + BLEND

                program.setUniform("model", cmd.transform)

                drawTriangleIndices(vao, subMesh.indexStart, subMesh.indexCount)
            }
        }

        glColorMask(true, true, true, true)

        // Resolve depth to single sampled texture (if surface is using MSAA)
        surface.renderTarget.resolveDepth()

        // Generate depth pyramid
        surface.getTextures().firstOrNullFast { it.attachment == DEPTH_TEXTURE }?.generateMips(engine)

        // Rebind render target for further rendering
        surface.renderTarget.begin()
    }

    override fun destroy()
    {
        program.destroy()
    }

    fun draw(mesh: Mesh, transform: Matrix4f) 
    {
        writeCommands += DrawCommand(mesh, transform)
        increaseBatchSize()
    }

    private data class DrawCommand(val mesh: Mesh, val transform: Matrix4f)
}