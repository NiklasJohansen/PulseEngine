package no.njoh.pulseengine.modules.scene.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.shared.annotations.ScnIcon
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import kotlin.math.*

@ScnIcon("CAMERA", size = 24f, showInViewport = true)
open class Camera : StandardSceneEntity()
{
    var viewPortWidth = 1000f
    var viewPortHeight = 800f
    var targetEntityId = INVALID_ID
    var trackRotation = false
    var smoothing = 0.1f
    var targetZoom = 1f

    @JsonIgnore private var initalized = false
    @JsonIgnore private var lastWidth = 0f
    @JsonIgnore private var lastHeight = 0f
    @JsonIgnore private var camSize = 100f
    @JsonIgnore private var zoom = targetZoom

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
        val opacity = 0.8f

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

        // Top
        surface.setDrawColor(0f, 0f, 0f, 0.5f * opacity)
        surface.drawQuadVertex(x - x1, y - y1) // Left top
        surface.drawQuadVertex(x - x0, y - y0) // Right top
        surface.drawQuadVertex(x - x2, y - y2) // Right bottom
        surface.drawQuadVertex(x - x3, y - y3) // Left bottom

        // Right
        surface.setDrawColor(0f, 0f, 0f, 0.3f * opacity)
        surface.drawQuadVertex(x - x2, y - y2) // Left top
        surface.drawQuadVertex(x - x0, y - y0) // Right top
        surface.drawQuadVertex(x + x1, y + y1) // Right bottom
        surface.drawQuadVertex(x + x3, y + y3) // Left bottom

        // Bottom
        surface.setDrawColor(0f, 0f, 0f, 0.25f * opacity)
        surface.drawQuadVertex(x + x2, y + y2) // Left top
        surface.drawQuadVertex(x + x3, y + y3) // Right top
        surface.drawQuadVertex(x + x1, y + y1) // Right bottom
        surface.drawQuadVertex(x + x0, y + y0) // Left bottom

        // Left
        surface.setDrawColor(0f, 0f, 0f, 0.4f * opacity)
        surface.drawQuadVertex(x - x1, y - y1) // Left top
        surface.drawQuadVertex(x - x3, y - y3) // Right top
        surface.drawQuadVertex(x + x2, y + y2) // Right bottom
        surface.drawQuadVertex(x + x0, y + y0) // Left bottom

        // Outer Rectangle lines
        surface.setDrawColor(1f, 1f, 1f, 0.8f)
        surface.drawLine(x + x0, y + y0, x + x1, y + y1)
        surface.drawLine(x + x1, y + y1, x - x0, y - y0)
        surface.drawLine(x - x0, y - y0, x - x1, y - y1)
        surface.drawLine(x - x1, y - y1, x + x0, y + y0)

        // Diagonal lines
        surface.setDrawColor(0f, 0f, 0f, 0.8f)
        surface.drawLine(x + x2, y + y2, x + x0, y + y0) // Left bottom
        surface.drawLine(x - x3, y - y3, x - x1, y - y1) // Left top
        surface.drawLine(x - x2, y - y2, x - x0, y - y0) // Right top
        surface.drawLine(x + x3, y + y3, x + x1, y + y1) // Right bottom
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        engine.scene.getEntityOfType<Spatial>(targetEntityId)?.let { trackEntity(it) }

        val surfaceWidth = engine.gfx.mainSurface.width
        val surfaceHeight = engine.gfx.mainSurface.height
        val newScale = min(surfaceWidth / viewPortWidth,  surfaceHeight / viewPortHeight) * zoom
        engine.gfx.mainCamera.apply()
        {
            scale.set(newScale)
            rotation.z = -super.rotation / 180f * PI.toFloat()
            origin.x = surfaceWidth * 0.5f
            origin.y = surfaceHeight * 0.5f
            position.x = surfaceWidth * 0.5f - x
            position.y = surfaceHeight * 0.5f - y
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
            zoom = targetZoom
            initalized = true
            return
        }

        x += (entity.x - x) * smoothing
        y += (entity.y - y) * smoothing
        zoom += (targetZoom - zoom) * smoothing
        if (trackRotation)
        {
            val diff = (entity.rotation - rotation).toRadians()
            rotation += atan2(sin(diff), cos(diff)).toDegrees() * smoothing
        }
    }
}