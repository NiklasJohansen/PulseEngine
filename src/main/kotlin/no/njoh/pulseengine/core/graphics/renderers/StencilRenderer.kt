package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.graphics.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import org.lwjgl.opengl.GL20.*

class StencilRenderer : BatchRenderer()
{
    private lateinit var vao: VertexArrayObject
    private lateinit var program: ShaderProgram
    private lateinit var vertexBuffer: StaticBufferObject

    override fun init()
    {
        vao = VertexArrayObject.createAndBind()

        if (!this::program.isInitialized)
        {
            vertexBuffer = StaticBufferObject.createBuffer(floatArrayOf(
                0f, 0f, // Top-left vertex
                1f, 0f, // Top-right vertex
                0f, 1f, // Bottom-left vertex
                1f, 1f  // Bottom-right vertex
            ))
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/stencil.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/stencil.frag"
            )
        }

        program.bind()
        vertexBuffer.bind()
        program.setVertexAttributeLayout(VertexAttributeLayout().withAttribute("vertexPos", 2, GL_FLOAT))
        vao.release()
    }

    fun drawStencil(surface: Surface2D, x: Float, y: Float, width: Float, height: Float)
    {
        // Bind VAO and shader program
        vao.bind()

        // Set uniforms
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        program.setUniform("posAndSize", x, y, width, height)

        // Draw quad
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)

        // Release VAO and reset count
        vao.release()
    }

    override fun onRenderBatch(surface: Surface2D, startIndex: Int, drawCount: Int) { }

    override fun cleanUp()
    {
        vertexBuffer.delete()
        program.delete()
        vao.delete()
    }
}