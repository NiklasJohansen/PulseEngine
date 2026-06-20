package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.AnimatedSkeletonPose
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.graphics.api.world.views.WorldVisibility.CAMERA
import no.njoh.pulseengine.core.graphics.api.world.views.WorldVisibility.GLOBAL_SHADOW
import no.njoh.pulseengine.core.graphics.api.world.views.WorldVisibility.LOCAL_SHADOW
import no.njoh.pulseengine.core.graphics.api.world.views.WorldRenderView
import no.njoh.pulseengine.core.graphics.api.world.views.WorldRenderViewKey
import no.njoh.pulseengine.core.graphics.api.objects.LightBufferObject
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
        visibilityMask: Int = CAMERA or GLOBAL_SHADOW or LOCAL_SHADOW
    )

    abstract fun submitMesh(
        mesh: Mesh,
        material: Material?,
        transform: Matrix4f,
        cullingBounds: Model.Aabb? = mesh.localBounds,
        boneMatrices: Array<Matrix4f>? = null,
        visibilityMask: Int = CAMERA or GLOBAL_SHADOW or LOCAL_SHADOW
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
}

abstract class WorldRenderContextInternal : WorldRenderContext()
{
    abstract fun initFrame()
    abstract fun buildFrame(engine: PulseEngineInternal)
    abstract fun endFrame()
    abstract fun destroy()

    /**
     * Declares that a view is needed for the current frame, creating it if necessary.
     * Only requested views are prepared by [buildFrame].
     */
    abstract fun <T: WorldRenderView> requestView(key: WorldRenderViewKey<T>): T

    /**
     * Gets a view requested for the current frame after [buildFrame] has prepared it.
     */
    abstract fun <T: WorldRenderView> getView(key: WorldRenderViewKey<T>): T?

    abstract fun getLightBuffer(): LightBufferObject
    abstract fun getLocalShadowAtlas(): LocalShadowAtlas
    abstract fun requestClusteredLightGrid(state: CameraRenderState)
    abstract fun getClusteredLightGrid(state: CameraRenderState): ClusteredLightGrid?
}