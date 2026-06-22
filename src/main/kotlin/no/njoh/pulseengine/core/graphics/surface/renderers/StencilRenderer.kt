package no.njoh.pulseengine.core.graphics.surface.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.shader.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.gpu.buffer.StaticBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.VertexArrayObject
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawTriangleStripVertices
import org.lwjgl.opengl.GL20.*

class StencilRenderer : Renderer()
{
    override val order: Int = 70

    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: StaticBufferObject
    private lateinit var program: ShaderProgram

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::program.isInitialized)
        {
            vbo = StaticBufferObject.createQuadVertexArrayBuffer()
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/stencil.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/stencil.frag"))
            )
        }

        vao = VertexArrayObject.createAndBind()
        vbo.bind()
        program.bind()
        VertexAttributeLayout().withAttribute("vertexPos", 2, GL_FLOAT).bind(program)
        vao.release()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int) { }

    fun drawStencil(surface: Surface, x: Float, y: Float, width: Float, height: Float)
    {
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        program.setUniform("posAndSize", x, surface.config.height - (y + height), width, height)
        drawTriangleStripVertices(vao, 0, 4)
    }

    override fun destroy(engine: PulseEngineInternal)
    {
        vao.destroy()
        vbo.destroy()
        program.destroy()
    }
}