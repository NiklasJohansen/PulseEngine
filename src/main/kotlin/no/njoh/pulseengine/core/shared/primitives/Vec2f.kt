package utils.Vec2f

typealias Vec2f      = Long
typealias Vec2fArray = LongArray

const val   VEC2F_MASK_LO     = 0xFFFF_FFFFL
const val   VEC2F_MASK_HI     = VEC2F_MASK_LO shl 32
private val VEC2F_EMPTY_ARRAY = LongArray(0)

inline fun Vec2f(x: Float, y: Float) = (x.toRawBits().toLong() and VEC2F_MASK_LO) or (y.toRawBits().toLong() shl 32)

inline fun Vec2f.x(x: Float): Vec2f = (this and VEC2F_MASK_HI) or (x.toRawBits().toLong() and VEC2F_MASK_LO)
inline fun Vec2f.y(y: Float): Vec2f = (this and VEC2F_MASK_LO) or (y.toRawBits().toLong() shl 32)

inline val Vec2f.x get(): Float = Float.fromBits((this and VEC2F_MASK_LO).toInt())
inline val Vec2f.y get(): Float = Float.fromBits((this ushr 32).toInt())

inline operator fun Vec2f.component1(): Float = x
inline operator fun Vec2f.component2(): Float = y

fun emptyVec2fArray(): Vec2fArray = VEC2F_EMPTY_ARRAY

fun vec2iArrayOf(vararg values: Vec2f): Vec2fArray =
    if (values.isEmpty()) VEC2F_EMPTY_ARRAY else LongArray(values.size) { values[it] }