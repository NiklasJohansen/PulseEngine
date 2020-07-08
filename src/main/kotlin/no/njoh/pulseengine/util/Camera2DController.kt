package no.njoh.pulseengine.util

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Mouse
import no.njoh.pulseengine.modules.graphics.CameraInterface
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

    fun update(engine: PulseEngine, camera: CameraInterface? = null)
    {
        val cam = camera ?: engine.gfx.mainCamera

        scaleChangeRate += engine.input.scroll * 0.01f * min(1f, cam.xScale)

        if (scaleChangeRate != 0f)
        {
            val xScale = min(maxScale, max(minScale, cam.xScale + scaleChangeRate))
            val yScale = min(maxScale, max(minScale, cam.yScale + scaleChangeRate))
            val xScaleDiff = xScale - cam.xScale
            val yScaleDiff = yScale - cam.yScale

            cam.xScale = xScale
            cam.yScale = yScale
            cam.xPos -= engine.input.xWorldMouse * xScaleDiff
            cam.yPos -= engine.input.yWorldMouse * yScaleDiff
        }

        if (engine.input.isPressed(dragMouseKey))
        {
            xPosChangeRate += engine.input.xdMouse * (1f - smoothing)
            yPosChangeRate += engine.input.ydMouse * (1f - smoothing)
        }

        cam.xPos += xPosChangeRate
        cam.yPos += yPosChangeRate

        xPosChangeRate *= smoothing
        yPosChangeRate *= smoothing
        scaleChangeRate *= 0.9f
    }
}