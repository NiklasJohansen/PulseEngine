package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.DrawList
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.StaticBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawTriangleIndices
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11.*
import kotlin.math.*

class CascadedShadowMapRenderer(
    var resolution: Int       = 4096, // Shadow map resolution in pixels
    var splitLambda: Float    = 0.5f, // 0 = uniform, 1 = logarithmic
    var shadowDistance: Float = 0f,   // Max shadow distance in meters. 0 = use camera far plane.
) : Renderer() {

    private lateinit var program: ShaderProgram
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: StaticBufferObject

    private val lightDirection = Vector3f()

    // Draw lists containing opaque and masked items to render into the shadow map
    private var readDrawLists  = ArrayList<DrawList>()
    private var writeDrawLists = ArrayList<DrawList>()

    // Per-cascade view-projection matrices
    private var readViewProjectionMatrices  = Array(CASCADE_COUNT) { Matrix4f() }
    private var writeViewProjectionMatrices = Array(CASCADE_COUNT) { Matrix4f() }

    // Per-cascade split distances in view space
    private var readCascadeSplits  = FloatArray(CASCADE_COUNT)
    private var writeCascadeSplits = FloatArray(CASCADE_COUNT)

    // Per-cascade world-space size in meters
    private var readCascadeSizeMeters  = FloatArray(CASCADE_COUNT)
    private var writeCascadeSizeMeters = FloatArray(CASCADE_COUNT)

    // Expanded culling VP matrix that covers all potential shadow casters
    private var shadowCullingMatrix = Matrix4f()

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::program.isInitialized)
        {
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/shadow.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/shadow.frag"))
            )
            vbo = StaticBufferObject.createFullscreenUvTriangleArrayBuffer()
        }

        vao = VertexArrayObject.createAndBind()
        vbo.bind()
        program.bind()
        VertexAttributeLayout().withAttribute("position", 2, GL_FLOAT).bind(program)
        vao.release()
    }

    override fun onInitFrame()
    {
        readViewProjectionMatrices = writeViewProjectionMatrices.also { writeViewProjectionMatrices = readViewProjectionMatrices }
        readCascadeSplits = writeCascadeSplits.also { writeCascadeSplits = readCascadeSplits }
        readCascadeSizeMeters = writeCascadeSizeMeters.also { writeCascadeSizeMeters = readCascadeSizeMeters }
        readDrawLists = writeDrawLists.also { writeDrawLists = readDrawLists }
        writeDrawLists.clear()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        val halfRes = resolution / 2

        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glColorMask(false, false, false, false)

        // Apply a slope-scaled depth bias to prevent shadow acne.
        // The slope factor biases proportionally to the surface slope relative to the light,
        // and the constant factor adds a minimum offset in depth buffer units.
        glEnable(GL_POLYGON_OFFSET_FILL)
        glPolygonOffset(SHADOW_SLOPE_BIAS, SHADOW_CONST_BIAS)

        program.bind()

        for (cascade in 0 until CASCADE_COUNT)
        {
            // Set viewport to the correct quadrant in the 2x2 atlas
            val col = cascade % 2
            val row = cascade / 2
            glViewport(col * halfRes, row * halfRes, halfRes, halfRes)

            program.setUniform("viewProjection", readViewProjectionMatrices[cascade])

            for (list in readDrawLists)
            {
                for (item in list.opaqueItems)
                {
                    val vao = item.model.vao ?: continue
                    program.setUniform("model", item.transform)
                    drawTriangleIndices(vao, item.subMesh.indexStart, item.subMesh.indexCount)
                }

                for (item in list.maskedItems)
                {
                    val vao = item.model.vao ?: continue
                    program.setUniform("model", item.transform)
                    drawTriangleIndices(vao, item.subMesh.indexStart, item.subMesh.indexCount)
                }
            }
        }

        // Restore viewport to full shadow map size and state
        glViewport(0, 0, resolution, resolution)
        glDisable(GL_POLYGON_OFFSET_FILL)
        glCullFace(GL_BACK)
        glColorMask(true, true, true, true)
    }

    override fun destroy()
    {
        program.destroy()
        vbo.destroy()
        vao.destroy()
    }

    fun draw(drawList: DrawList)
    {
        writeDrawLists += drawList
        increaseBatchSize()
    }

    fun setFor(camera: Camera, direction: Float, height: Float) // Camera?? Should we just take in position?
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
                shadowCullingMatrix
                    .identity()
                    .ortho(xMin - expand, xMax + expand, yMin - expand, yMax + expand, -zMax, -zMin)
                    .mul(lightViewMatrix)
            }
        }
    }

    /**
     * Computes cascade split distances using a split scheme that blends between logarithmic and uniform splits.
     */
    private fun getCascadeSplitDistances(near: Float, far: Float, splitLambda: Float, cascadeSplitDistances: FloatArray): FloatArray {
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

    /**
     * Returns the expanded culling matrix that covers all potential shadow casters.
     */
    fun getShadowCullingMatrix() = shadowCullingMatrix

    companion object
    {
        private val WORLD_UP = Vector3f(0f, 1f, 0f)

                const val CASCADE_COUNT         = 4
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