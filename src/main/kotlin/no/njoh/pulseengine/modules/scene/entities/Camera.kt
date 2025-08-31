package no.njoh.pulseengine.modules.scene.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import kotlin.math.*

@Icon("CAMERA", size = 24f, showInViewport = true)
open class Camera : StandardSceneEntity()
{
    var viewPortWidth = 1000f
    var viewPortHeight = 800f
    var xOrigin = 0.5f
    var yOrigin = 0.5f
    var targetEntityId = INVALID_ID
    var trackRotation = false
    var smoothing = 0.1f
    var targetZoom = 1f

    @JsonIgnore private var initalized = false
    @JsonIgnore private var lastWidth = 0f
    @JsonIgnore private var lastHeight = 0f
    @JsonIgnore private var camSize = 100f
    @JsonIgnore private var zoom = targetZoom

    override fun onStart(engine: PulseEngine)
    {
        zoom = targetZoom
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
        engine.scene.getEntityOfType<Spatial>(targetEntityId)?.let { trackEntity(it) }

        zoom += (targetZoom - zoom) * smoothing

        val surfaceWidth = engine.gfx.mainSurface.config.width
        val surfaceHeight = engine.gfx.mainSurface.config.height
        val newScale = min(surfaceWidth / viewPortWidth,  surfaceHeight / viewPortHeight) * zoom
        engine.gfx.mainCamera.apply()
        {
            scale.set(newScale)
            rotation.z = -super.rotation.toRadians()
            origin.x = surfaceWidth * xOrigin
            origin.y = surfaceHeight * yOrigin
            position.x = surfaceWidth * xOrigin - x
            position.y = surfaceHeight * yOrigin - y
        }
    }

    private fun trackEntity(entity: Spatial)
    {
        if (!initalized)
        {
            x = entity.x
            y = entity.y
            if (trackRotation)
                rotation = entity.rotation
            initalized = true
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