package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import org.lwjgl.opengl.ARBBaseInstance.glDrawArraysInstancedBaseInstance
import org.lwjgl.opengl.GL20.*

class BindlessTextureRenderer(
    private val context: RenderContextInternal,
    private val textureBank: TextureBank
) : BatchRenderer() {

    private lateinit var vao: VertexArrayObject
    private lateinit var vertexBuffer: StaticBufferObject
    private lateinit var instanceBuffer: DoubleBufferedFloatObject
    private lateinit var program: ShaderProgram

    override fun init()
    {
        if (!this::program.isInitialized)
        {
            instanceBuffer = DoubleBufferedFloatObject.createArrayBuffer()
            vertexBuffer = StaticBufferObject.createQuadVertexArrayBuffer()
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/texture_bindless.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/texture_bindless.frag"
            )
        }

        val vertexLayout = VertexAttributeLayout()
            .withAttribute("vertexPos", 2, GL_FLOAT)

        val instanceLayout = VertexAttributeLayout()
            .withAttribute("worldPos", 3, GL_FLOAT, 1)
            .withAttribute("size", 2, GL_FLOAT, 1)
            .withAttribute("origin", 2, GL_FLOAT, 1)
            .withAttribute("rotation", 1, GL_FLOAT, 1)
            .withAttribute("cornerRadius", 1, GL_FLOAT, 1)
            .withAttribute("uvMin", 2, GL_FLOAT, 1)
            .withAttribute("uvMax", 2, GL_FLOAT, 1)
            .withAttribute("tiling", 2, GL_FLOAT, 1)
            .withAttribute("color", 1, GL_FLOAT, 1)
            .withAttribute("textureHandle", 1, GL_FLOAT, 1)

        vao = VertexArrayObject.createAndBind()
        program.bind()
        vertexBuffer.bind()
        program.setVertexAttributeLayout(vertexLayout)
        instanceBuffer.bind()
        program.setVertexAttributeLayout(instanceLayout)
        vao.release()
    }

    override fun onInitFrame()
    {
        instanceBuffer.swapBuffers()
    }

    override fun onRenderBatch(surface: Surface2D, startIndex: Int, drawCount: Int)
    {
        if (startIndex == 0)
        {
            instanceBuffer.bind()
            instanceBuffer.submit()
            instanceBuffer.release()
        }

        vao.bind()
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        textureBank.bindAllTexturesTo(program)
        glDrawArraysInstancedBaseInstance(GL_TRIANGLE_STRIP, 0, 4, drawCount, startIndex)
        vao.release()
    }

    override fun cleanUp()
    {
        vertexBuffer.delete()
        instanceBuffer.delete()
        program.delete()
        vao.delete()
    }

    fun drawTexture(texture: Texture, x: Float, y: Float, w: Float, h: Float, rot: Float, xOrigin: Float, yOrigin: Float, cornerRadius: Float)
    {
        instanceBuffer.fill(17)
        {
            put(x)
            put(y)
            put(context.depth)
            put(w)
            put(h)
            put(xOrigin)
            put(yOrigin)
            put(rot)
            put(cornerRadius)
            put(texture.uMin)
            put(texture.vMin)
            put(texture.uMax)
            put(texture.vMax)
            put(1f) // U-tiling
            put(1f) // V-tiling
            put(context.drawColor)
            put(texture.handle.toFloat())
        }

        increaseBatchSize()
        context.increaseDepth()
    }

    fun drawTexture(
        texture: Texture,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        rot: Float,
        xOrigin: Float,
        yOrigin: Float,
        cornerRadius: Float,
        uMin: Float,
        vMin: Float,
        uMax: Float,
        vMax: Float,
        uTiling: Float,
        vTiling: Float
    ) {
        instanceBuffer.fill(17)
        {
            put(x)
            put(y)
            put(context.depth)
            put(w)
            put(h)
            put(xOrigin)
            put(yOrigin)
            put(rot)
            put(cornerRadius)
            put(texture.uMax * uMin)
            put(texture.vMax * vMin)
            put(texture.uMax * uMax)
            put(texture.vMax * vMax)
            put(uTiling)
            put(vTiling)
            put(context.drawColor)
            put(texture.handle.toFloat())
        }

        increaseBatchSize()
        context.increaseDepth()
    }
}