package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.DrawList
import no.njoh.pulseengine.core.graphics.api.DrawList.RenderItem
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.ModelBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.StaticBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawGpuCulledModelBatches
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawModelBatches
import no.njoh.pulseengine.core.graphics.util.GpuModelCuller
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.ModelBatcher
import no.njoh.pulseengine.core.graphics.util.addVisibleItems
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import no.njoh.pulseengine.core.shared.utils.Extensions.addAllNoAlloc
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11.*
import kotlin.math.*

class CascadedShadowMapRenderer(
    override val order: Int   = 0, 
    var resolution: Int       = 4096,
    var splitLambda: Float    = 0.5f,
    var shadowDistance: Float = 0f
) : Renderer() {

    private lateinit var staticProgram: ShaderProgram
    private lateinit var skinnedProgram: ShaderProgram
    private lateinit var modelBatcher: ModelBatcher
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: StaticBufferObject

    private var gpuCuller: GpuModelCuller? = null

    private val modelBuffer    = ModelBufferObject()
    private val renderItems    = ArrayList<RenderItem>(1024)
    private val shadowFrustum  = Frustum()
    private val lightDirection = Vector3f()
    private var readDrawLists  = ArrayList<DrawList>()
    private var writeDrawLists = ArrayList<DrawList>()
    private var readViewProjectionMatrices  = Array(CASCADE_COUNT) { Matrix4f() }
    private var writeViewProjectionMatrices = Array(CASCADE_COUNT) { Matrix4f() }
    private var readCascadeSplits  = FloatArray(CASCADE_COUNT)
    private var writeCascadeSplits = FloatArray(CASCADE_COUNT)
    private var readCascadeSizeMeters  = FloatArray(CASCADE_COUNT)
    private var writeCascadeSizeMeters = FloatArray(CASCADE_COUNT)
    private var readShadowCullingMatrix = Matrix4f()
    private var writeShadowCullingMatrix = Matrix4f()

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::staticProgram.isInitialized)
        {
            staticProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/shadow.vert", ::transformModelVertexShader)),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/shadow.frag"))
            )
            skinnedProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/shadow_skinned.vert", ::transformModelVertexShader)),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/shadow.frag"))
            )
            vbo = StaticBufferObject.createFullscreenUvTriangleArrayBuffer()
            modelBatcher = ModelBatcher(staticProgram, skinnedProgram)
            gpuCuller = GpuModelCuller.createIfSupported()?.apply { init(engine) }
            modelBuffer.init()
        }

        vao = VertexArrayObject.createAndBind()
        vbo.bind()
        staticProgram.bind()
        VertexAttributeLayout().withAttribute("position", 2, GL_FLOAT).bind(staticProgram)
        vao.release()
    }

    override fun onInitFrame()
    {
        readViewProjectionMatrices = writeViewProjectionMatrices.also { writeViewProjectionMatrices = readViewProjectionMatrices }
        readCascadeSplits = writeCascadeSplits.also { writeCascadeSplits = readCascadeSplits }
        readCascadeSizeMeters = writeCascadeSizeMeters.also { writeCascadeSizeMeters = readCascadeSizeMeters }
        readShadowCullingMatrix = writeShadowCullingMatrix.also { writeShadowCullingMatrix = readShadowCullingMatrix }
        readDrawLists = writeDrawLists.also { writeDrawLists = readDrawLists }
        writeDrawLists.clear()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glColorMask(false, false, false, false)
        glEnable(GL_POLYGON_OFFSET_FILL)
        glPolygonOffset(SHADOW_SLOPE_BIAS, SHADOW_CONST_BIAS)

        staticProgram.bind()
        staticProgram.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())

        skinnedProgram.bind()
        skinnedProgram.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        
        renderItems.clear()
        shadowFrustum.setForViewProjection(readShadowCullingMatrix)

        readDrawLists.forEachFast()
        {
            if (gpuCuller != null)
            {
                renderItems.addAllNoAlloc(it.opaqueItems)
                renderItems.addAllNoAlloc(it.maskedItems)
            }
            else
            {
                renderItems.addVisibleItems(it.opaqueItems, shadowFrustum)
                renderItems.addVisibleItems(it.maskedItems, shadowFrustum)
            }
        }

        gpuCuller?.clear()
        modelBuffer.clear()
        val modelBatches = modelBatcher.createBatchesAndFillBuffer(renderItems, modelBuffer, gpuCuller)
        modelBuffer.submit()

        gpuCuller?.submitAndCull(modelBatches, shadowFrustum)

        val count = modelBatches.totalInstanceCount()
        val halfRes = resolution / 2

        for (cascade in 0 until CASCADE_COUNT)
        {
            measure({ "cascade #" plus cascade plus " (" plus count plus ")" })
            {
                val col = cascade % 2
                val row = cascade / 2
                glViewport(col * halfRes, row * halfRes, halfRes, halfRes)

                staticProgram.bind()
                staticProgram.setUniform("viewProjection", readViewProjectionMatrices[cascade])
                skinnedProgram.bind()
                skinnedProgram.setUniform("viewProjection", readViewProjectionMatrices[cascade])

                if (gpuCuller != null)
                    drawGpuCulledModelBatches(modelBatches, gpuCuller!!)
                else
                    drawModelBatches(modelBatches, modelBuffer.instanceIndexMode, modelBuffer.instanceIndexBuffer)
            }
        }

        gpuCuller?.markSubmittedDataInUse()
        modelBuffer.markSubmittedDataInUse()

        glViewport(0, 0, resolution, resolution)
        glDisable(GL_POLYGON_OFFSET_FILL)
        glColorMask(true, true, true, true)
    }

    override fun destroy()
    {
        staticProgram.destroy()
        skinnedProgram.destroy()
        vbo.destroy()
        vao.destroy()
        modelBuffer.destroy()
        gpuCuller?.destroy()
    }

    fun draw(drawList: DrawList)
    {
        writeDrawLists += drawList
        increaseBatchSize()
    }

    fun setFor(camera: Camera, direction: Float, height: Float)
    {
        // Compute light direction
        val yaw = direction.toRadians()
        val pitch = height.toRadians()
        val cp = cos(pitch)
        val x = sin(yaw) * cp
        val y = sin(pitch)
        val z = -cos(yaw) * cp
        lightDirection.set(x, y, z).negate().normalize()

        // Determine up direction
        val up = if (abs(lightDirection.dot(WORLD_UP)) > 0.99f) Vector3f(0f, 0f, 1f) else WORLD_UP

        // Compute cascade split distances
        val camNear = camera.nearPlane
        val camFar  = if (shadowDistance > 0f) min(shadowDistance, camera.farPlane) else camera.farPlane
        val cascadeSplitDistances = getCascadeSplitDistances(camNear, camFar, splitLambda, writeCascadeSplits)

        // Derive aspect ratio from the camera's projection matrix
        val aspectRatio = camera.projectionMatrix.m11() / camera.projectionMatrix.m00()
        
        // Build a stable light-view matrix with only rotation (no translation).
        lightViewMatrix.identity().lookAt(
            -lightDirection.x, -lightDirection.y, -lightDirection.z, // Eye
            0f, 0f, 0f,                                              // Center = origin
            up.x, up.y, up.z                                         // Up direction
        )

        for (cascadeIdx in 0 until CASCADE_COUNT)
        {
            val splitNear = if (cascadeIdx == 0) camNear else cascadeSplitDistances[cascadeIdx - 1]
            val splitFar  = cascadeSplitDistances[cascadeIdx]

            // Build a sub-frustum projection for this cascade slice and get corners in world space
            val frustumSliceCorners = getFrustumSliceCorners(camera, aspectRatio, splitNear, splitFar)

            // Compute the frustum center from the corners
            frustumSliceCenter.set(0f)
            frustumSliceCorners.forEachFast { frustumSliceCenter.add(it) }
            frustumSliceCenter.div(8f)

            // Compute the radius of a bounding sphere that encompasses the frustum slice. 
            // This is used to create a tight orthographic projection for the cascade.
            val frustumBoundingSphereRadius = getFrustumBoundingSphereRadius(camera.fov.toRadians(), aspectRatio, splitNear, splitFar)
            val cascadeWorldSize = frustumBoundingSphereRadius * 2f
            writeCascadeSizeMeters[cascadeIdx] = cascadeWorldSize

            // Project the frustum center into light space for X/Y centering
            val center = tmpVec4.set(frustumSliceCenter.x, frustumSliceCenter.y, frustumSliceCenter.z, 1f).mul(lightViewMatrix)

            // Snap the center to the texel grid to prevent shadow swimming.
            val halfRes = resolution * 0.5f
            val texelSize = cascadeWorldSize / halfRes
            val xSnappedCenter = floor(center.x / texelSize) * texelSize
            val ySnappedCenter = floor(center.y / texelSize) * texelSize

            val xMin = xSnappedCenter - frustumBoundingSphereRadius
            val xMax = xSnappedCenter + frustumBoundingSphereRadius
            val yMin = ySnappedCenter - frustumBoundingSphereRadius
            val yMax = ySnappedCenter + frustumBoundingSphereRadius

            // Compute the minimum and maximum Z values of the frustum corners in light space to determine near/far planes.
            var zMin =  Float.MAX_VALUE
            var zMax = -Float.MAX_VALUE
            for (corner in frustumSliceCorners)
            {
                val depth = tmpVec4.set(corner.x, corner.y, corner.z, 1f).mul(lightViewMatrix).z
                zMin = min(zMin, depth)
                zMax = max(zMax, depth)
            }

            // Extend the depth range in both directions to catch shadow casters that lie outside
            // the tight camera frustum slice but still cast shadows into the visible area.
            // zMax is extended toward the light (catches objects above/behind the frustum),
            // zMin is extended away from the light (catches objects on the far side of the frustum).
            zMax += SHADOW_BACKOFF_METERS
            zMin -= SHADOW_BACKOFF_METERS

            // Build the orthographic projection matrix for this cascade slice
            writeViewProjectionMatrices[cascadeIdx]
                .identity()
                .ortho(xMin, xMax, yMin, yMax, -zMax, -zMin)
                .mul(lightViewMatrix)

            // Build an expanded culling matrix from the outermost cascade to catch
            // shadow casters that are outside the tight cascade frustum but still
            // cast shadows into the visible area (e.g. roofs, overhangs).
            if (cascadeIdx == CASCADE_COUNT - 1)
            {
                val expand = SHADOW_BACKOFF_METERS
                writeShadowCullingMatrix
                    .identity()
                    .ortho(xMin - expand, xMax + expand, yMin - expand, yMax + expand, -zMax, -zMin)
                    .mul(lightViewMatrix)
            }
        }
    }

    /**
     * Computes cascade split distances using a split scheme that blends between logarithmic and uniform splits.
     */
    private fun getCascadeSplitDistances(near: Float, far: Float, splitLambda: Float, cascadeSplitDistances: FloatArray): FloatArray 
    {
        for (i in 0 until cascadeSplitDistances.size)
        {
            val p = (i + 1f) / cascadeSplitDistances.size.toFloat()
            val log = near * (far / near).pow(p)
            val uniform = near + (far - near) * p
            cascadeSplitDistances[i] = splitLambda * log + (1f - splitLambda) * uniform
        }
        return cascadeSplitDistances
    }

    /**
     * Computes the minimum bounding sphere radius of a perspective frustum slice.
     * The optimal sphere center along the view axis is computed to minimize the radius,
     * rather than using the naive midpoint which overestimates significantly for wide FOVs.
     */
    private fun getFrustumBoundingSphereRadius(fovRadians: Float, aspect: Float, splitNear: Float, splitFar: Float): Float
    {
        // Measures how fast the frustum expands
        val tanHalfFov = tan(fovRadians * 0.5f)
        val frustumSpreadSquared = tanHalfFov * tanHalfFov * (1f + aspect * aspect)

        // Optimal sphere center along the view axis that minimizes the bounding radius.
        // If the optimal center falls behind the far plane, clamp to the midpoint.
        val zOptimal = 0.5f * (splitFar + splitNear) * (1f + frustumSpreadSquared)
        val zCenter  = min(zOptimal, (splitNear + splitFar) * 0.5f + (splitFar - splitNear) * 0.5f)

        // Radius = distance from zCenter to the farthest corner
        val farHalfHeight = tanHalfFov * splitFar
        val farHalfWidth  = farHalfHeight * aspect
        val zDelta = splitFar - zCenter

        return sqrt(farHalfWidth * farHalfWidth + farHalfHeight * farHalfHeight + zDelta * zDelta)
    }

    /**
     * Returns the 8 corners of the camera frustum slice between splitNear and splitFar, transformed to world space.
     */
    private fun getFrustumSliceCorners(camera: Camera, aspect: Float, splitNear: Float, splitFar: Float): Array<Vector3f>
    {
        // Builds an inverse view projection matrix for the frustum slice between splitNear and splitFar
        frustumSliceInvViewProjection
            .identity()
            .perspective(camera.fov.toRadians(), aspect, splitNear, splitFar)
            .mul(camera.viewMatrix)
            .invert()

        // 8 NDC corners of the unit cube [-1,1]^3   
        ndcCorners[0].set(-1f, -1f, -1f, 1f)
        ndcCorners[1].set( 1f, -1f, -1f, 1f)
        ndcCorners[2].set(-1f,  1f, -1f, 1f)
        ndcCorners[3].set( 1f,  1f, -1f, 1f)
        ndcCorners[4].set(-1f, -1f,  1f, 1f)
        ndcCorners[5].set( 1f, -1f,  1f, 1f)
        ndcCorners[6].set(-1f,  1f,  1f, 1f)
        ndcCorners[7].set( 1f,  1f,  1f, 1f)

        // Transform the 8 corners into world space
        for (i in 0 until 8)
        {
            val c = ndcCorners[i].mul(frustumSliceInvViewProjection)
            worldFrustumCorners[i].set(c.x / c.w, c.y / c.w, c.z / c.w)
        }

        return worldFrustumCorners
    }

    fun getDirection() = lightDirection
    
    /**
     * Returns the cascade view-projection matrices.
     */
    fun getViewProjectionMatrices() = readViewProjectionMatrices

    /**
     * Returns the far cascade split distances in view space depth.
     */
    fun getCascadeSplitDistances() = readCascadeSplits

    /**
     * Returns the world-space size of each cascade in meters.
     */
    fun getCascadeSizeMeters() = readCascadeSizeMeters

    companion object
    {
        private val WORLD_UP = Vector3f(0f, 1f, 0f)

        const val CASCADE_COUNT                 = 4
        private const val SHADOW_BACKOFF_METERS = 150f
        private const val SHADOW_SLOPE_BIAS     = 3.0f
        private const val SHADOW_CONST_BIAS     = 1.0f

        // Temp variables to avoid allocations every frame
        private val lightViewMatrix               = Matrix4f()
        private val frustumSliceInvViewProjection = Matrix4f()
        private val frustumSliceCenter            = Vector3f()
        private val tmpVec4                       = Vector4f()
        private val worldFrustumCorners           = Array(8) { Vector3f() }
        private val ndcCorners                    = Array(8) { Vector4f() }
    }
}
