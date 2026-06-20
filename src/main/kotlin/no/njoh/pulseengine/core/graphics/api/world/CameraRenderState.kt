package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.Frustum
import org.joml.Matrix4f
import org.joml.Vector3f

class CameraRenderState(var camera: Camera) 
{
    val frustum              = Frustum()
    val viewMatrix           = Matrix4f()
    val projectionMatrix     = Matrix4f()
    val viewProjectionMatrix = Matrix4f()
    val cameraPosition       = Vector3f()
    val cameraRight          = Vector3f()

    var screenWidth  = 1;     private set
    var screenHeight = 1;     private set
    var nearPlane    = 0.05f; private set
    var farPlane     = 1f;    private set

    fun setForCamera(camera: Camera, screenWidth: Int, screenHeight: Int)
    {
        camera.invViewMatrix.getTranslation(cameraPosition)
 
        this.cameraRight.set(camera.invViewMatrix.m00(), camera.invViewMatrix.m01(), camera.invViewMatrix.m02()).normalize()
        this.frustum.setForCamera(camera)
        this.viewMatrix.set(camera.viewMatrix)
        this.projectionMatrix.set(camera.projectionMatrix)
        this.viewProjectionMatrix.set(camera.viewProjectionMatrix)
        this.screenWidth = screenWidth.coerceAtLeast(1)
        this.screenHeight = screenHeight.coerceAtLeast(1)
        this.nearPlane = camera.nearPlane
        this.farPlane = camera.farPlane
    }
}