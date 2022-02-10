package no.njoh.pulseengine.core.shared.utils

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.Mouse
import no.njoh.pulseengine.core.graphics.Camera
import kotlin.math.max
import kotlin.math.min

class Camera2DController(
    private var dragMouseKey: Mouse,
    private var smoothing: Float = 0.92f,
    private var minScale: Float = 0.05f,
    private var maxScale: Float = 5f
) {
    private var scaleChangeRate = 0f
    private var xPosChangeRate = 0f
    private var yPosChangeRate = 0f

    fun update(engine: PulseEngine, camera: Camera? = null)
    {
        val cam = camera ?: engine.gfx.mainCamera

        scaleChangeRate += engine.input.scroll * 0.01f * min(1f, cam.xScale)

        if (scaleChangeRate != 0f)
        {
            val xScale = min(maxScale, max(minScale, cam.xScale + scaleChangeRate))
            val yScale = min(maxScale, max(minScale, cam.yScale + scaleChangeRate))
            val xScaleDiff = xScale - cam.xScale
            val yScaleDiff = yScale - cam.yScale
            val xCenter = engine.window.width * 0.5f
            val yCenter = engine.window.height * 0.5f

            cam.xScale = xScale
            cam.yScale = yScale
            cam.xOrigin = xCenter
            cam.yOrigin = yCenter
            cam.xPos -= (engine.input.xMouse - xCenter) * xScaleDiff / (xScale * xScale)
            cam.yPos -= (engine.input.yMouse - yCenter) * yScaleDiff / (yScale * yScale)
        }

        if (engine.input.isPressed(dragMouseKey))
        {
            xPosChangeRate += engine.input.xdMouse / cam.xScale * (1f - smoothing)
            yPosChangeRate += engine.input.ydMouse / cam.yScale * (1f - smoothing)
        }

        cam.xPos += xPosChangeRate
        cam.yPos += yPosChangeRate

        xPosChangeRate *= smoothing
        yPosChangeRate *= smoothing
        scaleChangeRate *= 0.9f
    }
}