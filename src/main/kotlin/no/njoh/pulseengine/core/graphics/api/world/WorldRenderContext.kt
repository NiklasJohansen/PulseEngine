package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.AnimatedSkeletonPose
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.graphics.api.world.views.ViewIds
import no.njoh.pulseengine.core.graphics.api.world.views.WorldRenderView
import org.joml.Matrix4f

abstract class WorldRenderContext()
{
    abstract fun submit(
        engine: PulseEngine,
        model: Model,
        transform: Matrix4f,
        material: Material? = null,
        animationPose: AnimatedSkeletonPose? = null,
        viewIds: Int = ViewIds.MAIN_CAMERA_VIEW or ViewIds.SHADOW_VIEW
    )

    abstract fun submit(
        mesh: Mesh,
        material: Material?,
        transform: Matrix4f,
        cullingBounds: Model.Aabb? = mesh.localBounds,
        boneMatrices: Array<Matrix4f>? = null,
        viewIds: Int = ViewIds.MAIN_CAMERA_VIEW or ViewIds.SHADOW_VIEW
    )
}

abstract class WorldRenderContextInternal : WorldRenderContext() //, WorldRenderContextViewBuilder
{
    abstract fun initFrame()
    abstract fun buildFrame(engine: PulseEngineInternal)
    abstract fun endFrame()
    abstract fun destroy()

    abstract fun addView(view: WorldRenderView)
    abstract fun <T> getView(viewId: Int, type: Class<T>): T?
    inline fun <reified T> getView(viewId: Int) = getView(viewId, T::class.java)
}