package engine.modules.graphics

import engine.modules.console.ConsoleTarget
import engine.modules.entity.Transform2D
import engine.util.interpolateFrom
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

abstract class CameraInterface
{
    open val viewMatrix: Matrix4f = Matrix4f()

    // Position
    var xPos: Float = 0f
    var yPos: Float = 0f
    var zPos: Float = 0f

    // Rotation
    var xRot: Float = 0f
    var yRot: Float = 0f
    var zRot: Float = 0f

    // Scale
    var xScale: Float = 1f
    var yScale: Float = 1f
    var zScale: Float = 1f

    // Origin
    var xOrigin: Float = 0f
    var yOrigin: Float = 0f
    var zOrigin: Float = 0f

    // Smoothing
    var targetTrackingSmoothing = 1f

    abstract fun enable()
    abstract fun disable()
    abstract fun setTarget(target: Transform2D?)
    abstract fun screenPosToWorldPos(x: Float, y: Float): Vector3f
    abstract fun worldPosToScreenPos(x: Float, y: Float, z: Float = 0f): Vector2f
}

abstract class CameraEngineInterface : CameraInterface()
{
    abstract fun updateViewMatrix()
    abstract fun updateTransform(deltaTime: Float)
    abstract fun viewMatrixAsArray(): FloatArray
    abstract fun setOnEnableChanged(callback: (Boolean) -> Unit)
}

@ConsoleTarget
class Camera : CameraEngineInterface()
{
    override var viewMatrix: Matrix4f = Matrix4f()

    private var onEnabledCallback: (Boolean) -> Unit = {}
    private var target: Transform2D? = null
    private var enabled = true

    private var xLastPos: Float = 0f
    private var yLastPos: Float = 0f
    private var zLastPos: Float = 0f
    private var xLastRot: Float = 0f
    private var yLastRot: Float = 0f
    private var zLastRot: Float = 0f
    private var xLastScale: Float = 1f
    private var yLastScale: Float = 1f
    private var zLastScale: Float = 1f

    private val floatArray = FloatArray(16)
    private val invCameraMatrix = Matrix4f()
    private var cameraMatrix = Matrix4f()
    private var identityMatrix = Matrix4f()
    private val positionVector = Vector4f()
    private val worldPositionVector = Vector3f()
    private val screenPositionVector = Vector2f()

    override fun enable()
    {
        enabled = true
        onEnabledCallback.invoke(enabled)
    }

    override fun disable()
    {
        enabled = false
        onEnabledCallback.invoke(enabled)
    }

    override fun screenPosToWorldPos(x: Float, y: Float): Vector3f
    {
        cameraMatrix.invert(invCameraMatrix)
        val pos = positionVector.set(x, y, 0f, 1f).mul(invCameraMatrix)
        return worldPositionVector.set(pos.x, pos.y, pos.z)
    }

    override fun worldPosToScreenPos(x: Float, y: Float, z: Float): Vector2f
    {
        val pos = positionVector.set(x, y, z, 1f).mul(viewMatrix)
        return screenPositionVector.set(pos.x, pos.y)
    }

    override fun setTarget(target: Transform2D?)
    {
        this.target = target
    }

    override fun updateViewMatrix()
    {
        viewMatrix = if (enabled)
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

            cameraMatrix
                .setTranslation(xPos + xOrigin, yPos + yOrigin, zPos + zOrigin)
                .translate(-xOrigin, -yOrigin, -zOrigin)
                .setRotationXYZ(xRot, yRot, zRot)
                .scale(xScale, yScale, zScale)
        }
        else identityMatrix
    }

    override fun updateTransform(deltaTime: Float)
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

        target?.let { transform ->
            val targetScreenPos = worldPosToScreenPos(transform.x, transform.y)
            xPos += (-targetScreenPos.x - (xPos / xScale - xOrigin)) * targetTrackingSmoothing * deltaTime
            yPos += (-targetScreenPos.y - (yPos / yScale - yOrigin)) * targetTrackingSmoothing * deltaTime
        }
    }

    override fun setOnEnableChanged(callback: (Boolean) -> Unit)
    {
        onEnabledCallback = callback
    }

    override fun viewMatrixAsArray(): FloatArray
    {
        viewMatrix.get(floatArray)
        return floatArray
    }
}