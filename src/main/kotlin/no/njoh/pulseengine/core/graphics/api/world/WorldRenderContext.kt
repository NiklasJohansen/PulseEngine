package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.AnimatedSkeletonPose
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.graphics.api.world.views.ViewIds.GLOBAL_SHADOW_VIEW
import no.njoh.pulseengine.core.graphics.api.world.views.ViewIds.LOCAL_SHADOW_VIEW
import no.njoh.pulseengine.core.graphics.api.world.views.ViewIds.MAIN_CAMERA_VIEW
import no.njoh.pulseengine.core.graphics.api.world.views.WorldRenderView
import no.njoh.pulseengine.core.shared.primitives.Color
import org.joml.Matrix4f
import org.joml.Vector3f

abstract class WorldRenderContext()
{
    abstract fun submitModel(
        engine: PulseEngine,
        model: Model,
        transform: Matrix4f,
        material: Material? = null,
        animationPose: AnimatedSkeletonPose? = null,
        viewIds: Int = MAIN_CAMERA_VIEW or GLOBAL_SHADOW_VIEW or LOCAL_SHADOW_VIEW
    )

    abstract fun submitMesh(
        mesh: Mesh,
        material: Material?,
        transform: Matrix4f,
        cullingBounds: Model.Aabb? = mesh.localBounds,
        boneMatrices: Array<Matrix4f>? = null,
        viewIds: Int = MAIN_CAMERA_VIEW or GLOBAL_SHADOW_VIEW or LOCAL_SHADOW_VIEW
    )

    abstract fun submitPointLight(
        position: Vector3f,
        radius: Float,
        color: Color,
        shadowEnabled: Boolean = false,
        shadowResolution: Int = 512,
        shadowBias: Float = 0.005f,
        shadowImportance: Float = 1f,
        shadowId: Long = 0L
    )

    abstract fun submitSpotLight(
        position: Vector3f,
        direction: Vector3f,
        radius: Float,
        color: Color,
        innerConeAngle: Float,
        outerConeAngle: Float,
        shadowEnabled: Boolean = false,
        shadowResolution: Int = 512,
        shadowBias: Float = 0.005f,
        shadowImportance: Float = 1f,
        shadowId: Long = 0L
    )

    abstract fun getLocalShadowAtlas(): LocalShadowAtlas
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