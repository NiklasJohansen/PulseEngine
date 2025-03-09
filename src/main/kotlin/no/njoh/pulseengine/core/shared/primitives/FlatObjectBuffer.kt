package no.njoh.pulseengine.core.shared.primitives

import kotlin.math.max
import kotlin.reflect.KProperty

@Suppress("UNCHECKED_CAST")
abstract class FlatObjectBuffer<T>(@JvmField val stride: Int)
{
    @JvmField var pos = 0
    @JvmField var limit = 0

    fun next() { pos += stride }
    fun flip() { limit = pos; pos = 0 }
    fun size()  = limit / stride
    fun clear() = this.also { limit = 0; pos = 0 } as T
    fun first() = this.also { pos = 0 } as T
    fun last()  = this.also { pos = max(0, limit - stride) } as T

    operator fun get(index: Int) = this.also { pos = index * stride }

    inline fun put(action: T.() -> Unit)
    {
        action(this as T)
        next()
    }

    inline fun forEach(action: (T) -> Unit)
    {
        pos = 0
        while (pos < limit)
        {
            action(this as T)
            next()
        }
    }

    class FloatRef<T: FlatObjectBuffer<*>>(private val data: FloatArray, private val offset: Int)
    {
        operator fun getValue(thisRef: T, p: KProperty<*>) = data[thisRef.pos + offset]
        operator fun setValue(thisRef: T, p: KProperty<*>, value: Float) { data[thisRef.pos + offset] = value }
    }
}