package game.cave

import kotlin.math.abs
import kotlin.math.floor

object NoiseGenerator {

    // noise < 0.f && noise > -0.1f
    fun getRidgedFractalNoise(x: Float, y: Float, nOctaves: Int): Float
    {
        return 1.0f - abs(getFractalNoise(x, y, nOctaves))
    }

    fun getFractalNoise(x: Float, y: Float, nOctaves: Int): Float
    {
        var result = 0f
        var frequency = 0.02f
        var amplitude = 0.5f

        for (i in 0 until nOctaves) {
            result += getNoise(x, y, frequency) * amplitude
            frequency *= 2f
            amplitude *= 0.5f
        }

        return result
    }

    fun getNoise(x: Float, y: Float, frequency: Float): Float
    {
        val x2 = x * frequency
        val y2 = y * frequency

        val x2Floored = floor(x2).toInt()
        val y2Floored = floor(y2).toInt()

        // Generate noise value for each corner
        val topLeft     = getFloatHash(x2Floored, y2Floored)
        val topRight    = getFloatHash(x2Floored + 1, y2Floored)
        val bottomRight = getFloatHash(x2Floored + 1, y2Floored + 1)
        val bottomLeft  = getFloatHash(x2Floored, y2Floored + 1)

        // Bilinear filtering
        val top = lerp(topLeft, topRight, smoothstep(fract(x2)))
        val bottom = lerp(bottomLeft, bottomRight, smoothstep(fract(x2)))

        return lerp(top, bottom, smoothstep(fract(y2)))
    }

    // Linear interpolation
    fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)

    // Generate pseudo random number from two integers and get result between -1 and 1
    fun getFloatHash(x: Int, y: Int): Float = getHash2D(x, y) / 2147483647.0f

    // Generate pseudo random number from two integers
    fun getHash2D(x: Int, y: Int): Int = getHash1D(getHash1D(x) + y)

    // Generate pseudo random number from single integer
    fun getHash1D(a: Int): Int
    {
        var a = a
        a = a.inv() + (a shl 15)
        a = a xor a.ushr(12)
        a += (a shl 2)
        a = a xor a.ushr(4)
        a *= 2057
        a = a xor a.ushr(16)
        return a
    }

    // Get decimal part from floating number
    fun fract(v: Float): Float = v - floor(v)

    // Make linear line smooth - smooth start and stop
    fun smoothstep(t: Float): Float = (3 - 2 * t) * t * t
}