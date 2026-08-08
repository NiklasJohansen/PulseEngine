package no.njoh.pulseengine.core.graphics.scene3d

import gnu.trove.list.array.TLongArrayList
import gnu.trove.map.hash.THashMap
import gnu.trove.set.hash.THashSet
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.*
import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderState
import no.njoh.pulseengine.core.graphics.gpu.buffer.BoneBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.CullingBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.LightBufferObject
import no.njoh.pulseengine.core.graphics.scene3d.lighting.ClusteredLightGrid
import no.njoh.pulseengine.core.graphics.scene3d.shadow.LocalShadowAtlas
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawCommandBuilder
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderItem
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderScene
import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderStateProvider
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderView
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewDeclarer
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewKey
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.LodCameraState
import no.njoh.pulseengine.core.graphics.util.LodUtils
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.shared.primitives.Mat4f
import no.njoh.pulseengine.core.shared.primitives.Mat4fProps
import no.njoh.pulseengine.core.shared.primitives.Mat4fArena
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachInstance
import org.joml.Matrix4f
import org.joml.Vector3f

class SceneRenderContextImpl : SceneRenderContextInternal()
{
    private val views                       = LinkedHashMap<RenderViewKey<*>, RenderView>()
    private val clusteredLightGrids         = THashMap<CameraRenderState, ClusteredLightGrid>()
    private val clusteredLightGridRequests  = THashSet<CameraRenderState>()
    
    private var nextFrameScene = RenderScene()
    private var thisFrameScene = RenderScene()

    private var nextLodCameraState = LodCameraState()
    private var thisLodCameraState = LodCameraState()

    private var mat4fArena     = Mat4fArena()
    private var mat4fArenaNext = Mat4fArena()
    
    private val instanceBuffer     = InstanceBufferObject()
    private val cullingBuffer      = CullingBufferObject()
    private val boneBuffer         = BoneBufferObject()
    private val lightBuffer        = LightBufferObject()
    private val drawCommandBuilder = DrawCommandBuilder(instanceBuffer, cullingBuffer)
    private val localShadowAtlas   = LocalShadowAtlas()
    private val objectIdOverrides  = TLongArrayList()

    private var frameNumber = 0
    private var initialized = false
    private var lastFrameHadAnyItems = false

    override fun initFrame()
    {
        objectIdOverrides.resetQuick()
        frameNumber++

        nextFrameScene = thisFrameScene.also { thisFrameScene = nextFrameScene }
        nextFrameScene.clear()

        nextLodCameraState = thisLodCameraState.also { thisLodCameraState = nextLodCameraState }
        LodUtils.pruneStaleLodStates(frameNumber)

        mat4fArena = mat4fArenaNext.also { mat4fArenaNext = mat4fArena }
        mat4fArena.reset()

        clusteredLightGridRequests.clear()

        views.forEach { it.value.beginFrame() }
    }

    override fun buildFrame(engine: PulseEngineInternal)
    {
        views.entries.removeIf { it.value.lastFrameRequested < frameNumber - 10 }

        if (!thisFrameScene.hasAnyItems() && !lastFrameHadAnyItems)
            return // This and last frame had no items, skip frame. If the last frame had items, do a pass to clear everything.

        if (!initialized)
        {
            instanceBuffer.init()
            cullingBuffer.init()
            boneBuffer.init()
            lightBuffer.init()
            drawCommandBuilder.init(engine)
            initialized = true
        }

        // Declare world views
        engine.gfx.getAllSurfaces().forEachFast { surface ->
            surface.getAllRenderers().forEachInstance<RenderViewDeclarer>()
            {
                it.declareRenderViews(engine, surface, this)
            }
        }

        captureLodCameraState()

        instanceBuffer.clear()
        cullingBuffer.clear()
        boneBuffer.clear()
        lightBuffer.clear()

        // Reserve buffers
        val itemCount = thisFrameScene.getItemCount()
        instanceBuffer.reserveItemCapacity(itemCount)
        cullingBuffer.reserveItemCapacity(itemCount)

        // Upload items
        thisFrameScene.opaqueItems.addToBuffers()
        thisFrameScene.maskedItems.addToBuffers()
        thisFrameScene.blendedItems.addToBuffers()

        // Prepare lighting
        localShadowAtlas.update(thisFrameScene, getCameraPosition())
        localShadowAtlas.getActiveShadowFaces().forEach { face -> lightBuffer.addShadowFace(face) }
        thisFrameScene.localLights.forEach { lightBuffer.addLight(it) }
        buildRequestedClusteredLightGrids()

        // Submit buffers to GPU
        measure("Submit context buffers")
        {
            instanceBuffer.submit()
            cullingBuffer.submit()
            boneBuffer.submit()
            lightBuffer.submit()
            clusteredLightGrids.forEach { it.value.submit() }
        }

        // Prepare views for rendering
        drawCommandBuilder.beginFrame()
        views.forEach { (_, view) -> if (view.wasRequested()) view.prepare(thisFrameScene, drawCommandBuilder) }
        drawCommandBuilder.finishFramePreparation()

        lastFrameHadAnyItems = thisFrameScene.hasAnyItems()
    }

    override fun endFrame()
    {
        if (initialized && thisFrameScene.hasAnyItems())
        {
            measure("Fence context buffers")
            {
                instanceBuffer.markSubmittedDataInUse()
                cullingBuffer.markSubmittedDataInUse()
                boneBuffer.markGpuDataInUse()
                lightBuffer.markSubmittedDataInUse()
                drawCommandBuilder.markSubmittedDataInUse()
                clusteredLightGrids.forEach { it.value.markSubmittedDataInUse() }
            }
        }
    }

    override fun destroy()
    {
        views.clear()
        clusteredLightGrids.forEach { it.value.destroy() }
        clusteredLightGrids.clear()

        if (!initialized) return

        instanceBuffer.destroy()
        cullingBuffer.destroy()
        boneBuffer.destroy()
        lightBuffer.destroy()
        drawCommandBuilder.destroy()
        initialized = false
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T: RenderView> requestView(key: RenderViewKey<T>): T
    {
        val existing = views[key]
        if (existing != null)
        {
            require(key.type.isAssignableFrom(existing.javaClass)) { "View $key is already used by ${existing.javaClass.simpleName}" }
            existing.lastFrameRequested = frameNumber
            return existing as T
        }

        val view = key.create()
        require(key.type.isAssignableFrom(view.javaClass)) { "View key $key created ${view.javaClass.simpleName}, expected ${key.type.simpleName}" }
        view.lastFrameRequested = frameNumber
        views[key] = view
        return view
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T: RenderView> getView(key: RenderViewKey<T>): T? = 
        views[key]?.takeIf { it.wasRequested() && key.type.isAssignableFrom(it.javaClass) } as T?

    override fun submitModel(
        engine: PulseEngine,
        model: Model,
        transform: Matrix4f,
        material: Material?,
        animationPose: AnimatedSkeletonPose?,
        renderPassMask: RenderPassMask,
        lodPixelHeightThresholds: IntArray?,
        lodHysteresis: Float,
        lodKey: Long,
        objectId: Long
    ) {
        val lodLevel = LodUtils.getLodLevel(model, transform, lodPixelHeightThresholds, lodHysteresis, lodKey, thisLodCameraState)
        val meshInstances = model.getMeshInstancesAtLevel(lodLevel)
        val transformProperties = Mat4fProps.from(transform)
        val hasBones = model.hasBones

        meshInstances.forEachFast()
        {
            val animatedMeshPose = if (hasBones) animationPose?.getAnimatedMeshPose(it.nodeName) else null
            val boneMatrices     = if (hasBones) animatedMeshPose?.boneMatrices ?: model.getBindPoseBoneMatrices(it.nodeName) else null
            val cullingBounds    = if (hasBones && animatedMeshPose != null) it.mesh.animatedBounds ?: it.cullingBounds else it.cullingBounds
            val material         = material ?: engine.asset.getOrNull(it.materialHandle)
            val meshTransform    = Mat4f(mat4fArena)

            if (it.transformProperties.isIdentity)
            {
                meshTransform.set(transform, transformProperties)
            }
            else
            {
                val properties = transformProperties.commonWith(it.transformProperties)
                meshTransform.setMul(transform, it.transform, properties)
            }

            nextFrameScene.addMesh(it.mesh, material, meshTransform, cullingBounds, boneMatrices, renderPassMask, resolveObjectId(objectId))
        }
    }

    override fun submitMesh(mesh: Mesh, material: Material?, transform: Matrix4f, cullingBounds: Aabb?, boneMatrices: Array<Matrix4f>?, renderPassMask: RenderPassMask, objectId: Long)
    {
        nextFrameScene.addMesh(mesh, material, Mat4f(mat4fArena).set(transform), cullingBounds, boneMatrices, renderPassMask, resolveObjectId(objectId))
    }

    override fun submitPointLight(position: Vector3f, radius: Float, color: Color, shadowEnabled: Boolean, shadowResolution: Int, shadowNearPlane: Float, shadowBias: Float, shadowImportance: Float, shadowId: Long)
    {
        nextFrameScene.addLight(position, null, radius, color, 180f, 180f, shadowEnabled, shadowResolution, shadowNearPlane, shadowBias, shadowImportance, shadowId)
    }

    override fun submitSpotLight(
        position: Vector3f,
        direction: Vector3f,
        radius: Float,
        color: Color,
        innerConeAngle: Float,
        outerConeAngle: Float,
        shadowEnabled: Boolean,
        shadowResolution: Int,
        shadowNearPlane: Float,
        shadowBias: Float,
        shadowImportance: Float,
        shadowId: Long
    ) {
        nextFrameScene.addLight(position, direction, radius, color, innerConeAngle, outerConeAngle, shadowEnabled, shadowResolution, shadowNearPlane, shadowBias, shadowImportance, shadowId)
    }

    override fun pushObjectIdOverride(objectId: Long)
    {
        objectIdOverrides.add(objectId)
    }

    override fun popObjectIdOverride()
    {
        objectIdOverrides.removeAt(objectIdOverrides.size() - 1)
    }

    override fun getSubmittedScene() = thisFrameScene
    
    override fun getLocalShadowAtlas() = localShadowAtlas

    override fun getLightBuffer() = lightBuffer

    override fun getInstanceBuffer() = instanceBuffer

    override fun getClusteredLightGrid(state: CameraRenderState) = clusteredLightGrids[state]
    
    override fun requestClusteredLightGrid(state: CameraRenderState)
    {
        clusteredLightGridRequests += state
    }

    private fun buildRequestedClusteredLightGrids()
    {
        if (!thisFrameScene.hasAnyItems())
        {
            clusteredLightGrids.forEach { it.value.destroy() }
            clusteredLightGrids.clear()
            return
        }

        for (state in clusteredLightGridRequests)
        {
            val grid = clusteredLightGrids.getOrPut(state) { ClusteredLightGrid() }
            grid.build(state, thisFrameScene)
        }

        clusteredLightGrids.retainEntries { state, grid -> 
            val retain = state in clusteredLightGridRequests
            if (!retain) grid.destroy()
            retain
        }
    }

    private fun DynamicList<RenderItem>.addToBuffers() =
        forEach { item ->
            val boneOffset = boneBuffer.addBoneMatricesAndGetOffset(item.boneMatrices)
            item.gpuInstanceIndex = instanceBuffer.addItem(item, boneOffset)
            cullingBuffer.addItem(item)
        }

    private fun getCameraPosition(): Vector3f? = 
        views.firstNotNullOfOrNull { (_, view) -> if (view.wasRequested() && view is CameraRenderStateProvider) view.shadowReferencePosition else null }

    private fun captureLodCameraState()
    {
        nextLodCameraState.invalidate()
        val cameraState = views
            .firstNotNullOfOrNull { (_, view) -> (view as? CameraRenderStateProvider)?.cameraStates?.firstOrNull()?.takeIf { view.wasRequested() } } 
            ?: return
        nextLodCameraState.set(cameraState, frameNumber)
    }

    private fun resolveObjectId(objectId: Long) = if (objectIdOverrides.isEmpty) objectId else objectIdOverrides[objectIdOverrides.size() - 1]

    private fun RenderView.wasRequested() = (lastFrameRequested == frameNumber)
}
