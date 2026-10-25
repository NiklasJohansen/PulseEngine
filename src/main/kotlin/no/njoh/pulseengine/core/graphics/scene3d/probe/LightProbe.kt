package no.njoh.pulseengine.core.graphics.scene3d.probe

import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs

class LightProbe
{
    var id                = -1L
    val position          = Vector3f()
    val rotation          = Quaternionf()
    val captureOffset     = Vector3f()
    val influenceSize     = Vector3f(10f)
    val projectionSize    = Vector3f(10f)
    var blendDistance     = 1f
    var specularIntensity = 1f
    var diffuseIntensity  = 1f
    var nearPlane         = 0.05f
    var farPlane          = 100f
    var active            = false
    var ready             = false
    var needsUpdate       = true
    val capturedPosition  = Vector3f()

    private val capturePosition     = Vector3f()
    private val nextCapturePosition = Vector3f()

    fun getCapturePosition(out: Vector3f): Vector3f = out.set(captureOffset).rotate(rotation).add(position)

    fun hasSameCapture(other: LightProbe): Boolean =
        id == other.id &&
        getCapturePosition(capturePosition) == other.getCapturePosition(nextCapturePosition) &&
        nearPlane == other.nearPlane &&
        farPlane == other.farPlane

    fun setFrom(other: LightProbe)
    {
        if (id != other.id)
            ready = false

        if (!hasSameCapture(other))
            needsUpdate = true

        id = other.id
        position.set(other.position)
        rotation.set(other.rotation)
        captureOffset.set(other.captureOffset)
        influenceSize.set(other.influenceSize)
        projectionSize.set(other.projectionSize)
        blendDistance = other.blendDistance
        specularIntensity = other.specularIntensity
        diffuseIntensity = other.diffuseIntensity
        nearPlane = other.nearPlane
        farPlane = other.farPlane
        active = true
    }

    fun isValid(): Boolean = 
        position.isFinite && 
        rotation.isFinite && abs(rotation.lengthSquared() - 1f) < 0.0001f &&
        captureOffset.isFinite && 
        influenceSize.isFinite && 
        projectionSize.isFinite &&
        influenceSize.x  > 0f && 
        influenceSize.y  > 0f && 
        influenceSize.z  > 0f &&
        projectionSize.x > 0f && 
        projectionSize.y > 0f && 
        projectionSize.z > 0f &&
        blendDistance.isFinite() && 
        blendDistance >= 0f && 
        specularIntensity.isFinite() && 
        specularIntensity >= 0f &&
        diffuseIntensity.isFinite() &&
        diffuseIntensity >= 0f &&
        nearPlane.isFinite() && 
        farPlane.isFinite() && 
        nearPlane > 0f && 
        farPlane > nearPlane
}