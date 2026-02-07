package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.DrawList
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.StaticBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawTriangleIndices
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.GL_BACK
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_LEQUAL
import org.lwjgl.opengl.GL11.glColorMask
import org.lwjgl.opengl.GL11.glCullFace
import org.lwjgl.opengl.GL11.glDepthFunc
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11C.GL_DEPTH_TEST
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

class ShadowMapRenderer(
    val lightColor: Color               = Color(1f, 1f, 1f),
    var lightIntensity: Float           = 1f,
    var lightRadius: Float              = 1f,
    var lightDirection: Vector3f        = Vector3f(0f, -1f, 0f),
    var shadowMapResolution: Int        = 4096,
    var shadowMapSizeMeters: Float      = 15f,
    var shadowContactHardening: Boolean = true,
) : Renderer() {

    var nearPlane = 0.1f
    var farPlane  = 200f

    private lateinit var program: ShaderProgram
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: StaticBufferObject

    private var readDrawLists  = ArrayList<DrawList>()
    private var writeDrawLists = ArrayList<DrawList>()
    private var readViewProjectionMatrix = Matrix4f()
    private var writeViewProjectionMatrix = Matrix4f()
    
    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::program.isInitialized)
        {
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/shadow.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/shadow.frag"))
            )
            vbo = StaticBufferObject.createFullscreenUvTriangleArrayBuffer()
        }

        vao = VertexArrayObject.createAndBind()
        vbo.bind()
        program.bind()
        VertexAttributeLayout().withAttribute("position", 2, GL_FLOAT).bind(program)
        vao.release()
    }

    override fun onInitFrame()
    {
        readViewProjectionMatrix.set(writeViewProjectionMatrix)
        readDrawLists = writeDrawLists.also { writeDrawLists = readDrawLists }
        writeDrawLists.clear()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glColorMask(false, false, false, false)
        
        program.bind()
        program.setUniform("viewProjection", readViewProjectionMatrix)

        for (list in readDrawLists)
        {
            for (item in list.opaqueItems)
            {
                val vao = item.model.vao ?: continue

                program.setUniform("model", item.transform)

                drawTriangleIndices(vao, item.subMesh.indexStart, item.subMesh.indexCount)
            }
        }

        // Restore state
        glCullFace(GL_BACK)
        glColorMask(true, true, true, true)
    }

    override fun destroy()
    {
        program.destroy()
        vbo.destroy()
        vao.destroy()
    }

    fun draw(drawList: DrawList)
    {
        writeDrawLists += drawList
        increaseBatchSize()
    }

    fun updateSunParameters(cameraPos: Vector3f, sunDirection: Float, sunHeight: Float) 
    {
        val yaw   = sunDirection.toRadians()
        val pitch = sunHeight.toRadians()
        val cp = cos(pitch)
        val x = sin(yaw) * cp
        val y = sin(pitch)
        val z = -cos(yaw) * cp
        lightDirection.set(x, y, z).negate().normalize() // Light direction (eye -> center)

        val up = if (abs(lightDirection.dot(WORLD_UP)) > 0.99f) Vector3f(0f, 0f, 1f) else WORLD_UP
        
        // Rotation-only light view (eye at origin)
        val lightViewRot = Matrix4f().lookAt(CENTER, lightDirection, up)

        // Center in rotated light space (NO translation component involved)
        val centerLightSpace = Vector3f(cameraPos)
        lightViewRot.transformPosition(centerLightSpace)

        // Snap X/Y in that rotated space
        val texelSize = shadowMapSizeMeters / shadowMapResolution.toFloat()
        centerLightSpace.x = floor(centerLightSpace.x / texelSize + 0.5f) * texelSize
        centerLightSpace.y = floor(centerLightSpace.y / texelSize + 0.5f) * texelSize

        // Back to world space
        val snappedCenterWorldSpace = Vector3f(centerLightSpace)
        lightViewRot.invert().transformPosition(snappedCenterWorldSpace)

        // Build light view around snapped center
        val shadowBackoffMeters = 90f
        val lightPosWS = Vector3f(snappedCenterWorldSpace).sub(Vector3f(lightDirection).mul(shadowBackoffMeters))
        val lightView  = Matrix4f().lookAt(lightPosWS, snappedCenterWorldSpace, up)
        
        // Projection
        val halfSize = shadowMapSizeMeters / 2f
        nearPlane = 0.1f
        farPlane  = shadowBackoffMeters + 200f
        writeViewProjectionMatrix
            .identity()
            .ortho(-halfSize, halfSize, -halfSize, halfSize, nearPlane, farPlane)
            .mul(lightView)
    }

    fun getViewProjectionMatrix(read: Boolean = true) = if (read) readViewProjectionMatrix else writeViewProjectionMatrix
    
    companion object
    {
        private val WORLD_UP = Vector3f(0f, 1f, 0f)
        private val CENTER = Vector3f(0f, 0f, 0f)
    }
}