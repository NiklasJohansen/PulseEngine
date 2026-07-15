package no.njoh.pulseengine.core.graphics.camera

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.camera.CameraProjectionType.ORTHOGRAPHIC_2D
import no.njoh.pulseengine.core.graphics.camera.CameraProjectionType.ORTHOGRAPHIC_3D
import no.njoh.pulseengine.core.graphics.camera.CameraProjectionType.PERSPECTIVE_3D
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateFrom
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

class DefaultCamera(override var projectionType: CameraProjectionType) : CameraInternal() {

    override var projectionMatrix = Matrix4f()
    override var viewProjectionMatrix = Matrix4f()

    private val returnVector = Vector4f()
    private val worldPositionVector = Vector3f()
    private val screenPositionVector = Vector2f()
    private val iPos = Vector3f()
    private val iRot = Vector3f()
    private val lastRotQuaternion = Quaternionf()
    private val currentRotQuaternion = Quaternionf()
    private val iRotQuaternion = Quaternionf()
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
        val worldY = if (projectionType == ORTHOGRAPHIC_2D) screenHeight - yUp else yUp
        return worldPositionVector.set(xUp, worldY, zUp)
    }

    override fun worldPosToScreenPos(x: Float, y: Float, z: Float, screenWidth: Int, screenHeight: Int): Vector2f
    {
        val yUp = if (projectionType == ORTHOGRAPHIC_2D) screenHeight - y else y
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
            ORTHOGRAPHIC_2D -> Matrix4f().ortho(0f, width.toFloat(), 0f, height.toFloat(), nearPlane, farPlane) // Y-up
            ORTHOGRAPHIC_3D -> {
                val halfHeight = orthographicHeight.coerceAtLeast(0.001f) * 0.5f
                val halfWidth = halfHeight * width.toFloat() / height.coerceAtLeast(1).toFloat()
                Matrix4f().ortho(-halfWidth, halfWidth, -halfHeight, halfHeight, nearPlane, farPlane)
            }
            PERSPECTIVE_3D ->
            {
                Matrix4f().perspective(fov.toRadians(), width.toFloat() / height.coerceAtLeast(1).toFloat(), nearPlane, farPlane)
            }
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
        origin.interpolateFrom(originLast, destination = iOrigin)
        scale.interpolateFrom(scaleLast, destination = iScale)

        when (projectionType)
        {
            ORTHOGRAPHIC_2D ->
            {
                rotation.interpolateFrom(rotationLast, destination = iRot)
                viewMatrix
                    .identity()
                    .translate(iOrigin)
                    .scale(iScale)
                    .rotateXYZ(iRot.x, iRot.y, iRot.z)
                    .translate(iPos.x - iOrigin.x, iPos.y - iOrigin.y, iPos.z - iOrigin.z)

                invViewMatrix.set(viewMatrix).invert()
            }
            ORTHOGRAPHIC_3D, PERSPECTIVE_3D ->
            {
                interpolate3DRotation(engine.data.interpolation)
                invViewMatrix
                    .identity()
                    .translate(iPos)
                    .rotate(iRotQuaternion)

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

    private fun interpolate3DRotation(t: Float): Quaternionf
    {
        lastRotQuaternion.rotationYXZ(rotationLast.y, rotationLast.x, rotationLast.z)
        currentRotQuaternion.rotationYXZ(rotation.y, rotation.x, rotation.z)
        return iRotQuaternion.set(lastRotQuaternion)
            .slerp(currentRotQuaternion, t.coerceIn(0f, 1f))
            .normalize()
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
        fun createOrthographic(width: Int, height: Int) = DefaultCamera(ORTHOGRAPHIC_2D).also { it.updateProjection(width, height) }
    }
}