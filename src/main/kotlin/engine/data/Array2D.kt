package engine.data

class Array2D <T> (
    val height: Int,
    val width: Int,
    val init: (y: Int, x: Int) -> T? = { _, _ -> null }
): Iterable<T> {

    private val data: Array<Any?> = Array(width * height) { i -> init.invoke(i / width, i % width) }
    private val iterator = Array2DIterator()

    @Suppress("UNCHECKED_CAST")
    operator fun get(y: Int, x: Int): T = data[y * width + x] as T

    operator fun set(y: Int, x: Int, value: T) { data[y * width + x] = value }

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