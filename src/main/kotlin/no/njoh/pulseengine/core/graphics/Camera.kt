package no.njoh.pulseengine.core.graphics

import no.njoh.pulseengine.core.graphics.DefaultCamera.*
import no.njoh.pulseengine.core.graphics.DefaultCamera.ProjectionType.*
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateFrom
import org.joml.*

abstract class Camera
{
    /** Camera matrices */
    open val viewMatrix: Matrix4f = Matrix4f()
    open val projectionMatrix: Matrix4f = Matrix4f()

    /** World position */
    var position = Vector3f()

    /** Rotation in radians */
    var rotation = Vector3f()

    /** Scale - default value 1.0f */
    var scale = Vector3f(1.0f)

    /** Origin in screen space coordinates. Determines the point of rotation. */
    var origin = Vector3f()

    /** Depth range */
    var farPlane = 5f
    var nearPlane = -1f

    /** Screen positions in world space */
    val topLeftWorldPosition = Vector2f()
    val bottomRightWorldPosition = Vector2f()

    /** Transforms a coordinate from screen space to world space */
    abstract fun screenPosToWorldPos(x: Float, y: Float): Vector3f

    /** Transforms a coordinate from world space to screen space */
    abstract fun worldPosToScreenPos(x: Float, y: Float, z: Float = 0f): Vector2f

    /** Returns true if a rectangle (in world space coordinates) intersects intersects the camera view rectangle */
    abstract fun isInView(x: Float, y: Float, width: Float, height: Float, padding: Float = 0f): Boolean

    /** Updates the projection matrix */
    abstract fun updateProjection(width: Int, height: Int, type: ProjectionType? = null)
}

abstract class CameraInternal : Camera()
{
    /** Used to make sure a single Camera instance is not updated multiple times per frame */
    var updateNumber: Int = 0

    /** Last world position */
    val positionLast = Vector3f()

    /** Last rotation in radians */
    val rotationLast = Vector3f()

    /** Last scale - default value 1.0f */
    val scaleLast = Vector3f(1.0f)

    /** Called each physics step */
    abstract fun updateLastState()

    /** Called each frame */
    abstract fun updateViewMatrix()

    /** Called each frame */
    abstract fun updateWorldPositions(screenWidth: Int, screenHeight: Int)
}

class DefaultCamera(
    private var projectionType: ProjectionType
) : CameraInternal() {

    override var projectionMatrix = Matrix4f()

    private val invViewMatrix = Matrix4f()
    private val returnVector = Vector4f()
    private val worldPositionVector = Vector3f()
    private val screenPositionVector = Vector2f()
    private val iPos = Vector3f()
    private val iRot = Vector3f()
    private val iScale = Vector3f()

    override fun screenPosToWorldPos(x: Float, y: Float): Vector3f
    {
        val pos = returnVector.set(x, y, 0f, 1f).mul(invViewMatrix)
        return worldPositionVector.set(pos.x, pos.y, pos.z)
    }

    override fun worldPosToScreenPos(x: Float, y: Float, z: Float): Vector2f
    {
        val pos = returnVector.set(x, y, z, 1f).mul(viewMatrix)
        return screenPositionVector.set(pos.x, pos.y)
    }

    override fun updateProjection(width: Int, height: Int, type: ProjectionType?)
    {
        projectionType = type ?: projectionType
        projectionMatrix = when (projectionType)
        {
            ORTHOGRAPHIC -> Matrix4f().ortho(0.0f, width.toFloat(), height.toFloat(), 0.0f, nearPlane, farPlane)
        }
    }

    override fun isInView(x: Float, y: Float, width: Float, height: Float, padding: Float) =
        x + width >= topLeftWorldPosition.x - padding &&
        x <= bottomRightWorldPosition.x + padding &&
        y + height >= topLeftWorldPosition.y - padding &&
        y <= bottomRightWorldPosition.y + padding

    override fun updateViewMatrix()
    {
        position.interpolateFrom(positionLast, destination = iPos)
        rotation.interpolateFrom(rotationLast, destination = iRot)
        scale.interpolateFrom(scaleLast, destination = iScale)

        viewMatrix
            .identity()
            .translate(origin)
            .scale(iScale)
            .rotateXYZ(iRot.x, iRot.y, -iRot.z)
            .translate(iPos.x - origin.x, iPos.y - origin.y, iPos.z - origin.z)

        viewMatrix.invert(invViewMatrix)
    }

    override fun updateWorldPositions(screenWidth: Int, screenHeight: Int)
    {
        val topLeft = screenPosToWorldPos(0f, 0f)
        topLeftWorldPosition.set(topLeft.x, topLeft.y)
        val bottomRight = screenPosToWorldPos(screenWidth.toFloat(), screenHeight.toFloat())
        bottomRightWorldPosition.set(bottomRight.x, bottomRight.y)
    }

    override fun updateLastState()
    {
        positionLast.set(position)
        rotationLast.set(rotation)
        scaleLast.set(scale)
    }

    companion object
    {
        fun createOrthographic(width: Int, height: Int): DefaultCamera =
            DefaultCamera(ORTHOGRAPHIC)
                .also { it.updateProjection(width, height) }
    }

    enum class ProjectionType
    {
        ORTHOGRAPHIC
    }
}