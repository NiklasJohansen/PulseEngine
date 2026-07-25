package no.njoh.pulseengine.modules.editor

import no.njoh.pulseengine.core.graphics.camera.Camera
import no.njoh.pulseengine.core.graphics.camera.CameraInternal
import no.njoh.pulseengine.core.graphics.camera.CameraProjectionType
import no.njoh.pulseengine.core.graphics.camera.CameraProjectionType.ORTHOGRAPHIC_2D
import no.njoh.pulseengine.core.graphics.camera.CameraProjectionType.PERSPECTIVE_3D
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** 
 * Complete mutable camera state owned by one editor viewport interaction. 
 */
data class CameraState(
    val pos: Vector3f,
    val rot: Vector3f,
    val origin: Vector3f,
    val scale: Vector3f,
    var fov: Float,
    var orthographicHeight: Float,
    var nearPlane: Float,
    var farPlane: Float,
    var projectionType: CameraProjectionType
) {
    fun duplicate() = CameraState(
        pos = Vector3f(pos),
        rot = Vector3f(rot),
        origin = Vector3f(origin),
        scale = Vector3f(scale),
        fov = fov,
        orthographicHeight = orthographicHeight,
        nearPlane = nearPlane,
        farPlane = farPlane,
        projectionType = projectionType
    )

    fun saveFrom(camera: Camera)
    {
        pos.set(camera.position)
        rot.set(camera.rotation)
        origin.set(camera.origin)
        scale.set(camera.scale)
        fov = camera.fov
        orthographicHeight = camera.orthographicHeight
        nearPlane = camera.nearPlane
        farPlane = camera.farPlane
        projectionType = camera.projectionType
    }

    fun loadInto(camera: Camera, width: Int, height: Int)
    {
        camera.position.set(pos)
        camera.rotation.set(rot)
        camera.origin.set(origin)
        camera.scale.set(scale)
        camera.fov = fov
        camera.orthographicHeight = orthographicHeight
        camera.nearPlane = nearPlane
        camera.farPlane = farPlane
        camera.updateProjection(width, height, projectionType)
        (camera as? CameraInternal)?.updateLastState()
    }

    fun sanitizePerspective3D()
    {
        val defaults = perspective3D()
        if (!pos.x.isFinite() || !pos.y.isFinite() || !pos.z.isFinite()) pos.set(defaults.pos)
        if (!rot.x.isFinite() || !rot.y.isFinite() || !rot.z.isFinite()) rot.set(defaults.rot)
        rot.x = rot.x.coerceIn(-MAX_PITCH, MAX_PITCH)
        rot.y = atan2(sin(rot.y), cos(rot.y))
        rot.z = 0f
        origin.zero()
        scale.set(1f)
        fov = if (fov.isFinite()) fov.coerceIn(1f, 179f) else defaults.fov
        nearPlane = if (nearPlane.isFinite()) nearPlane.coerceAtLeast(0.001f) else defaults.nearPlane
        farPlane = if (farPlane.isFinite()) farPlane.coerceAtLeast(nearPlane + 0.001f) else defaults.farPlane
        projectionType = PERSPECTIVE_3D
    }

    companion object
    {
        private const val MAX_PITCH = PI.toFloat() * 0.5f - 0.0174533f

        fun orthographic2D() = CameraState(
            pos = Vector3f(),
            rot = Vector3f(),
            origin = Vector3f(),
            scale = Vector3f(1f),
            fov = 90f,
            orthographicHeight = 10f,
            nearPlane = -1f,
            farPlane = 5f,
            projectionType = ORTHOGRAPHIC_2D
        )

        // Three-quarter view looking from (5, 5, 5) directly at the origin.
        fun perspective3D() = CameraState(
            pos = Vector3f(5f, 5f, 5f),
            rot = Vector3f(-0.6154797f, 0.7853982f, 0f),
            origin = Vector3f(),
            scale = Vector3f(1f),
            fov = 90f,
            orthographicHeight = 10f,
            nearPlane = 0.01f,
            farPlane = 200f,
            projectionType = PERSPECTIVE_3D
        )

        fun from(camera: Camera): CameraState
        {
            return CameraState(
                pos = Vector3f(camera.position),
                rot = Vector3f(camera.rotation),
                origin = Vector3f(camera.origin),
                scale = Vector3f(camera.scale),
                fov = camera.fov,
                orthographicHeight = camera.orthographicHeight,
                nearPlane = camera.nearPlane,
                farPlane = camera.farPlane,
                projectionType = camera.projectionType
            )
        }
    }
}