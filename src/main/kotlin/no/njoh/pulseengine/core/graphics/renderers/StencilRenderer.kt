package no.njoh.pulseengine.core.graphics.renderers

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

    override fun init()
    {
        if (!this::program.isInitialized)
        {
            vbo = StaticBufferObject.createQuadVertexArrayBuffer()
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/stencil.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/stencil.frag"
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

    override fun onRenderBatch(surface: Surface, startIndex: Int, drawCount: Int) { }

    override fun destroy()
    {
        vao.delete()
        vbo.delete()
        program.delete()
    }
}