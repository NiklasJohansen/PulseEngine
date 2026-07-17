package no.njoh.pulseengine.core.graphics.scene3d.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContextInternal
import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderState
import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderView
import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderViewGroup
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewDeclarer
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewKey
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.surface.renderers.Renderer
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawRenderBucket
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*

class ObjectIdRenderer(
    override val order: Int = 10,
    var enabled: Boolean = true,
    val viewGroup: CameraRenderViewGroup? = null
) : Renderer(), RenderViewDeclarer {

    private lateinit var opaqueStaticProgram: ShaderProgram
    private lateinit var opaqueSkinnedProgram: ShaderProgram
    private lateinit var alphaStaticProgram: ShaderProgram
    private lateinit var alphaSkinnedProgram: ShaderProgram
    private lateinit var opaquePrograms: ShaderProgramSet
    private lateinit var alphaPrograms: ShaderProgramSet
    private lateinit var viewKey: RenderViewKey<CameraRenderView>

    override fun init(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        val staticVertex   = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_object_id.vert", ::transformModelVertexShader))
        val skinnedVertex  = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_object_id_skinned.vert", ::transformModelVertexShader))
        val opaqueFragment = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_object_id.frag"))
        val alphaFragment  = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_object_id_alpha.frag"))

        opaqueStaticProgram  = ShaderProgram.create(staticVertex, opaqueFragment)
        opaqueSkinnedProgram = ShaderProgram.create(skinnedVertex, opaqueFragment)
        alphaStaticProgram   = ShaderProgram.create(staticVertex, alphaFragment)
        alphaSkinnedProgram  = ShaderProgram.create(skinnedVertex, alphaFragment)
        opaquePrograms       = ShaderProgramSet(opaqueStaticProgram, opaqueSkinnedProgram)
        alphaPrograms        = ShaderProgramSet(alphaStaticProgram, alphaSkinnedProgram)

        viewKey = viewGroup?.createViewKey() ?: RenderViewKey(surface.viewGroup) { CameraRenderView() }
    }

    override fun declareRenderViews(engine: PulseEngineInternal, surface: SurfaceInternal, context: SceneRenderContextInternal)
    {
        if (!enabled) return
        
        context.requestView(viewKey).addCameraStateFor(surface.camera, surface.config.width, surface.config.height)
        increaseBatchSize()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        glDisable(GL_BLEND)
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        glDepthFunc(GL_LESS)
        glViewport(0, 0, surface.config.width, surface.config.height)
        glClearBufferiv(GL_COLOR, 0, BACKGROUND_ID)
        glClearDepth(1.0)
        glClear(GL_DEPTH_BUFFER_BIT)

        val view = engine.gfx.sceneContext.getView(viewKey)
        val cameraState = view?.getCameraState(surface.camera)
        if (view != null && cameraState != null)
        {
            engine.gfx.sceneContext.getInstanceBuffer().bindObjectIds()

            measure("opaque", label = { "Draw opaque (" plus view.opaqueBucket.instanceCount plus "i, " plus view.opaqueBucket.size plus "b)" })
            {
                configureOpaque(opaqueStaticProgram, cameraState)
                configureOpaque(opaqueSkinnedProgram, cameraState)
                configureAlpha(alphaStaticProgram, engine, cameraState)
                configureAlpha(alphaSkinnedProgram, engine, cameraState)
                drawRenderBucket(view.opaqueBucket, view.drawPayload, opaquePrograms)
            }

            measure("masked", label = { "Draw masked (" plus view.maskedBucket.instanceCount plus "i, " plus view.maskedBucket.size plus "b)" })
            {
                alphaStaticProgram.bind()
                alphaStaticProgram.setUniform("uMinimumAlpha", 0f)
                alphaSkinnedProgram.bind()
                alphaSkinnedProgram.setUniform("uMinimumAlpha", 0f)
                drawRenderBucket(view.maskedBucket, view.drawPayload, alphaPrograms)
            }

            measure("blended", label = { "Draw blended (" plus  view.blendedBucket.instanceCount plus "i, " plus view.blendedBucket.size plus "b)" })
            {
                alphaStaticProgram.bind()
                alphaStaticProgram.setUniform("uMinimumAlpha", BLENDED_ALPHA_THRESHOLD)
                alphaSkinnedProgram.bind()
                alphaSkinnedProgram.setUniform("uMinimumAlpha", BLENDED_ALPHA_THRESHOLD)
                drawRenderBucket(view.blendedBucket, view.drawPayload, alphaPrograms)
            }
        }

    }

    override fun destroy(engine: PulseEngineInternal)
    {
        opaqueStaticProgram.destroy()
        opaqueSkinnedProgram.destroy()
        alphaStaticProgram.destroy()
        alphaSkinnedProgram.destroy()
    }

    private fun configureOpaque(program: ShaderProgram, state: CameraRenderState)
    {
        program.bind()
        program.setUniform("uViewProjection", state.viewProjectionMatrix)
    }

    private fun configureAlpha(program: ShaderProgram, engine: PulseEngineInternal, state: CameraRenderState)
    {
        configureOpaque(program, state)
        program.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
    }

    companion object
    {
        private const val BLENDED_ALPHA_THRESHOLD = 0.05f
        private val BACKGROUND_ID = intArrayOf(-1, -1, 0, 0)
    }
}