package no.njoh.pulseengine.modules.lighting.global

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import no.njoh.pulseengine.core.graphics.renderers.BatchRenderer
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceConfigInternal
import no.njoh.pulseengine.core.shared.utils.Extensions.component1
import no.njoh.pulseengine.core.shared.utils.Extensions.component2
import org.joml.Vector2f
import org.lwjgl.opengl.ARBBaseInstance.glDrawArraysInstancedBaseInstance
import org.lwjgl.opengl.GL20.*

class GiSceneRenderer(private val config: SurfaceConfigInternal) : BatchRenderer()
{
    private lateinit var vao: VertexArrayObject
    private lateinit var vertexBuffer: StaticBufferObject
    private lateinit var instanceBuffer: DoubleBufferedFloatObject
    private lateinit var program: ShaderProgram

    var fixJitter = false

    override fun init(engine: PulseEngineInternal)
    {
        if (!this::program.isInitialized)
        {
            instanceBuffer = DoubleBufferedFloatObject.createArrayBuffer()
            vertexBuffer = StaticBufferObject.createQuadVertexArrayBuffer()
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/global/scene.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/lighting/global/scene.frag"))
            )
        }

        val vertexLayout = VertexAttributeLayout()
            .withAttribute("vertexPos", 2, GL_FLOAT)

        val instanceLayout = VertexAttributeLayout()
            .withAttribute("worldPos", 3, GL_FLOAT, 1)
            .withAttribute("size", 2, GL_FLOAT, 1)
            .withAttribute("angle", 1, GL_FLOAT, 1)
            .withAttribute("cornerRadius", 1, GL_FLOAT, 1)
            .withAttribute("color", 1, GL_FLOAT, 1)
            .withAttribute("intensity", 1, GL_FLOAT, 1)
            .withAttribute("coneAngle", 1, GL_FLOAT, 1)
            .withAttribute("radius", 1, GL_FLOAT, 1)

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

    override fun onRenderBatch(engine: PulseEngineInternal, surface: Surface, startIndex: Int, drawCount: Int)
    {
        if (startIndex == 0)
        {
            instanceBuffer.bind()
            instanceBuffer.submit()
            instanceBuffer.release()
        }

        val (xPixelOffset, yPixelOffset) = calculatePixelOffset(surface)
        val xDrawOffset = if (fixJitter) xPixelOffset / (surface.config.width  * 0.5f) else 0f
        val yDrawOffset = if (fixJitter) yPixelOffset / (surface.config.height * 0.5f) else 0f

        vao.bind()
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        program.setUniform("drawOffset", xDrawOffset, yDrawOffset)
        program.setUniform("resolution", surface.config.width.toFloat() * surface.config.textureScale, surface.config.height.toFloat() * surface.config.textureScale)
        program.setUniform("camScale", surface.camera.scale.x)

        glDrawArraysInstancedBaseInstance(GL_TRIANGLE_STRIP, 0, 4, drawCount, startIndex)
        vao.release()
    }

    override fun destroy()
    {
        vertexBuffer.delete()
        instanceBuffer.delete()
        program.delete()
        vao.delete()
    }

    fun drawLight(x: Float, y: Float, w: Float, h: Float, angle: Float, cornerRadius: Float, intensity: Float, coneAngle: Float, radius: Float)
    {
        instanceBuffer.fill(11)
        {
            put(x, y, config.currentDepth)
            put(w, h)
            put(angle)
            put(cornerRadius)
            put(config.currentDrawColor)
            put(intensity)
            put(coneAngle)
            put(radius)
        }
        increaseBatchSize()
        config.increaseDepth()
    }

    fun drawOccluder(x: Float, y: Float, w: Float, h: Float, angle: Float, cornerRadius: Float, edgeLight: Float)
    {
        instanceBuffer.fill(11)
        {
            put(x, y, config.currentDepth)
            put(w, h)
            put(angle)
            put(cornerRadius)
            put(config.currentDrawColor)
            put(0f)
            put(360f)
            put(edgeLight)
        }
        increaseBatchSize()
        config.increaseDepth()
    }

    companion object
    {
        private val OFFSET = Vector2f()

        fun calculatePixelOffset(surface: Surface): Vector2f
        {
            val pixelSize = 1f / surface.config.textureScale
            val viewMatrix = surface.camera.viewMatrix
            val xTranslation = -viewMatrix.m30()
            val yTranslation = viewMatrix.m31()
            val xOffset = xTranslation % pixelSize
            val yOffset = yTranslation % pixelSize
            return OFFSET.set(xOffset, yOffset)
        }
    }
}