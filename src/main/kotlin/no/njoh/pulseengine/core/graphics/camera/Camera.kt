package no.njoh.pulseengine.core.graphics.camera

import no.njoh.pulseengine.core.PulseEngineInternal
import org.joml.*

abstract class Camera
{
    /** The projection type currently used by this camera. */
    abstract val projectionType: CameraProjectionType

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

    /** Vertical field of view in degrees */
    var fov = 90f

    /** Vertical world-space height used by the orthographic 3D projection. */
    var orthographicHeight = 10f

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

    /** Called each fixed update step */
    abstract fun updateLastState()

    /** Called from the engine thread at the beginning of each frame */
    open fun onFrameStart(engine: PulseEngineInternal) { }

    /** Called from the engine thread right before the submitted date is drawn */
    open fun onFrameDraw(engine: PulseEngineInternal) { }
}