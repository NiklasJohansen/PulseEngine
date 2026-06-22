package no.njoh.pulseengine.core.graphics.scene3d.view

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawCommandBuilder
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContextInternal
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderScene
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.shared.primitives.StaticList
import org.joml.Vector3f

abstract class RenderView(
    val viewId: Int,
    val visibilityMask: Int
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

class RenderViewKey<T : RenderView>(
    val viewId: Int,
    val type: Class<T>,
    val create: () -> T
) {
    companion object
    {
        inline operator fun <reified T : RenderView> invoke(viewId: Int, noinline create: () -> T) = 
            RenderViewKey(viewId, T::class.java, create)
    }
}

interface CameraRenderStateProvider
{
    val cameraStates: StaticList<CameraRenderState>
    val shadowReferencePosition: Vector3f?
}

object RenderViewIds
{
    const val MAIN_CAMERA   = 1
    const val GLOBAL_SHADOW = 2
    const val LOCAL_SHADOW  = 3
}

object RenderVisibility
{
    const val CAMERA        = 1 shl 0
    const val GLOBAL_SHADOW = 1 shl 1
    const val LOCAL_SHADOW  = 1 shl 2
}