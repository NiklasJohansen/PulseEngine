package no.njoh.pulseengine.core.shared.primitives

import kotlin.math.max

/**
 * Dense Int-to-Int lookup that is cleared by advancing a generation instead of
 * filling its backing arrays. Keys must be non-negative.
 */
class GenerationalIntLookup(
    initialCapacity: Int,
    private val noValue: Int
) {
    private var values = IntArray(max(1, initialCapacity))
    private var generations = IntArray(values.size)
    private var generation = 1

    operator fun get(key: Int): Int = 
        if (key >= 0 && key < values.size && generations[key] == generation) values[key] else noValue

    operator fun set(key: Int, value: Int)
    {
        ensureCapacity(key + 1)
        values[key] = value
        generations[key] = generation
    }

    fun clear()
    {
        if (generation == Int.MAX_VALUE)
        {
            generations.fill(0)
            generation = 1
        }
        else generation++
    }

    private fun ensureCapacity(requiredCapacity: Int)
    {
        if (requiredCapacity <= values.size)
            return

        val newCapacity = max(requiredCapacity, values.size * 2)
        values = values.copyOf(newCapacity)
        generations = generations.copyOf(newCapacity)
    }
}