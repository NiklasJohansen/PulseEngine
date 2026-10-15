package no.njoh.pulseengine.modules.editor

import no.njoh.pulseengine.core.graphics.camera.Camera
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

class GizmoRenderContext
{
    lateinit var camera: Camera; private set
    
    var width  = 0; private set
    var height = 0; private set

    val cameraRight = Vector3f()
    val cameraUp    = Vector3f()

    private val clip           = Vector4f()
    private val cameraPosition = Vector3f()
    private val circleStart    = Vector3f()
    private val circleEnd      = Vector3f()
    private val corners        = Array(8) { Vector3f() }

    fun setFor(camera: Camera, width: Int, height: Int)
    {
        this.camera = camera
        this.width  = width
        this.height = height
        camera.invViewMatrix.getTranslation(cameraPosition)
        camera.invViewMatrix.transformDirection(cameraRight.set(1f, 0f, 0f)).normalize()
        camera.invViewMatrix.transformDirection(cameraUp.set(0f, 1f, 0f)).normalize()
    }

    fun project(position: Vector3f, out: Vector2f): Boolean
    {
        if (width <= 0 || height <= 0 || !position.isFinite) 
            return false
        
        clip.set(position, 1f).mul(camera.viewProjectionMatrix)
        if (!clip.isFinite || clip.w <= 1e-6f) 
            return false
        
        val x = clip.x / clip.w
        val y = clip.y / clip.w
        out.set((x * 0.5f + 0.5f) * width, (0.5f - y * 0.5f) * height)

        // Check if the projected point is within the normalized device coordinates (NDC) range of -1 to 1 for both x and y axes
        return (x in -2f..2f) && (y in -2f..2f)
    }

    fun getWorldSize(position: Vector3f, pixels: Float = 90f): Float
    {
        val distance = max(cameraPosition.distance(position), 0.01f)
        return max(2f * distance * tan(camera.fov.toRadians() * 0.5f) * pixels / max(height, 1), 0.001f)
    }

    fun drawMarker(surface: Surface, position: Vector3f, radius: Float, cross: Boolean = false)
    {
        if (!position.isFinite || !radius.isFinite() || radius <= 0f)
            return

        surface.getRenderer<GizmoRenderer>()?.drawMarker(position, radius, cross)
    }

    fun drawLine(surface: Surface, start: Vector3f, end: Vector3f, dashed: Boolean = false)
    {
        if (!start.isFinite || !end.isFinite)
            return

        surface.getRenderer<GizmoRenderer>()?.drawLine(start, end, dashed)
    }

    fun drawCircle(surface: Surface, center: Vector3f, right: Vector3f, up: Vector3f, radius: Float)
    {
        if (!radius.isFinite() || radius <= 0f) 
            return
        
        circleStart.set(right).mul(radius).add(center)

        for (i in 1..48)
        {
            val angle = i * (Math.PI.toFloat() * 2f / 48)
            circleEnd.set(center).fma(cos(angle) * radius, right).fma(sin(angle) * radius, up)
            drawLine(surface, circleStart, circleEnd)
            circleStart.set(circleEnd)
        }
    }

    fun drawBox(surface: Surface, center: Vector3f, size: Vector3f, dashed: Boolean = false, rotation: Quaternionf? = null)
    {
        if (!size.isFinite || size.x <= 0f || size.y <= 0f || size.z <= 0f) return
        for (i in 0..7)
        {
            corners[i].set(
                size.x * (if (i and 1 == 0) -0.5f else 0.5f),
                size.y * (if (i and 2 == 0) -0.5f else 0.5f),
                size.z * (if (i and 4 == 0) -0.5f else 0.5f)
            )
            if (rotation != null) corners[i].rotate(rotation)
            corners[i].add(center)
        }

        for (i in 0..7)
        {
            for (axis in 0..2)
            {
                if (i and (1 shl axis) == 0) 
                    drawLine(surface, corners[i], corners[i or (1 shl axis)], dashed)
            }
        }
    }

    fun drawFrustum(surface: Surface, position: Vector3f, rotation: Quaternionf, fov: Float, near: Float, far: Float)
    {
        if (width <= 0 || height <= 0) 
            return

        val slope = tan(fov.toRadians() * 0.5f)
        val aspect = width.toFloat() / height

        for (i in 0..7)
        {
            val distance = if (i < 4) near else far
            val halfHeight = slope * distance
            corners[i].set(
                halfHeight * aspect * (if (i and 1 == 0) -1f else 1f),
                halfHeight * (if (i and 2 == 0) -1f else 1f),
                -distance
            ).rotate(rotation).add(position)
        }
        
        for (i in 0..7)
        {
            for (axis in 0..1)
            {
                if (i and (1 shl axis) == 0) 
                    drawLine(surface, corners[i], corners[i or (1 shl axis)])
            }
        }

        for (i in 4..7)
        {
            drawLine(surface, position, corners[i])
        }
    }

    companion object
    {
        const val MARKER_PICK_PADDING = 6f
        val ACTIVE_COLOR = Color(1f, 0.8f, 0.12f, 1f)
    }
}