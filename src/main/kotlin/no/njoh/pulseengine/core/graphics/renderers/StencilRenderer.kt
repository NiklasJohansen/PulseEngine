package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import no.njoh.pulseengine.core.graphics.surface.Surface
import org.lwjgl.opengl.GL20.*

class StencilRenderer : BatchRenderer()
{
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: StaticBufferObject
    private lateinit var program: ShaderProgram

    override fun init(engine: PulseEngineInternal)
    {
        if (!this::program.isInitialized)
        {
            vbo = StaticBufferObject.createQuadVertexArrayBuffer()
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/stencil.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/stencil.frag"))
            )
        }

        val layout = VertexAttributeLayout()
            .withAttribute("vertexPos", 2, GL_FLOAT)

        vao = VertexArrayObject.createAndBind()
        vbo.bind()
        program.bind()
        program.setVertexAttributeLayout(layout)
        vao.release()
    }

    override fun onInitFrame() { }

    fun drawStencil(surface: Surface, x: Float, y: Float, width: Float, height: Float)
    {
        vao.bind()
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        program.setUniform("posAndSize", x, y, width, height)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        vao.release()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: Surface, startIndex: Int, drawCount: Int) { }

    override fun destroy()
    {
        vao.destroy()
        vbo.destroy()
        program.destroy()
    }
}