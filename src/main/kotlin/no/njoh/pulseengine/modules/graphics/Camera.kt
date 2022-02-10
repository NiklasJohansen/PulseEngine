package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.modules.graphics.DefaultCamera.*
import no.njoh.pulseengine.modules.graphics.DefaultCamera.ProjectionType.*
import no.njoh.pulseengine.modules.shared.utils.Extensions.interpolateFrom
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

abstract class Camera
{
    /** Camera matrices */
    open val viewMatrix: Matrix4f = Matrix4f()
    open val projectionMatrix: Matrix4f = Matrix4f()

    /** Position */
    var xPos: Float = 0f
    var yPos: Float = 0f
    var zPos: Float = 0f

    /** Rotation */
    var xRot: Float = 0f
    var yRot: Float = 0f
    var zRot: Float = 0f

    /** Scale */
    var xScale: Float = 1f
    var yScale: Float = 1f
    var zScale: Float = 1f

    /** Origin from center */
    var xOrigin: Float = 0f
    var yOrigin: Float = 0f
    var zOrigin: Float = 0f

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

    private var xLastPos: Float = 0f
    private var yLastPos: Float = 0f
    private var zLastPos: Float = 0f
    private var xLastRot: Float = 0f
    private var yLastRot: Float = 0f
    private var zLastRot: Float = 0f
    private var xLastScale: Float = 1f
    private var yLastScale: Float = 1f
    private var zLastScale: Float = 1f

    private val invViewMatrix = Matrix4f()
    private val positionVector = Vector4f()
    private val worldPositionVector = Vector3f()
    private val screenPositionVector = Vector2f()

    override fun screenPosToWorldPos(x: Float, y: Float): Vector3f
    {
        val pos = positionVector.set(x, y, 0f, 1f).mul(invViewMatrix)
        return worldPositionVector.set(pos.x, pos.y, pos.z)
    }

    override fun worldPosToScreenPos(x: Float, y: Float, z: Float): Vector2f
    {
        val pos = positionVector.set(x, y, z, 1f).mul(viewMatrix)
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
        val xPos = xPos.interpolateFrom(xLastPos)
        val yPos = yPos.interpolateFrom(yLastPos)
        val zPos = zPos.interpolateFrom(zLastPos)
        val xRot = xRot.interpolateFrom(xLastRot)
        val yRot = yRot.interpolateFrom(yLastRot)
        val zRot = zRot.interpolateFrom(zLastRot)
        val xScale = xScale.interpolateFrom(xLastScale)
        val yScale = yScale.interpolateFrom(yLastScale)
        val zScale = zScale.interpolateFrom(zLastScale)

        viewMatrix
            .identity()
            .translate(xOrigin, yOrigin, zOrigin)
            .scale(xScale, yScale, zScale)
            .rotateXYZ(xRot, yRot, -zRot)
            .translate(xPos - xOrigin, yPos - yOrigin, zPos - zOrigin)

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
        xLastPos = xPos
        yLastPos = yPos
        zLastPos = zPos
        xLastRot = xRot
        yLastRot = yRot
        zLastRot = zRot
        xLastScale = xScale
        yLastScale = yScale
        zLastScale = zScale
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