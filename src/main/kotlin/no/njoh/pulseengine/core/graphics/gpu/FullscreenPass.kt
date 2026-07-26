package no.njoh.pulseengine.core.graphics.gpu

import no.njoh.pulseengine.core.graphics.gpu.buffer.StaticBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.VertexArrayObject
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.shader.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.gpu.texture.Attachment.*
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureAlphaMode.*
import no.njoh.pulseengine.core.graphics.util.DrawUtils
import org.lwjgl.opengl.GL11

class FullscreenPass(private val program: ShaderProgram)
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
                .withAttribute("position", 2, GL11.GL_FLOAT)
                .withAttribute("texCoord", 2, GL11.GL_FLOAT)
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
        program.setUniform("isDepthTexture", texture.attachment == DEPTH_TEXTURE)
        program.setUniform("isPremultipliedAlpha", texture.alphaMode == PREMULTIPLIED)
        DrawUtils.drawTriangleVertices(vao, 0, 3)
    }

    fun draw()
    {
        DrawUtils.drawTriangleVertices(vao, 0, 3)
    }

    fun destroy()
    {
        if (this::vao.isInitialized) vao.destroy()
        if (this::vertexBuffer.isInitialized) vertexBuffer.destroy()
    }
}