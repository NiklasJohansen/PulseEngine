package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.api.ModelProgramSet
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.WorldRenderFrame
import no.njoh.pulseengine.core.graphics.api.WorldRenderState
import no.njoh.pulseengine.core.graphics.api.WorldShadowRenderView
import no.njoh.pulseengine.core.graphics.api.objects.StaticBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawGpuCulledModelBatches
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawModelBatches
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11.*
import kotlin.math.*

class CascadedShadowMapRenderer(
    var worldRenderState: WorldRenderState,
    var resolution: Int       = 4096,
    var splitLambda: Float    = 0.5f,
    var shadowDistance: Float = 0f,
    override val order: Int   = 0,
) : Renderer() {

    private lateinit var staticProgram: ShaderProgram
    private lateinit var skinnedProgram: ShaderProgram
    private lateinit var programs: ModelProgramSet
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: StaticBufferObject

    private val cascadeFrustums = Array(CASCADE_COUNT) { Frustum() }
    private val lightDirection  = Vector3f()
    private var readFrames      = ArrayList<WorldRenderFrame>()
    private var writeFrames     = ArrayList<WorldRenderFrame>()
    private var readViewProjectionMatrices  = Array(CASCADE_COUNT) { Matrix4f() }
    private var writeViewProjectionMatrices = Array(CASCADE_COUNT) { Matrix4f() }
    private var readCascadeSplits  = FloatArray(CASCADE_COUNT)
    private var writeCascadeSplits = FloatArray(CASCADE_COUNT)
    private var readCascadeSizeMeters  = FloatArray(CASCADE_COUNT)
    private var writeCascadeSizeMeters = FloatArray(CASCADE_COUNT)
    private var readCascadeFrustumPlaneSets  = Array(CASCADE_COUNT) { FrustumPlaneSet(MAX_FRUSTUM_PLANES) }
    private var writeCascadeFrustumPlaneSets = Array(CASCADE_COUNT) { FrustumPlaneSet(MAX_FRUSTUM_PLANES) }
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
            programs = ModelProgramSet(staticProgram, skinnedProgram)
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
        readCascadeFrustumPlaneSets = writeCascadeFrustumPlaneSets.also { writeCascadeFrustumPlaneSets = readCascadeFrustumPlaneSets }
        readShadowCullingMatrix = writeShadowCullingMatrix.also { writeShadowCullingMatrix = readShadowCullingMatrix }
        readFrames = writeFrames.also { writeFrames = readFrames }
        writeFrames.clear()
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

        for (frame in readFrames)
        {
            worldRenderState.getShadowRenderView(engine, frame, readShadowCullingMatrix, readCascadeFrustumPlaneSets)
            {
                view -> renderShadowView(view)
            }
        }

        glViewport(0, 0, resolution, resolution)
        glDisable(GL_POLYGON_OFFSET_FILL)
        glColorMask(true, true, true, true)
    }

    private fun renderShadowView(view: WorldShadowRenderView)
    {
        val modelBatches = view.getBatches()
        val gpuCuller = view.getGpuCuller()
        val halfRes = resolution / 2

        for (cascade in 0 until CASCADE_COUNT)
        {
            measure({ "cascade #" plus cascade })
            {
                val col = cascade % 2
                val row = cascade / 2
                glViewport(col * halfRes, row * halfRes, halfRes, halfRes)

                staticProgram.bind()
                staticProgram.setUniform("viewProjection", readViewProjectionMatrices[cascade])
                skinnedProgram.bind()
                skinnedProgram.setUniform("viewProjection", readViewProjectionMatrices[cascade])

                if (gpuCuller != null)
                    drawGpuCulledModelBatches(modelBatches, programs, gpuCuller, commandSetIndex = cascade)
                else
                    drawModelBatches(modelBatches, programs, view.modelBuffer.instanceIndexMode, view.modelBuffer.instanceIndexBuffer)
            }
        }
    }

    override fun destroy()
    {
        staticProgram.destroy()
        skinnedProgram.destroy()
        vbo.destroy()
        vao.destroy()
    }

    fun draw(frame: WorldRenderFrame)
    {
        writeFrames += frame
        worldRenderState.queue(frame)
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
        
        // Build a stable light-view orientation from the light direction. The lookAt
        // translation is arbitrary here, each cascade is centered explicitly below.
        lightViewMatrix.identity().lookAt(
            -lightDirection.x, -lightDirection.y, -lightDirection.z, // Eye
            0f, 0f, 0f,                                              // Center = origin
            up.x, up.y, up.z                                         // Up direction
        )

        for (cascadeIdx in 0 until CASCADE_COUNT)
        {
            val splitFar = cascadeSplitDistances[cascadeIdx]
            val overlappedSplitNear = getOverlappedCascadeSplitNear(cascadeIdx, camNear, cascadeSplitDistances)

            // Build the receiver slice for this cascade and get its corners in world space.
            frustumSliceViewProjection
                .identity()
                .perspective(camera.fov.toRadians(), aspectRatio, overlappedSplitNear, splitFar)
                .mul(camera.viewMatrix)
            val frustumSliceCorners = getFrustumSliceCorners(frustumSliceViewProjection)

            // Compute the frustum center from the corners
            frustumSliceCenter.set(0f)
            frustumSliceCorners.forEachFast { frustumSliceCenter.add(it) }
            frustumSliceCenter.div(8f)

            // Compute the radius of a bounding sphere that encompasses the frustum slice. 
            // This is used to create a tight orthographic projection for the cascade.
            val frustumBoundingSphereRadius = getFrustumBoundingSphereRadius(camera.fov.toRadians(), aspectRatio, overlappedSplitNear, splitFar)
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

            // Extend the light-space depth range in both directions to keep casters outside
            // the receiver slice that can still project shadows into it.
            // zMax extends toward the light source, and zMin extends along the light direction.
            zMax += SHADOW_BACKOFF_METERS
            zMin -= SHADOW_BACKOFF_METERS

            // Build the orthographic projection matrix for this cascade slice
            writeViewProjectionMatrices[cascadeIdx]
                .identity()
                .ortho(xMin, xMax, yMin, yMax, -zMax, -zMin)
                .mul(lightViewMatrix)

            // Build the cascade and receiver frustum for this cascade slice
            cascadeFrustums[cascadeIdx].setForViewProjection(writeViewProjectionMatrices[cascadeIdx])
            receiverFrustum.setForViewProjection(frustumSliceViewProjection)

            // Build receiver-based caster culling planes for this cascade slice
            writeCascadeFrustumPlaneSets[cascadeIdx].buildCascadeCasterCullPlanes(
                shadowFrustum = cascadeFrustums[cascadeIdx],
                receiverFrustum = receiverFrustum,
                receiverCenter = frustumSliceCenter
            )

            // Build the broad CPU fallback culling matrix from the outermost cascade.
            // The GPU path uses the per-cascade receiver plane sets above.
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
     * Returns the near distance used to fit and cull a cascade.
     *
     * Cascade split distances are still the canonical boundaries used by the lighting shader
     * for selecting the primary cascade. However, the shader also samples the next cascade
     * through the last [SHADOW_CASCADE_BLEND_RATIO] of the current cascade to hide seams.
     * If cascade N+1 is fitted/cull-tested from its exact split near plane, it can omit
     * casters needed by those blended pixels and create a bright band at the split. Expanding
     * the next cascade backward by the previous cascade's blend width keeps both cascades
     * valid for every pixel that can sample them.
     */
    private fun getOverlappedCascadeSplitNear(cascadeIdx: Int, camNear: Float, cascadeSplitDistances: FloatArray): Float
    {
        if (cascadeIdx == 0) return camNear

        val previousSplitNear = if (cascadeIdx == 1) camNear else cascadeSplitDistances[cascadeIdx - 2]
        val previousSplitFar = cascadeSplitDistances[cascadeIdx - 1]
        val previousCascadeRange = previousSplitFar - previousSplitNear
        return max(camNear, previousSplitFar - previousCascadeRange * SHADOW_CASCADE_BLEND_RATIO)
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
     * Returns the 8 world-space corners of the frustum described by [frustumSliceViewProjection].
     */
    private fun getFrustumSliceCorners(frustumSliceViewProjection: Matrix4f): Array<Vector3f>
    {
        frustumSliceInvViewProjection.set(frustumSliceViewProjection).invert()

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

    /**
     * Builds a conservative shadow-caster culling volume for one cascade.
     *
     * The shadow-map orthographic box is kept as the outer bound. The receiver frustum adds
     * conservative planes that reject casters whose shadows cannot reach this cascade's visible
     * receiver slice.
     *
     * Receiver frustum planes point inward. When dot(plane.normal, lightDirection) is negative,
     * moving along the light direction exits the receiver through that plane. These are the
     * receiver "back planes", and they are safe caster-side culling planes.
     *
     * The remaining receiver planes are not added directly. Casters in front of the receiver
     * along the light direction can still cast into it, so front planes are only used to find
     * silhouette edges: every receiver-frustum edge shared by one back plane and one front plane
     * is extruded along [lightDirection]. The plane through that edge and the light direction
     * cuts away side regions where projected shadows pass beside the receiver.
     */
    private fun FrustumPlaneSet.buildCascadeCasterCullPlanes(shadowFrustum: Frustum, receiverFrustum: Frustum, receiverCenter: Vector3f)
    {
        clear()
        shadowFrustum.planes.forEachFast { add(it) }

        var planeIndex = 0
        var backPlaneCount = 0
        for (plane in receiverFrustum.planes)
        {
            val lightDot = plane.a * lightDirection.x + plane.b * lightDirection.y + plane.c * lightDirection.z
            val isBackPlane = lightDot < -0.0001f
            receiverPlaneIsBackFacing[planeIndex++] = isBackPlane

            if (isBackPlane)
            {
                addPlaneFacingPoint(plane, receiverCenter)
                backPlaneCount++
            }
        }

        if (backPlaneCount == 0)
            return // No receiver trimming can be derived - keep the shadow-map box only

        for (edgeIndex in 0 until RECEIVER_PLANE_EDGES.size / 2)
        {
            val p0 = RECEIVER_PLANE_EDGES[edgeIndex * 2]
            val p1 = RECEIVER_PLANE_EDGES[edgeIndex * 2 + 1]
            val p0IsBack = receiverPlaneIsBackFacing[p0]
            val p1IsBack = receiverPlaneIsBackFacing[p1]
            if (p0IsBack == p1IsBack)
                continue

            addLightExtrusionPlane(
                plane0 = receiverFrustum.planes[p0],
                plane1 = receiverFrustum.planes[p1],
                insidePoint = receiverCenter,
                lightDirection = lightDirection,
            )
        }
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

                const val CASCADE_COUNT              = 4
        private const val MAX_FRUSTUM_PLANES         = 24
        private const val SHADOW_CASCADE_BLEND_RATIO = 0.15f
        private const val SHADOW_BACKOFF_METERS      = 150f
        private const val SHADOW_SLOPE_BIAS          = 3.0f
        private const val SHADOW_CONST_BIAS          = 1.0f

        // Temp variables to avoid allocations every frame
        private val receiverFrustum               = Frustum()
        private val lightViewMatrix               = Matrix4f()
        private val frustumSliceViewProjection    = Matrix4f()
        private val frustumSliceInvViewProjection = Matrix4f()
        private val frustumSliceCenter            = Vector3f()
        private val tmpVec4                       = Vector4f()
        private val worldFrustumCorners           = Array(8) { Vector3f() }
        private val ndcCorners                    = Array(8) { Vector4f() }
        private val receiverPlaneIsBackFacing     = BooleanArray(6)

        private val RECEIVER_PLANE_EDGES = arrayOf(
            0, 2, // Left, bottom
            0, 3, // Left, top
            0, 4, // Left, near
            0, 5, // Left, far
            1, 2, // Right, bottom
            1, 3, // Right, top
            1, 4, // Right, near
            1, 5, // Right, far
            2, 4, // Bottom, near
            2, 5, // Bottom, far
            3, 4, // Top, near
            3, 5  // Top, far
        )
    }
}
