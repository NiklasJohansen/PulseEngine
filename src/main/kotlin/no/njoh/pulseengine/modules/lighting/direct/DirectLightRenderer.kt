package no.njoh.pulseengine.modules.lighting.direct

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureHandle
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import no.njoh.pulseengine.core.graphics.renderers.BatchRenderer
import no.njoh.pulseengine.core.graphics.api.CameraInternal
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateFrom
import org.lwjgl.opengl.GL31.*
import kotlin.math.max

class DirectLightRenderer : BatchRenderer()
{
    var ambientColor = Color(0.1f, 0.1f, 0.1f)
    var normalMapTextureHandle: TextureHandle? = null
    var occluderMapTextureHandle: TextureHandle? = null

    var xDrawOffset = 0f
    var yDrawOffset = 0f
    var gpuRenderTimeMs: Float = 0f

    private lateinit var vao: VertexArrayObject
    private lateinit var program: ShaderProgram
    private lateinit var vertexBuffer: StaticBufferObject
    private lateinit var lightBuffer: DoubleBufferedFloatObject
    private lateinit var edgeBuffer: DoubleBufferedFloatObject

    private var readLights = 0
    private var readEdges = 0
    private var writeLights = 0
    private var writeEdges = 0

    override fun init()
    {
        if (!this::program.isInitialized)
        {
            lightBuffer = DoubleBufferedFloatObject.createArrayBuffer()
            edgeBuffer = DoubleBufferedFloatObject.createShaderStorageBuffer(blockBinding = 0)
            vertexBuffer = StaticBufferObject.createArrayBuffer(floatArrayOf(
                0f, 0f, // Top-left vertex
                1f, 0f, // Top-right vertex
                0f, 1f, // Bottom-left vertex
                1f, 1f  // Bottom-right vertex
            ))
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/lighting/direct/light.vert",
                fragmentShaderFileName = "/pulseengine/shaders/lighting/direct/light.frag"
            )
        }

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
        program.bind()
        edgeBuffer.bind()
        vertexBuffer.bind()
        program.setVertexAttributeLayout(vertexLayout)
        lightBuffer.bind()
        program.setVertexAttributeLayout(instanceLayout)
        vao.release()
    }

    override fun onInitFrame()
    {
        lightBuffer.swapBuffers()
        edgeBuffer.swapBuffers()
        readLights = writeLights.also { writeLights = 0 }
        readEdges = writeEdges.also { writeEdges = 0 }
    }

    override fun onRenderBatch(surface: Surface, startIndex: Int, drawCount: Int)
    {
        if (readLights == 0)
        {
            gpuRenderTimeMs = 0f
            return
        }

        val cam = surface.camera as CameraInternal
        val zRotCamera = cam.rotation.z.interpolateFrom(cam.rotationLast.z)
        val renderStartTime = System.nanoTime()
        val texScale = surface.config.textureScale
        val view = surface.camera.viewMatrix
        var index = 0
        val size = readEdges * 4
        val buffer = edgeBuffer.readArray
        val xEdgeDrawOffset = xDrawOffset * texScale
        val yEdgeDrawOffset = yDrawOffset * texScale

        // Transform world coordinates of edges to screen space
        while (index < size)
        {
            val x0 = buffer[index]
            val y0 = buffer[index + 1]
            val x1 = buffer[index + 2]
            val y1 = buffer[index + 3]
            buffer[index + 0] = (view.m00() * x0 + view.m10() * y0 + view.m30()) * texScale - xEdgeDrawOffset
            buffer[index + 1] = (view.m01() * x0 + view.m11() * y0 + view.m31()) * texScale - yEdgeDrawOffset
            buffer[index + 2] = (view.m00() * x1 + view.m10() * y1 + view.m30()) * texScale - xEdgeDrawOffset
            buffer[index + 3] = (view.m01() * x1 + view.m11() * y1 + view.m31()) * texScale - yEdgeDrawOffset
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

        // Set up VAO, shader program and uniforms
        vao.bind()
        program.bind()
        program.setUniform("projection", surface.camera.projectionMatrix)
        program.setUniform("view", view)
        program.setUniform("edgeCount", readEdges)
        program.setUniform("ambientColor", ambientColor)
        program.setUniform("resolution", surface.config.width * texScale, surface.config.height * texScale)
        program.setUniform("textureScale", texScale)
        program.setUniform("drawOffset", xDrawOffset, yDrawOffset)
        program.setUniform("zRotation", zRotCamera)

        // Bind normal map texture if available
        normalMapTextureHandle?.let {
            program.setUniformSampler("normalMap", it)
            program.setUniform("hasNormalMap", 1f)
        } ?: program.setUniform("hasNormalMap", 0f)

        // Bind occluder map texture if available
        occluderMapTextureHandle?.let {
            program.setUniformSampler("occluderMap", it)
            program.setUniform("hasOccluderMap", 1f)
        } ?: program.setUniform("hasOccluderMap", 0f)

        // Perform draw call
        glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, readLights)

        // Reset
        vao.release()
        gpuRenderTimeMs = (System.nanoTime() - renderStartTime) / 1_000_000f
    }

    override fun destroy()
    {
        vertexBuffer.delete()
        lightBuffer.delete()
        edgeBuffer.delete()
        program.delete()
        vao.delete()
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
        lightType: DirectLightType,
        shadowType: DirectShadowType,
        spill: Float,
        edgeIndex: Int,
        edgeCount: Int
    ) {
        val shadowType = if (edgeCount == 0) DirectShadowType.NONE else shadowType
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
        writeLights++
        increaseBatchSize()
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
        writeEdges++
    }
}