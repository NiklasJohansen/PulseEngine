package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import org.lwjgl.opengl.ARBBaseInstance.glDrawArraysInstancedBaseInstance
import org.lwjgl.opengl.GL20.*

class BindlessTextureRenderer(
    private val initialCapacity: Int,
    private val context: RenderContextInternal,
    private val textureBank: TextureBank
) : BatchRenderer() {

    private lateinit var vao: VertexArrayObject
    private lateinit var program: ShaderProgram
    private lateinit var vertexBuffer: StaticBufferObject
    private lateinit var instanceBuffer: FloatBufferObject

    override fun init()
    {
        vao = VertexArrayObject.createAndBind()

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

        if (!this::program.isInitialized)
        {
            instanceBuffer = BufferObject.createArrayBuffer(initialCapacity * instanceLayout.strideInBytes)
            vertexBuffer = StaticBufferObject.createBuffer(floatArrayOf(
                0f, 0f, // Top-left vertex
                1f, 0f, // Top-right vertex
                0f, 1f, // Bottom-left vertex
                1f, 1f  // Bottom-right vertex
            ))
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/texture_bindless.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/texture_bindless.frag"
            )
        }

        program.bind()
        vertexBuffer.bind()
        program.setVertexAttributeLayout(vertexLayout)
        instanceBuffer.bind()
        program.setVertexAttributeLayout(instanceLayout)
        vao.release()
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

    override fun onRenderBatch(surface: Surface2D, startIndex: Int, drawCount: Int)
    {
        // Submit per-instance data to GPU
        if (startIndex == 0)
        {
            instanceBuffer.bind()
            instanceBuffer.submit()
            instanceBuffer.release()
        }

        // Bind VAO with buffers and attribute layout
        vao.bind()

        // Bind shader program and set uniforms
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)

        // Bind texture bank
        textureBank.bindAllTexturesTo(program)

        // Draw all instances
        glDrawArraysInstancedBaseInstance(GL_TRIANGLE_STRIP, 0, 4, drawCount, startIndex)

        // Release VAO and reset count
        vao.release()
    }

    override fun cleanUp()
    {
        vertexBuffer.delete()
        instanceBuffer.delete()
        program.delete()
        vao.delete()
    }
}