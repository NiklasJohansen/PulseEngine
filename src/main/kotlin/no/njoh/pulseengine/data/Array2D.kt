package no.njoh.pulseengine.data

class Array2D <T> (
    val width: Int,
    val height: Int,
    val init: (x: Int, y: Int) -> T? = { _, _ -> null }
): Iterable<T> {
    private val data: Array<Any?> = Array(width * height) { i -> init.invoke(i % width, i / width) }
    private val iterator = Array2DIterator()

    @Suppress("UNCHECKED_CAST")
    operator fun get(x: Int, y: Int): T = data[y * width + x] as T
    operator fun set(x: Int, y: Int, value: T) { data[y * width + x] = value }
    override fun iterator(): Iterator<T> = iterator.apply { index = 0 }

    inner class Array2DIterator(var index: Int = 0) : Iterator<T>
    {
        override fun hasNext(): Boolean =
            index < width * height

        @Suppress("UNCHECKED_CAST")
        override fun next(): T =
            data[index++] as T
    }
}