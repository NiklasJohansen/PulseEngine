package engine.modules.graphics.postprocessing.effects

import engine.data.Texture
import engine.modules.graphics.CameraInterface
import engine.modules.graphics.FloatBufferObject
import engine.modules.graphics.ShaderProgram
import engine.modules.graphics.VertexBufferObject
import engine.modules.graphics.postprocessing.SinglePassEffect
import org.joml.Math
import org.joml.Vector4f
import org.lwjgl.opengl.ARBUniformBufferObject.GL_UNIFORM_BUFFER
import org.lwjgl.opengl.ARBUniformBufferObject.glBindBufferBase

class LightingEffect (
    val camera: CameraInterface
): SinglePassEffect() {

    private lateinit var lightUbo: FloatBufferObject
    private lateinit var edgeUbo: FloatBufferObject

    private var lights = 0
    private var edges = 0
    private var pos0 = Vector4f(0f, 0f, 0f, 1f)
    private var pos1 = Vector4f(0f, 0f, 0f, 1f)
    private var scale = 1f

    override fun loadShaderProgram(): ShaderProgram =
        ShaderProgram.create(
            vertexShaderFileName = "/engine/shaders/effects/lighting.vert",
            fragmentShaderFileName = "/engine/shaders/effects/lighting.frag"
        )

    override fun init()
    {
        super.init()
        initialize()
    }

    private fun initialize()
    {
        program.bind()
        program.assignUniformBlockBinding("LightBlock", 1)
            .let { index ->
                lightUbo = VertexBufferObject.createAndBindUniformBuffer(8 * 1000, index)
            }
        program.assignUniformBlockBinding("EdgeBlock", 2)
            .let { index ->
                edgeUbo = VertexBufferObject.createAndBindUniformBuffer(8 * 1000, index)
            }
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
        glBindBufferBase(GL_UNIFORM_BUFFER, 1, lightUbo.id) // TODO: This should be handled in buffer class
        lightUbo.flush()
        lightUbo.release()

        edgeUbo.bind()
        glBindBufferBase(GL_UNIFORM_BUFFER, 2, edgeUbo.id)
        edgeUbo.flush()
        edgeUbo.release()

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("lightCount", lights)
        program.setUniform("edgeCount", edges)
        program.setUniform("resolution", texture.width.toFloat(), texture.height.toFloat())

        renderer.render(texture)
        fbo.release()
        lights = 0
        edges = 0

        return fbo.texture
    }

    fun addLight(x: Float, y: Float, radius: Float, intensity: Float, type: Float, red: Float, green: Float, blue: Float)
    {
        if(lights == 0)
        {
            val vm = camera.viewMatrix
            scale = Math.sqrt(vm.m00() * vm.m00() + vm.m01() * vm.m01() + vm.m02() * vm.m02())
        }

        pos0.set(x, y, 0f).mul(camera.viewMatrix)
        lightUbo.put(pos0.x, pos0.y, radius * scale, intensity, type, red, green, blue)
        lights++
    }

    fun addEdge(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        pos0.set(x0, y0, 0f).mul(camera.viewMatrix)
        pos1.set(x1, y1, 0f).mul(camera.viewMatrix)
        edgeUbo.put(pos0.x, pos0.y, pos1.x, pos1.y)
        edges++
    }
}