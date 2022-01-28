package no.njoh.pulseengine.modules.scene.systems.lighting

import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.*
import no.njoh.pulseengine.modules.graphics.objects.BufferObject
import no.njoh.pulseengine.modules.graphics.objects.FloatBufferObject
import no.njoh.pulseengine.modules.graphics.postprocessing.SinglePassEffect
import no.njoh.pulseengine.util.BufferExtensions.putAll
import org.joml.Math
import org.joml.Vector4f

class LightingPostProcessingEffect (
    private val camera: Camera,
    private val normalSurface: Surface2D,
    private val lightOccluderSurface: Surface2D
): SinglePassEffect() {

    var ambientColor = Color(0.1f, 0.1f, 0.1f)
    var lightBleed = 0.1f

    private lateinit var lightUbo: FloatBufferObject
    private lateinit var edgeUbo: FloatBufferObject

    private var lights = 0
    private var edges = 0
    private var pos0 = Vector4f(0f, 0f, 0f, 1f)
    private var pos1 = Vector4f(0f, 0f, 0f, 1f)
    private var scale = 1f

    private val MAX_LIGHTS = 1000
    private val MAX_EDGES = 1000
    private val LIGHT_BLOCK_SIZE = 12L
    private val EDGE_BLOCK_SIZE = 4L

    override fun loadShaderProgram(): ShaderProgram =
        ShaderProgram.create(
            vertexShaderFileName = "/pulseengine/shaders/effects/lighting.vert",
            fragmentShaderFileName = "/pulseengine/shaders/effects/lighting.frag"
        )

    override fun init()
    {
        super.init()

        program.bind()
        program.assignUniformBlockBinding("LightBlock", 0)
        program.assignUniformBlockBinding("EdgeBlock", 1)

        if (!this::lightUbo.isInitialized)
            lightUbo = BufferObject.createShaderStorageBuffer(MAX_LIGHTS * LIGHT_BLOCK_SIZE, 0)

        if (!this::edgeUbo.isInitialized)
            edgeUbo = BufferObject.createShaderStorageBuffer(MAX_EDGES * EDGE_BLOCK_SIZE, 1)
    }

    override fun cleanUp()
    {
        super.cleanUp()
        lightUbo.delete()
        edgeUbo.delete()
    }

    override fun applyEffect(texture: Texture): Texture
    {
        lightUbo.bind()
        lightUbo.submit()
        lightUbo.release()

        edgeUbo.bind()
        edgeUbo.submit()
        edgeUbo.release()

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("lightCount", lights)
        program.setUniform("edgeCount", edges)
        program.setUniform("resolution", texture.width.toFloat(), texture.height.toFloat())
        program.setUniform("ambientColor", ambientColor)
        program.setUniform("lightBleed", lightBleed)

        renderer.render(texture, normalSurface.getTexture(), lightOccluderSurface.getTexture())

        fbo.release()
        lights = 0
        edges = 0

        return fbo.texture
    }

    fun addLight(
        x: Float,
        y: Float,
        z: Float,
        radius: Float,
        direction: Float,
        coneAngle: Float,
        size: Float,
        intensity: Float,
        red: Float,
        green: Float,
        blue: Float,
        lightType: LightType,
        shadowType: ShadowType
    ) {
        if (lights == 0)
        {
            val vm = camera.viewMatrix
            scale = Math.sqrt(vm.m00() * vm.m00() + vm.m01() * vm.m01() + vm.m02() * vm.m02())
        }

        val flags = lightType.flag or shadowType.flag

        pos0.set(x, y, 0f).mul(camera.viewMatrix)
        lightUbo.fill(12)
        {
            putAll(pos0.x, pos0.y, z * scale, radius * scale, direction, coneAngle, size * scale, red, green, blue, intensity, Float.fromBits(flags))
        }
        lights++
    }

    fun addEdge(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        pos0.set(x0, y0, 0f).mul(camera.viewMatrix)
        pos1.set(x1, y1, 0f).mul(camera.viewMatrix)
        lightUbo.fill(4)
        {
            putAll(pos0.x, pos0.y, pos1.x, pos1.y)
        }
        edges++
    }
}