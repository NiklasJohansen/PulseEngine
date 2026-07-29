package no.njoh.pulseengine.core.graphics.scene3d.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.camera.CameraProjectionType.ORTHOGRAPHIC_2D
import no.njoh.pulseengine.core.graphics.gpu.FullscreenPass
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.texture.BlendFunction
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.surface.renderers.Renderer
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL14.*

class GridRenderer(override val order: Int = 50) : Renderer() 
{
    private lateinit var program: ShaderProgram
    private lateinit var pass: FullscreenPass
    private val cameraPosition = Vector3f()

    override fun init(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        program = ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/surface.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/grid3d.frag"))
        )
        pass = FullscreenPass(program)
        pass.init()
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        increaseBatchSize() // To ensure the batch is rendered
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glDepthMask(false)
        glEnable(GL_BLEND)
        glBlendEquation(GL_FUNC_ADD)
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        glDisable(GL_CULL_FACE)
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL)

        val is2D = surface.camera.projectionType == ORTHOGRAPHIC_2D

        program.bind()
        program.setUniform("uInvViewProjection", surface.camera.invViewProjectionMatrix)
        program.setUniform("uViewProjection", surface.camera.viewProjectionMatrix)
        program.setUniform("uCameraPosition", surface.camera.invViewMatrix.getTranslation(cameraPosition))
        program.setUniform("uFadeDistance", surface.camera.farPlane.coerceIn(10f, 200f) * 0.75f)
        program.setUniform("uIs2D", is2D)

        if (is2D)
        {
            // The 2D projection operates in Y-up coordinates while the public 2D drawing API is
            // Y-down. Placing the origin at the bottom-left and flipping the V basis aligns the
            // generated grid with the coordinates used by 2D entities.
            program.setUniform("uPlaneOrigin", 0f, surface.config.height.toFloat(), 0f)
            program.setUniform("uPlaneNormal", 0f, 0f, 1f)
            program.setUniform("uPlaneU", 1f, 0f, 0f)
            program.setUniform("uPlaneV", 0f, -1f, 0f)
            program.setUniform("uMinorSpacing", 200f)
            program.setUniform("uMajorSpacing", 600f)
        }
        else
        {
            program.setUniform("uPlaneOrigin", 0f, 0f, 0f)
            program.setUniform("uPlaneNormal", 0f, 1f, 0f)
            program.setUniform("uPlaneU", 1f, 0f, 0f)
            program.setUniform("uPlaneV", 0f, 0f, 1f)
            program.setUniform("uMinorSpacing", 1f)
            program.setUniform("uMajorSpacing", 10f)
        }

        pass.draw()

        glEnable(GL_CULL_FACE)
        glCullFace(GL_BACK)
        glDepthMask(true)
        glDepthFunc(GL_LEQUAL)
        glPolygonMode(GL_FRONT_AND_BACK, if (surface.config.drawWireframe) GL_LINE else GL_FILL)

        if (surface.config.hasDepthAttachment) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)

        val blendFunc = surface.config.blendFunction
        if (blendFunc != BlendFunction.NONE)
        {
            glEnable(GL_BLEND)
            glBlendFuncSeparate(blendFunc.srcRgb, blendFunc.destRgb, blendFunc.srcAlpha, blendFunc.destAlpha)
        }
        else glDisable(GL_BLEND)
    }

    override fun destroy(engine: PulseEngineInternal)
    {
        pass.destroy()
        program.destroy()
    }
}