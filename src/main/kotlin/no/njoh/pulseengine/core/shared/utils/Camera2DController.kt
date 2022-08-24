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

    fun update(engine: PulseEngine, camera: Camera? = null, enableScrolling: Boolean = true)
    {
        val cam = camera ?: engine.gfx.mainCamera

        if (enableScrolling)
            scaleChangeRate += engine.input.scroll * 0.01f * min(1f, cam.scale.x)

        if (scaleChangeRate != 0f)
        {
            val xScale = min(maxScale, max(minScale, cam.scale.x + scaleChangeRate))
            val yScale = min(maxScale, max(minScale, cam.scale.y + scaleChangeRate))
            val xScaleDiff = xScale - cam.scale.x
            val yScaleDiff = yScale - cam.scale.y
            val xCenter = engine.window.width * 0.5f
            val yCenter = engine.window.height * 0.5f

            cam.scale.x = xScale
            cam.scale.y = yScale
            cam.origin.x = xCenter
            cam.origin.y = yCenter
            cam.position.x -= (engine.input.xMouse - xCenter) * xScaleDiff / (xScale * xScale)
            cam.position.y -= (engine.input.yMouse - yCenter) * yScaleDiff / (yScale * yScale)
        }

        if (engine.input.isPressed(dragMouseKey))
        {
            xPosChangeRate += engine.input.xdMouse / cam.scale.x * (1f - smoothing)
            yPosChangeRate += engine.input.ydMouse / cam.scale.y * (1f - smoothing)
        }

        cam.position.x += xPosChangeRate
        cam.position.y += yPosChangeRate

        xPosChangeRate *= smoothing
        yPosChangeRate *= smoothing
        scaleChangeRate *= 0.9f
    }
}