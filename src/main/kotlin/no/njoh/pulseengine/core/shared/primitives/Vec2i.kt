package utils.Vec2i

typealias Vec2i      = Long
typealias Vec2iArray = LongArray

const val   VEC2I_MASK_LO     = 0xFFFF_FFFFL
const val   VEC2I_MASK_HI     = VEC2I_MASK_LO shl 32
private val VEC2I_EMPTY_ARRAY = LongArray(0)

// Constructors
inline fun Vec2i(x: Int, y: Int): Vec2i = (x.toLong() and VEC2I_MASK_LO) or (y.toLong() shl 32)
inline fun Vec2i(v: Int): Vec2i = Vec2i(v, v)

// Setters
inline fun Vec2i.x(v: Int): Vec2i = (this and VEC2I_MASK_HI) or (v.toLong() and VEC2I_MASK_LO)
inline fun Vec2i.y(v: Int): Vec2i = (this and VEC2I_MASK_LO) or (v.toLong() shl 32)

// Getters
inline val Vec2i.x get(): Int = (this and VEC2I_MASK_LO).toInt()
inline val Vec2i.y get(): Int = (this ushr 32).toInt()
inline operator fun Vec2i.component1(): Int = x
inline operator fun Vec2i.component2(): Int = y

// Arithmetic operations
inline fun Vec2i.vAdd(v: Vec2i) = Vec2i(x + v.x, y + v.y)
inline fun Vec2i.vAdd(v: Int)   = Vec2i(x + v, y + v)
inline fun Vec2i.vSub(v: Vec2i) = Vec2i(x - v.x, y - v.y)
inline fun Vec2i.vSub(v: Int)   = Vec2i(x - v, y - v)
inline fun Vec2i.vMul(v: Vec2i) = Vec2i(x * v.x, y * v.y)
inline fun Vec2i.vMul(v: Int)   = Vec2i(x * v, y * v)
inline fun Vec2i.vDiv(v: Vec2i) = Vec2i(x / v.x, y / v.y)
inline fun Vec2i.vDiv(v: Int)   = Vec2i(x / v, y / v)

// Array constructors
fun emptyVec2iArray(): Vec2iArray = VEC2I_EMPTY_ARRAY
fun vec2iArrayOf(vararg values: Vec2i): Vec2iArray =
    if (values.isEmpty()) VEC2I_EMPTY_ARRAY else LongArray(values.size) { values[it] }