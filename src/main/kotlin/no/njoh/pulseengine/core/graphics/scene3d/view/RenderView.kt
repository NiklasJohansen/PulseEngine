package no.njoh.pulseengine.core.graphics.scene3d.view

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawCommandBuilder
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContextInternal
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderScene
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal

abstract class RenderView(
    val renderPassMask: RenderPassMask
) {
    var lastFrameRequested = -1

    abstract fun beginFrame()

    abstract fun prepare(scene: RenderScene, builder: DrawCommandBuilder)
}

/**
 * Interface for renderers that need to declare and use [RenderView]s.
 */
interface RenderViewDeclarer
{
    /**
     * Called after late camera updates and before render passes are prepared.
     */
    fun declareRenderViews(engine: PulseEngineInternal, surface: SurfaceInternal, context: SceneRenderContextInternal)
}