package no.njoh.pulseengine.modules.lighting

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import no.njoh.pulseengine.core.graphics.renderers.BatchRenderer
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL30.GL_TEXTURE0
import org.lwjgl.opengl.GL31.*
import kotlin.math.max

class LightRenderer(
    private val initialLightCapacity: Int = 100,
    private val initialEdgeCapacity: Int = 4000
) : BatchRenderer {

    var ambientColor = Color(0.1f, 0.1f, 0.1f)
    var normalMapSurface: Surface2D? = null
    var occluderMapSurface: Surface2D? = null
    var xDrawOffset = 0f
    var yDrawOffset = 0f
    var renderTimeMs: Float = 0f

    private lateinit var vao: VertexArrayObject
    private lateinit var program: ShaderProgram
    private lateinit var vertexBuffer: StaticBufferObject
    private lateinit var lightBuffer: FloatBufferObject
    private lateinit var edgeBuffer: FloatBufferObject

    private var lights = 0
    private var edges = 0
    private val edgeBlockByteCount = 16L // 4 x 4-byte floats

    override fun init()
    {
        val vertexLayout = VertexAttributeLayout()
            .withAttribute("vertexPos", 2, GL_FLOAT)

        val instanceLayout = VertexAttributeLayout()
            .withAttribute("position",3, GL_FLOAT, 1)
            .withAttribute("radius", 1, GL_FLOAT, 1)
            .withAttribute("directionAngle", 1, GL_FLOAT, 1)
            .withAttribute("coneAngle",1, GL_FLOAT, 1)
            .withAttribute("size",1, GL_FLOAT, 1)
            .withAttribute("color",1, GL_FLOAT, 1)
            .withAttribute("intensity",1, GL_FLOAT, 1)
            .withAttribute("spill",1, GL_FLOAT, 1)
            .withAttribute("flags",1, GL_FLOAT, 1)
            .withAttribute("edgeIndex",1, GL_FLOAT, 1)
            .withAttribute("edgeCount",1, GL_FLOAT, 1)

        vao = VertexArrayObject.createAndBind()

        if (!this::program.isInitialized)
        {
            lightBuffer = BufferObject.createArrayBuffer(initialLightCapacity * instanceLayout.strideInBytes)
            edgeBuffer = BufferObject.createShaderStorageBuffer(initialEdgeCapacity * edgeBlockByteCount, blockBinding = 0)
            vertexBuffer = StaticBufferObject.createBuffer(floatArrayOf(
                0f, 0f, // Top-left vertex
                1f, 0f, // Top-right vertex
                0f, 1f, // Bottom-left vertex
                1f, 1f  // Bottom-right vertex
            ))
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/effects/lighting3.vert",
                fragmentShaderFileName = "/pulseengine/shaders/effects/lighting3.frag"
            )
        }

        program.bind()
        edgeBuffer.bind()
        vertexBuffer.bind()
        program.defineVertexAttributeLayout(vertexLayout)
        lightBuffer.bind()
        program.defineVertexAttributeLayout(instanceLayout)
        vao.release()
    }

    fun addLight(
        x: Float,
        y: Float,
        z: Float,
        radius: Float,
        direction: Float,
        coneAngle: Float,
        sourceSize: Float,
        intensity: Float,
        red: Float,
        green: Float,
        blue: Float,
        lightType: LightType,
        shadowType: ShadowType,
        spill: Float,
        edgeIndex: Int,
        edgeCount: Int
    ) {
        val shadowType = if (edgeCount == 0) ShadowType.NONE else shadowType
        val r = (red * 255).toInt()
        val g = (green * 255).toInt()
        val b = (blue * 255).toInt()
        val rgba = Float.fromBits((r shl 24) or (g shl 16) or (b shl 8) or 255)
        val flags = Float.fromBits(lightType.flag or shadowType.flag)
        lightBuffer.fill(13)
        {
            put(x)
            put(y)
            put(z)
            put(radius)
            put(direction)
            put(coneAngle)
            put(max(sourceSize, 0.01f))
            put(rgba)
            put(intensity)
            put(spill)
            put(flags)
            put(edgeIndex.toFloat())
            put(edgeCount.toFloat())
        }
        lights++
    }

    fun addEdge(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        edgeBuffer.fill(4)
        {
            put(x0)
            put(y0)
            put(x1)
            put(y1)
        }
        edges++
    }

    override fun render(surface: Surface2D)
    {
        if (lights == 0)
        {
            renderTimeMs = 0f
            return
        }

        val renderStartTime = System.nanoTime()
        val texScale = surface.context.textureScale
        val view = surface.camera.viewMatrix
        var index = 0
        val size = edgeBuffer.count
        val buffer = edgeBuffer.backingBuffer
        val xEdgeDrawOffset = xDrawOffset * texScale
        val yEdgeDrawOffset = yDrawOffset * texScale

        while (index < size)
        {
            val x0 = buffer[index]
            val y0 = buffer[index + 1]
            val x1 = buffer[index + 2]
            val y1 = buffer[index + 3]
            // Transform world coordinates to screen space
            buffer.put(index + 0, (view.m00() * x0 + view.m10() * y0 + view.m30()) * texScale - xEdgeDrawOffset)
            buffer.put(index + 1, (view.m01() * x0 + view.m11() * y0 + view.m31()) * texScale - yEdgeDrawOffset)
            buffer.put(index + 2, (view.m00() * x1 + view.m10() * y1 + view.m30()) * texScale - xEdgeDrawOffset)
            buffer.put(index + 3, (view.m01() * x1 + view.m11() * y1 + view.m31()) * texScale - yEdgeDrawOffset)
            index += 4
        }

        // Submit edge buffer data to GPU
        edgeBuffer.bind()
        edgeBuffer.submit()
        edgeBuffer.release()

        // Submit light instance data to GPU
        lightBuffer.bind()
        lightBuffer.submit()
        lightBuffer.release()

        // Bind normal map texture if available
        var hasNormalMap = 0f
        normalMapSurface?.let {
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, it.getTexture().id)
            hasNormalMap = 1f
        }

        // Bind normal map texture if available
        var hasOccluderMap = 0f
        occluderMapSurface?.let {
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, it.getTexture().id)
            hasOccluderMap = 1f
        }

        vao.bind()
        program.bind()
        program.setUniform("projection", surface.camera.projectionMatrix)
        program.setUniform("view", view)
        program.setUniform("edgeCount", edges)
        program.setUniform("ambientColor", ambientColor)
        program.setUniform("resolution", surface.width * texScale, surface.height * texScale)
        program.setUniform("textureScale", texScale)
        program.setUniform("drawOffset", xDrawOffset, yDrawOffset)
        program.setUniform("hasNormalMap", hasNormalMap)
        program.setUniform("hasOccluderMap", hasOccluderMap)

        glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, lights)

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, 0)
        vao.release()
        edges = 0
        lights = 0
        renderTimeMs = (System.nanoTime() - renderStartTime) / 1_000_000f
    }

    override fun cleanUp()
    {
        vertexBuffer.delete()
        lightBuffer.delete()
        edgeBuffer.delete()
        program.delete()
        vao.delete()
    }
}