package no.njoh.pulseengine.modules.scene.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.scene.SceneState
import no.njoh.pulseengine.modules.asset.types.Texture
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.shared.utils.Extensions.toDegrees
import no.njoh.pulseengine.modules.shared.utils.Extensions.toRadians
import kotlin.math.*

open class Camera : SceneEntity()
{
    var viewPortWidth = 1000f
    var viewPortHeight = 800f
    var targetEntityId = -1L
    var trackRotation = false
    var smoothing = 0.1f

    @JsonIgnore private var initalized = false
    @JsonIgnore private var lastWidth = 0f
    @JsonIgnore private var lastHeight = 0f
    @JsonIgnore private var camSize = 100f

    override fun onRender(engine: PulseEngine, surface: Surface2D)
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

        surface.setDrawColor(0.01f, 0.01f, 0.01f, 1f)
        surface.drawTexture(Texture.BLANK, x, y, camSize, camSize, rotation, xOrigin = 0.5f, yOrigin = 0.5f)

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

        surface.setDrawColor(1f, 1f, 1f, 1f)
        surface.drawText("CAMERA", x, y, xOrigin = 0.5f, yOrigin = 0.5f)

        // Top
        surface.setDrawColor(1f, 1f, 1f, 0.5f * 0.75f)
        surface.drawQuadVertex(x - x1, y - y1) // Left top
        surface.drawQuadVertex(x - x0, y - y0) // Right top
        surface.drawQuadVertex(x - x2, y - y2) // Right bottom
        surface.drawQuadVertex(x - x3, y - y3) // Left bottom

        // Right
        surface.setDrawColor(1f, 1f, 1f, 0.3f * 0.75f)
        surface.drawQuadVertex(x - x2, y - y2) // Left top
        surface.drawQuadVertex(x - x0, y - y0) // Right top
        surface.drawQuadVertex(x + x1, y + y1) // Right bottom
        surface.drawQuadVertex(x + x3, y + y3) // Left bottom

        // Bottom
        surface.setDrawColor(1f, 1f, 1f, 0.25f * 0.75f)
        surface.drawQuadVertex(x + x2, y + y2) // Left top
        surface.drawQuadVertex(x + x3, y + y3) // Right top
        surface.drawQuadVertex(x + x1, y + y1) // Right bottom
        surface.drawQuadVertex(x + x0, y + y0) // Left bottom

        // Left
        surface.setDrawColor(1f, 1f, 1f, 0.4f * 0.75f)
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
        engine.scene.getEntity(targetEntityId)?.let {
            if (!initalized)
            {
                x = it.x
                y = it.y
                if (trackRotation)
                    rotation = it.rotation
                initalized = true
            }
            else
            {
                x += (it.x - x) * smoothing
                y += (it.y - y) * smoothing
                if (trackRotation)
                {
                    val diff = (it.rotation - rotation).toRadians()
                    rotation += atan2(sin(diff), cos(diff)).toDegrees() * smoothing
                }
            }
        }

        val surfaceWidth = engine.gfx.mainSurface.width
        val surfaceHeight = engine.gfx.mainSurface.height
        val scale = min(surfaceWidth / viewPortWidth,  surfaceHeight / viewPortHeight)
        engine.gfx.mainCamera.apply {
            xScale = scale
            yScale = scale
            zRot = -rotation / 180f * PI.toFloat()
            xOrigin = surfaceWidth * 0.5f
            yOrigin = surfaceHeight * 0.5f
            xPos = surfaceWidth * 0.5f - x
            yPos = surfaceHeight * 0.5f - y
        }
    }
}