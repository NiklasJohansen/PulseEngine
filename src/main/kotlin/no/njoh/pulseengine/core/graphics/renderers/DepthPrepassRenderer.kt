package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Attachment.DEPTH_TEXTURE
import no.njoh.pulseengine.core.graphics.api.world.views.WorldCameraRenderView
import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawWorldRenderBucket
import no.njoh.pulseengine.core.graphics.api.world.views.ViewIds.MAIN_CAMERA_VIEW
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE
import org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_ONE

class DepthPrepassRenderer(
    override val order: Int = 20,
    val viewId: Int = MAIN_CAMERA_VIEW
) : Renderer() {

    private lateinit var opaqueStaticProgram: ShaderProgram
    private lateinit var opaqueSkinnedProgram: ShaderProgram
    private lateinit var maskedStaticProgram: ShaderProgram
    private lateinit var maskedSkinnedProgram: ShaderProgram
    private lateinit var opaquePrograms: ShaderProgramSet
    private lateinit var maskedPrograms: ShaderProgramSet

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::opaqueStaticProgram.isInitialized)
        {
            val staticVertex   = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_depth.vert", ::transformModelVertexShader))
            val skinnedVertex  = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_depth_skinned.vert", ::transformModelVertexShader))
            val opaqueFragment = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_depth_opaque.frag"))
            val maskedFragment = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_depth.frag"))

            opaqueStaticProgram = ShaderProgram.create(staticVertex, opaqueFragment)
            opaqueSkinnedProgram = ShaderProgram.create(skinnedVertex, opaqueFragment)
            maskedStaticProgram = ShaderProgram.create(staticVertex, maskedFragment)
            maskedSkinnedProgram = ShaderProgram.create(skinnedVertex, maskedFragment)

            opaquePrograms = ShaderProgramSet(opaqueStaticProgram, opaqueSkinnedProgram)
            maskedPrograms = ShaderProgramSet(maskedStaticProgram, maskedSkinnedProgram)
        }

        if (engine.gfx.worldContext.getView<WorldCameraRenderView>(viewId) == null)
            engine.gfx.worldContext.addView(WorldCameraRenderView(viewId))
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        increaseBatchSize() // Ensure that the batch size is at least 1

        engine.gfx.worldContext.getView<WorldCameraRenderView>(viewId)
            ?.setForCamera(surface.camera, surface.config.width, surface.config.height)
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        surface.config.hasDepthPrepass = true

        glColorMask(false, false, false, false)
        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LESS)
        glViewport(0, 0, surface.config.width, surface.config.height)

        configureOpaqueProgram(opaqueStaticProgram, surface)
        configureOpaqueProgram(opaqueSkinnedProgram, surface)
        configureMaskedProgram(maskedStaticProgram, engine, surface)
        configureMaskedProgram(maskedSkinnedProgram, engine, surface)

        engine.gfx.worldContext.getView<WorldCameraRenderView>(viewId)?.let { view -> render(view) }

        glColorMask(true, true, true, true)
        glDisable(GL_CULL_FACE)

        surface.renderTarget.resolveDepth(engine)
        surface.getTextures().firstOrNullFast { it.attachment == DEPTH_TEXTURE }?.generateMips(engine)
        surface.renderTarget.begin()
    }

    private fun configureOpaqueProgram(program: ShaderProgram, surface: SurfaceInternal)
    {
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
    }

    private fun configureMaskedProgram(program: ShaderProgram, engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        program.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
    }

    private fun render(view: WorldCameraRenderView)
    {
        val opaqueCount = view.opaqueBucket.totalInstanceCount()
        if (opaqueCount > 0)
        {
            measure({"opaque depth (" plus opaqueCount plus "i, " plus view.opaqueBucket.size plus "b)"})
            {
                drawWorldRenderBucket(view.opaqueBucket, view.preparedPass, opaquePrograms)
            }
        }

        val maskedCount = view.maskedBucket.totalInstanceCount()
        if (maskedCount > 0)
        {
            measure({"masked depth (" plus maskedCount plus "i, " plus view.maskedBucket.size plus "b)"})
            {
                glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE)
                glEnable(GL_SAMPLE_ALPHA_TO_ONE)

                drawWorldRenderBucket(view.maskedBucket, view.preparedPass, maskedPrograms)

                glDisable(GL_SAMPLE_ALPHA_TO_ONE)
                glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE)
            }
        }
    }

    override fun destroy()
    {
        opaqueStaticProgram.destroy()
        opaqueSkinnedProgram.destroy()
        maskedStaticProgram.destroy()
        maskedSkinnedProgram.destroy()
    }
}