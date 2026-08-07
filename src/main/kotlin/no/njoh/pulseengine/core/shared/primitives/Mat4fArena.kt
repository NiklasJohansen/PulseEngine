package no.njoh.pulseengine.core.shared.primitives

import no.njoh.pulseengine.core.shared.primitives.Mat4f.Companion.MATRIX_SIZE
import kotlin.math.max

@JvmInline
value class Mat4fArena(val id: Int)
{
    fun alloc(properties: Mat4fProps): Mat4f
    {
        val arena = ARENAS[id]
        val offset = OFFSETS[id]
        val nextOffset = offset + MATRIX_SIZE
        if (nextOffset > arena.size)
            ARENAS[id] = arena.copyOf(max(nextOffset, arena.size * 2))
        OFFSETS[id] = nextOffset
        return Mat4f(id, offset, properties)
    }

    fun reset() 
    { 
        OFFSETS[id] = 0
    }

    companion object
    {
        @PublishedApi @JvmField internal var ARENAS: Array<FloatArray> = emptyArray()
        @PublishedApi @JvmField internal var OFFSETS = IntArray(0)

        operator fun invoke(): Mat4fArena
        {
            val size = ARENAS.size
            val oldArenas = ARENAS
            ARENAS = Array(size + 1) { if (it < size) oldArenas[it] else FloatArray(MATRIX_SIZE * 1024) }
            OFFSETS = OFFSETS.copyOf(size + 1)
            return Mat4fArena(id = size)
        }
    }
}