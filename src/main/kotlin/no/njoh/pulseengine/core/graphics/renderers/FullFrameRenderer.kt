package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.StaticBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawTriangleVertices
import org.lwjgl.opengl.GL11.*

class FullFrameRenderer(private val program: ShaderProgram)
{
    private lateinit var vao: VertexArrayObject
    private lateinit var vertexBuffer: StaticBufferObject
    private lateinit var vertexLayout: VertexAttributeLayout

    fun init()
    {
        if (!this::vao.isInitialized)
        {
            vertexBuffer = StaticBufferObject.createFullscreenUvTriangleArrayBuffer()
            vertexLayout = VertexAttributeLayout()
                .withAttribute("position", 2, GL_FLOAT)
                .withAttribute("texCoord", 2, GL_FLOAT)
        }
        else vao.destroy()

        vao = VertexArrayObject.createAndBind()
        program.bind()
        vertexBuffer.bind()
        vertexLayout.bind(program)
        vao.release()
    }

    fun drawTexture(texture: RenderTexture)
    {
        program.bind()
        program.setUniformSampler("tex", texture)
        drawTriangleVertices(vao, 0, 3)
    }

    fun draw()
    {
        drawTriangleVertices(vao, 0, 3)
    }

    fun destroy()
    {
        if (this::vao.isInitialized) vao.destroy()
        if (this::vertexBuffer.isInitialized) vertexBuffer.destroy()
    }
}