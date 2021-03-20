package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.SceneState
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.Assets
import no.njoh.pulseengine.modules.graphics.Surface2D
import kotlin.math.*

open class CameraEntity : SceneEntity() {

    var viewPortWidth = 1000f
    var viewPortHeight = 800f

    private var lastWidth = 0f
    private var lastHeight = 0f
    private var camSize = 100f

    override fun onRender(surface: Surface2D, assets: Assets, sceneState: SceneState)
    {
        if (sceneState != SceneState.STOPPED)
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

        surface.setDrawColor(1f, 1f, 1f, 0.5f)
        surface.drawTexture(Texture.BLANK, x, y, camSize, camSize, rotation, xOrigin = 0.5f, yOrigin = 0.5f)

        val r = this.rotation / 180f * PI.toFloat()
        val c = cos(r) * 0.5f
        val s = sin(r) * 0.5f

        // Rectangle lines
        val x0 = -viewPortWidth * c - viewPortHeight * s
        val y0 = -viewPortWidth * s + viewPortHeight * c
        val x1 =  viewPortWidth * c - viewPortHeight * s
        val y1 =  viewPortWidth * s + viewPortHeight * c
        surface.setDrawColor(1f, 1f, 1f, 0.5f)
        surface.drawLine(x + x0, y + y0, x + x1, y + y1)
        surface.drawLine(x + x1, y + y1, x - x0, y - y0)
        surface.drawLine(x - x0, y - y0, x - x1, y - y1)
        surface.drawLine(x - x1, y - y1, x + x0, y + y0)

        // Diagonal lines
        val x2 = -width * c - height * s
        val y2 = -width * s + height * c
        val x3 =  width * c - height * s
        val y3 =  width * s + height * c
        surface.drawLine(x + x2, y + y2, x + x0, y + y0)
        surface.drawLine(x - x3, y - y3, x - x1, y - y1)
        surface.drawLine(x - x2, y - y2, x - x0, y - y0)
        surface.drawLine(x + x3, y + y3, x + x1, y + y1)
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
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