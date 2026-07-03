package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.linearToSrgb

/**
 * Reusable destination for asynchronous surface pixel reads. 
 * A completed value remains available while the next read is pending.
 */
class PixelReadResult
{
    val sRgbColor get() = colors[completedIndex]
    val redInt    get() = redValues[completedIndex]
    val greenInt  get() = greenValues[completedIndex]
    val blueInt   get() = blueValues[completedIndex]
    val alphaInt  get() = alphaValues[completedIndex]

    @Volatile var isReady   = false; private set
    @Volatile var isPending = false; private set

    @Volatile 
    private var completedIndex = 0
    private var version        = 0L
    private val colors         = arrayOf(Color(0f, 0f, 0f, 0f), Color(0f, 0f, 0f, 0f))
    private val redValues      = IntArray(2)
    private val greenValues    = IntArray(2)
    private val blueValues     = IntArray(2)
    private val alphaValues    = IntArray(2)

    @Synchronized
    internal fun tryPrepare(): Long?
    {
        if (isPending) return null
        isPending = true
        return ++version
    }

    internal fun completeInt(version: Long, linearRed: Int, linearGreen: Int, linearBlue: Int, alpha: Int)
    {
        if (this.version != version) return

        val nextIndex = 1 - completedIndex

        redValues[nextIndex]   = linearRed
        greenValues[nextIndex] = linearGreen
        blueValues[nextIndex]  = linearBlue
        alphaValues[nextIndex] = alpha
        
        colors[nextIndex].setFromRgba(
            red   = (linearRed.coerceIn(0, 255) / 255f).linearToSrgb(),
            green = (linearGreen.coerceIn(0, 255) / 255f).linearToSrgb(),
            blue  = (linearBlue.coerceIn(0, 255) / 255f).linearToSrgb(),
            alpha = alpha.coerceIn(0, 255) / 255f
        )

        publish(nextIndex)
    }

    internal fun completeFloat(version: Long, linearRed: Float, linearGreen: Float, linearBlue: Float, alpha: Float)
    {
        if (this.version != version) return

        val nextIndex = 1 - completedIndex

        redValues[nextIndex]   = (linearRed.coerceIn(0f, 1f) * 255f).toInt()
        greenValues[nextIndex] = (linearGreen.coerceIn(0f, 1f) * 255f).toInt()
        blueValues[nextIndex]  = (linearBlue.coerceIn(0f, 1f) * 255f).toInt()
        alphaValues[nextIndex] = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        colors[nextIndex].setFromRgba(
            red   = linearRed.linearToSrgb(),
            green = linearGreen.linearToSrgb(),
            blue  = linearBlue.linearToSrgb(),
            alpha = alpha
        )

        publish(nextIndex)
    }

    @Synchronized
    internal fun cancel(version: Long)
    {
        if (this.version == version) isPending = false
    }

    private fun publish(nextIndex: Int)
    {
        completedIndex = nextIndex
        isReady = true
        isPending = false
    }
}