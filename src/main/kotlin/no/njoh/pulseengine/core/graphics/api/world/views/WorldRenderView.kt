package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderCommandBuilder
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderContextInternal
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.shared.primitives.StaticList
import org.joml.Vector3f

abstract class WorldRenderView(
    val viewId: Int,
    val visibilityMask: Int
) {
    var lastFrameRequested = -1

    abstract fun beginFrame()

    abstract fun prepare(scene: WorldRenderScene, builder: WorldRenderCommandBuilder)
}

/**
 * Interface for renderers that need to declare and use [WorldRenderView]s.
 */
interface WorldViewDeclarer
{
    /**
     * Called after late camera updates and before world render passes are prepared.
     */
    fun declareWorldViews(engine: PulseEngineInternal, surface: SurfaceInternal, context: WorldRenderContextInternal)
}

class WorldRenderViewKey<T : WorldRenderView>(
    val viewId: Int,
    val type: Class<T>,
    val create: () -> T
) {
    companion object
    {
        inline operator fun <reified T : WorldRenderView> invoke(viewId: Int, noinline create: () -> T) = 
            WorldRenderViewKey(viewId, T::class.java, create)
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

object WorldVisibility
{
    const val CAMERA        = 1 shl 0
    const val GLOBAL_SHADOW = 1 shl 1
    const val LOCAL_SHADOW  = 1 shl 2
}