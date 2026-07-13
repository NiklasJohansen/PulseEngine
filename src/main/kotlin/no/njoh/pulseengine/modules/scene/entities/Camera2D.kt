package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.camera.CameraProjectionType.ORTHOGRAPHIC_2D
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import kotlin.math.*

@Icon("CAMERA", size = 24f, showInViewport = true)
open class Camera2D : CommonSceneEntity()
{
    var viewPortWidth  = 1000f
    var viewPortHeight = 800f
    var xOrigin        = 0.5f
    var yOrigin        = 0.5f
    var targetEntityId = INVALID_ID
    var trackRotation  = false
    var smoothing      = 0.1f
    var targetZoom     = 1f

    private var initialized = false
    private var lastWidth   = 0f
    private var lastHeight  = 0f
    private var camSize     = 100f
    private var zoom        = targetZoom

    override fun onStart(engine: PulseEngine)
    {
        zoom = targetZoom
        updateCamera(engine)
    }

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        if (engine.scene.state != SceneState.STOPPED)
            return

        if (width != camSize || height != camSize)
        {
            viewPortWidth += width - lastWidth
            viewPortHeight += height - lastHeight
        }

        lastWidth = width
        lastHeight = height
        width = camSize
        height = camSize

        if (!isSet(SELECTED))
            return

        val r = -this.rotation / 180f * PI.toFloat()
        val c = cos(r) * 0.5f
        val s = sin(r) * 0.5f

        // Outer rectangle points
        val x0 = -viewPortWidth * c - viewPortHeight * s
        val y0 = -viewPortWidth * s + viewPortHeight * c
        val x1 =  viewPortWidth * c - viewPortHeight * s
        val y1 =  viewPortWidth * s + viewPortHeight * c

        // Inner rectangle points
        val x2 = -width * c - height * s
        val y2 = -width * s + height * c
        val x3 =  width * c - height * s
        val y3 =  width * s + height * c

        // Outer Rectangle lines
        surface.setDrawColor(1f, 1f, 1f, 1f)
        surface.drawLine(x + x0, y + y0, x + x1, y + y1)
        surface.drawLine(x + x1, y + y1, x - x0, y - y0)
        surface.drawLine(x - x0, y - y0, x - x1, y - y1)
        surface.drawLine(x - x1, y - y1, x + x0, y + y0)

        // Diagonal lines
        surface.drawLine(x + x2, y + y2, x + x0, y + y0) // Left bottom
        surface.drawLine(x - x3, y - y3, x - x1, y - y1) // Left top
        surface.drawLine(x - x2, y - y2, x - x0, y - y0) // Right top
        surface.drawLine(x + x3, y + y3, x + x1, y + y1) // Right bottom
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        updateCamera(engine)
    }

    private fun updateCamera(engine: PulseEngine)
    {
        engine.scene.getEntityOfType<Spatial>(targetEntityId)?.let { trackEntity(it) }

        zoom += (targetZoom - zoom) * smoothing

        val surfaceWidth = engine.gfx.mainSurface.config.width
        val surfaceHeight = engine.gfx.mainSurface.config.height
        val newScale = min(surfaceWidth / viewPortWidth,  surfaceHeight / viewPortHeight) * zoom
        engine.gfx.mainCamera.apply()
        {
            position.set(surfaceWidth * xOrigin - x, y - surfaceHeight * yOrigin, 0f)
            rotation.set(0f, 0f, -super.rotation.toRadians())
            origin.set(surfaceWidth * xOrigin, surfaceHeight * (1f - yOrigin), 0f)
            scale.set(newScale, newScale, 1f)
        }
        configureCameraMode(engine)
    }

    private fun configureCameraMode(engine: PulseEngine)
    {
        val camera = engine.gfx.mainCamera
        if (camera.projectionType == ORTHOGRAPHIC_2D)
            return

        camera.nearPlane = -1f
        camera.farPlane = 5f
        camera.updateProjection(engine.window.width, engine.window.height, ORTHOGRAPHIC_2D)
    }

    private fun trackEntity(entity: Spatial)
    {
        if (!initialized)
        {
            x = entity.x
            y = entity.y
            if (trackRotation)
                rotation = entity.rotation
            initialized = true
            return
        }

        x += (entity.x - x) * smoothing
        y += (entity.y - y) * smoothing

        if (trackRotation)
        {
            val diff = (entity.rotation - rotation).toRadians()
            rotation += atan2(sin(diff), cos(diff)).toDegrees() * smoothing
        }
    }
}