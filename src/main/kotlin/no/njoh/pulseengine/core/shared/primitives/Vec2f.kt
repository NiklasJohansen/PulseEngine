package utils.Vec2f

typealias Vec2f      = Long
typealias Vec2fArray = LongArray

const val   VEC2F_MASK_LO     = 0xFFFF_FFFFL
const val   VEC2F_MASK_HI     = VEC2F_MASK_LO shl 32
private val VEC2F_EMPTY_ARRAY = LongArray(0)

// Constructors
inline fun Vec2f(x: Float, y: Float): Vec2f = (x.toRawBits().toLong() and VEC2F_MASK_LO) or (y.toRawBits().toLong() shl 32)
inline fun Vec2f(v: Float): Vec2f = Vec2f(v, v)

// Setters
inline fun Vec2f.x(x: Float): Vec2f = (this and VEC2F_MASK_HI) or (x.toRawBits().toLong() and VEC2F_MASK_LO)
inline fun Vec2f.y(y: Float): Vec2f = (this and VEC2F_MASK_LO) or (y.toRawBits().toLong() shl 32)

// Getters
inline val Vec2f.x get(): Float = Float.fromBits((this and VEC2F_MASK_LO).toInt())
inline val Vec2f.y get(): Float = Float.fromBits((this ushr 32).toInt())
inline operator fun Vec2f.component1(): Float = x
inline operator fun Vec2f.component2(): Float = y

// Arithmetic operations
inline fun Vec2f.vAdd(v: Vec2f): Vec2f = Vec2f(x + v.x, y + v.y)
inline fun Vec2f.vAdd(v: Float): Vec2f = Vec2f(x + v, y + v)
inline fun Vec2f.vSub(v: Vec2f): Vec2f = Vec2f(x - v.x, y - v.y)
inline fun Vec2f.vSub(v: Float): Vec2f = Vec2f(x - v, y - v)
inline fun Vec2f.vMul(v: Vec2f): Vec2f = Vec2f(x * v.x, y * v.y)
inline fun Vec2f.vMul(v: Float): Vec2f = Vec2f(x * v, y * v)
inline fun Vec2f.vDiv(v: Vec2f): Vec2f = Vec2f(x / v.x, y / v.y)
inline fun Vec2f.vDiv(v: Float): Vec2f = Vec2f(x / v, y / v)

// Array constructors
fun emptyVec2fArray(): Vec2fArray = VEC2F_EMPTY_ARRAY
fun vec2fArrayOf(vararg values: Vec2f): Vec2fArray =
    if (values.isEmpty()) VEC2F_EMPTY_ARRAY else LongArray(values.size) { values[it] }