package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.CameraProjectionType.*
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateFrom
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.*

abstract class Camera
{
    /** Camera matrices */
    open val viewMatrix = Matrix4f()
    open val projectionMatrix = Matrix4f()
    open val viewProjectionMatrix = Matrix4f()
    
    /** Inverse camera matrices */
    open val invViewMatrix = Matrix4f()
    open val invProjectionMatrix = Matrix4f()
    open val invViewProjectionMatrix = Matrix4f()

    /** World position */
    var position = Vector3f()

    /** Rotation in radians */
    var rotation = Vector3f()

    /** Origin in screen space coordinates. Determines the point of rotation. */
    var origin = Vector3f()

    /** Scale - default value 1.0f */
    var scale = Vector3f(1.0f)

    /** Depth range */
    var farPlane = 5f
    var nearPlane = -1f

    var fov = 90f

    /** Screen positions in world space */
    val topLeftWorldPosition = Vector2f()
    val bottomRightWorldPosition = Vector2f()

    /** Transforms a coordinate from screen space to world space */
    abstract fun screenPosToWorldPos(x: Float, y: Float, z: Float = 0f, screenWidth: Int, screenHeight: Int): Vector3f

    /** Transforms a coordinate from world space to screen space */
    abstract fun worldPosToScreenPos(x: Float, y: Float, z: Float, screenWidth: Int, screenHeight: Int): Vector2f

    /** Returns true if a rectangle (in world space coordinates) intersects the camera view rectangle */
    abstract fun isInView(x: Float, y: Float, width: Float, height: Float, padding: Float = 0f): Boolean

    /** Updates the projection matrix */
    abstract fun updateProjection(width: Int, height: Int, type: CameraProjectionType? = null)
}

abstract class CameraInternal : Camera()
{
    /** Used to make sure a single Camera instance is not updated multiple times per frame */
    var updateNumber: Int = 0

    /** Last world position */
    val positionLast = Vector3f()

    /** Last rotation in radians */
    val rotationLast = Vector3f()

    /** Last origin */
    val originLast = Vector3f()

    /** Last scale - default value 1.0f */
    val scaleLast = Vector3f(1.0f)

    /** Called each physics step */
    abstract fun updateLastState()

    /** Called from the engine thread at the beginning of each frame */
    open fun onFrameStart(engine: PulseEngineInternal) { }

    /** Called from the engine thread right before the submitted date is drawn */
    open fun onFrameDraw(engine: PulseEngineInternal) { }
}

class DefaultCamera(private var projectionType: CameraProjectionType) : CameraInternal() 
{
    override var projectionMatrix = Matrix4f()
    override var viewProjectionMatrix = Matrix4f()

    private val returnVector = Vector4f()
    private val worldPositionVector = Vector3f()
    private val screenPositionVector = Vector2f()
    private val iPos = Vector3f()
    private val iRot = Vector3f()
    private val iScale = Vector3f()
    private val iOrigin = Vector3f()

    override fun screenPosToWorldPos(x: Float, y: Float, z: Float, screenWidth: Int, screenHeight: Int): Vector3f
    {
        val xNdc = (2f * x) / screenWidth - 1f
        val yNdc = 1f - (2f * y) / screenHeight
        val zNdc = z * 2f - 1f
        val p = returnVector.set(xNdc, yNdc, zNdc, 1f).mul(invViewProjectionMatrix)
        val wInv = 1f / p.w
        val xUp = p.x * wInv
        val yUp = p.y * wInv
        val zUp = p.z * wInv
        val yDown = screenHeight - yUp
        return worldPositionVector.set(xUp, yDown, zUp)
    }

    override fun worldPosToScreenPos(x: Float, y: Float, z: Float, screenWidth: Int, screenHeight: Int): Vector2f
    {
        val yUp = screenHeight - y
        val clip = returnVector.set(x, yUp, z, 1f).mul(viewProjectionMatrix)
        val wInv = 1f / clip.w
        val xNdc = clip.x * wInv
        val yNdc = clip.y * wInv
        val sx = (xNdc * 0.5f + 0.5f) * screenWidth
        val sy = (1f - (yNdc * 0.5f + 0.5f)) * screenHeight // Screen Y-down
        return screenPositionVector.set(sx, sy)
    }

    override fun updateProjection(width: Int, height: Int, type: CameraProjectionType?)
    {
        projectionType = type ?: projectionType
        projectionMatrix = when (projectionType)
        {
            ORTHOGRAPHIC -> Matrix4f().ortho(0f, width.toFloat(), 0f, height.toFloat(), nearPlane, farPlane) // Y-up
            PERSPECTIVE -> Matrix4f().perspective(fov.toRadians(), width.toFloat() / height.toFloat(), nearPlane, farPlane)
        }
        invProjectionMatrix.set(projectionMatrix).invert()
    }

    override fun isInView(x: Float, y: Float, width: Float, height: Float, padding: Float) =
        x + width >= topLeftWorldPosition.x - padding &&
        x <= bottomRightWorldPosition.x + padding &&
        y + height >= topLeftWorldPosition.y - padding &&
        y <= bottomRightWorldPosition.y + padding

    override fun onFrameStart(engine: PulseEngineInternal)
    {
        // Updates the view matrix at the start of the frame.
        // Ensures that geometry submitted last frame is rendered with the camera state 
        // from the previous frame. This is the default behavior as the renderers are double-buffered.

        position.interpolateFrom(positionLast, destination = iPos)
        rotation.interpolateFrom(rotationLast, destination = iRot)
        origin.interpolateFrom(originLast, destination = iOrigin)
        scale.interpolateFrom(scaleLast, destination = iScale)

        when (projectionType)
        {
            ORTHOGRAPHIC ->
            {
                viewMatrix
                    .identity()
                    .translate(iOrigin)
                    .scale(iScale)
                    .rotateXYZ(iRot.x, iRot.y, iRot.z)
                    .translate(iPos.x - iOrigin.x, iPos.y - iOrigin.y, iPos.z - iOrigin.z)

                invViewMatrix.set(viewMatrix).invert()
            }
            PERSPECTIVE ->
            {
                invViewMatrix
                    .identity()
                    .translate(iPos)
                    .rotateXYZ(iRot.x, iRot.y, iRot.z)

                viewMatrix.set(invViewMatrix).invert()
            }
        }

        viewProjectionMatrix.set(projectionMatrix).mul(viewMatrix)
        invViewProjectionMatrix.set(viewProjectionMatrix).invert()

        // Update world positions of screen corners
        val screenWidth = engine.gfx.mainSurface.config.width
        val screenHeight = engine.gfx.mainSurface.config.height

        val topLeft = screenPosToWorldPos(0f, 0f, 0f, screenWidth, screenHeight)
        topLeftWorldPosition.set(topLeft.x, topLeft.y)

        val bottomRight = screenPosToWorldPos(screenWidth.toFloat(), screenHeight.toFloat(), 0f, screenWidth, screenHeight)
        bottomRightWorldPosition.set(bottomRight.x, bottomRight.y)
    }

    override fun updateLastState()
    {
        positionLast.set(position)
        rotationLast.set(rotation)
        originLast.set(origin)
        scaleLast.set(scale)
    }

    companion object
    {
        fun createOrthographic(width: Int, height: Int) =
            DefaultCamera(ORTHOGRAPHIC).also { it.updateProjection(width, height) }
    }
}

enum class CameraProjectionType
{
    ORTHOGRAPHIC,
    PERSPECTIVE
}