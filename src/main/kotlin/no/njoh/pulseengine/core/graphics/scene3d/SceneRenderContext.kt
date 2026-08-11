package no.njoh.pulseengine.core.graphics.scene3d

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.AnimatedSkeletonPose
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.graphics.gpu.buffer.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.LightBufferObject
import no.njoh.pulseengine.core.graphics.scene3d.lighting.ClusteredLightGrid
import no.njoh.pulseengine.core.graphics.scene3d.shadow.LocalShadowAtlas
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderScene
import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderState
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderView
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewKey
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.CAMERA
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.GLOBAL_SHADOW
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.LOCAL_SHADOW
import no.njoh.pulseengine.core.shared.primitives.Color
import org.joml.Matrix4f
import org.joml.Vector3f

abstract class SceneRenderContext()
{
    /**
     * Submits a [Model] to be rendered in the next frame.
     * If no material is provided, the model's default material will be used.
     */
    abstract fun submitModel(
        engine: PulseEngine,
        model: Model,
        transform: Matrix4f,
        material: Material? = null,
        animationPose: AnimatedSkeletonPose? = null,
        renderPassMask: RenderPassMask = CAMERA or GLOBAL_SHADOW or LOCAL_SHADOW,
        lodPixelHeightThresholds: IntArray? = null,
        lodHysteresis: Float = 0.15f,
        lodKey: Long = 0L,
        renderId: Long = -1
    )

    /**
     * Submits a specific [Mesh] to be rendered in the next frame.
     */
    abstract fun submitMesh(
        mesh: Mesh,
        material: Material?,
        transform: Matrix4f,
        cullingBounds: Model.Aabb? = mesh.localBounds,
        boneMatrices: Array<Matrix4f>? = null,
        renderPassMask: RenderPassMask = CAMERA or GLOBAL_SHADOW or LOCAL_SHADOW,
        renderId: Long = -1
    )

    /**
     * Submits a point light to be rendered in the next frame.
     */
    abstract fun submitPointLight(
        position: Vector3f,
        radius: Float,
        color: Color,
        shadowEnabled: Boolean = false,
        shadowResolution: Int = 512,
        shadowNearPlane: Float = 0.05f,
        shadowBias: Float = 0.005f,
        shadowImportance: Float = 1f,
        shadowId: Long = 0L
    )

    /**
     * Submits a spotlight to be rendered in the next frame.
     */
    abstract fun submitSpotLight(
        position: Vector3f,
        direction: Vector3f,
        radius: Float,
        color: Color,
        innerConeAngle: Float,
        outerConeAngle: Float,
        shadowEnabled: Boolean = false,
        shadowResolution: Int = 512,
        shadowNearPlane: Float = 0.05f,
        shadowBias: Float = 0.005f,
        shadowImportance: Float = 1f,
        shadowId: Long = 0L
    )

    /**
     * Returns the latest submitted [RenderScene].
     */
    abstract fun getSubmittedScene(): RenderScene
}

abstract class SceneRenderContextInternal : SceneRenderContext()
{
    abstract fun initFrame()
    abstract fun buildFrame(engine: PulseEngineInternal)
    abstract fun endFrame()
    abstract fun destroy()

    /**
     * Declares that a view is needed for the current frame, creating it if necessary.
     * Only requested views are prepared by [buildFrame].
     */
    abstract fun <T: RenderView> requestView(key: RenderViewKey<T>): T

    /**
     * Gets a view requested for the current frame after [buildFrame] has prepared it.
     */
    abstract fun <T: RenderView> getView(key: RenderViewKey<T>): T?

    /**
     * Overrides render IDs supplied by model and mesh submissions until [popRenderIdOverride] is called.
     * Overrides may be nested.
     */
    abstract fun pushRenderIdOverride(renderId: Long)

    /**
     * Ends the most recently pushed render ID override.
     */
    abstract fun popRenderIdOverride()
    
    abstract fun getLightBuffer(): LightBufferObject
    abstract fun getInstanceBuffer(): InstanceBufferObject
    abstract fun getLocalShadowAtlas(): LocalShadowAtlas
    abstract fun requestClusteredLightGrid(state: CameraRenderState)
    abstract fun getClusteredLightGrid(state: CameraRenderState): ClusteredLightGrid?
}
