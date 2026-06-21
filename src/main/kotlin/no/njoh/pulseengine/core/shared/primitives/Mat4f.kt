package no.njoh.pulseengine.core.shared.primitives

import no.njoh.pulseengine.core.shared.primitives.Mat4fArena.Companion.ARENAS
import org.joml.Matrix4f
import org.joml.Vector3f

@JvmInline
value class Mat4f internal constructor(@PublishedApi internal val handle: Long)
{
    constructor(arena: Mat4fArena) : this(arena.alloc().handle)
    
    constructor(arenaId: Int, offset: Int) : this((arenaId.toLong() shl 32) or (offset.toLong() and OFFSET_MASK))

    val m00 inline get() = get(0)
    val m01 inline get() = get(1)
    val m02 inline get() = get(2)
    val m03 inline get() = get(3)
    val m10 inline get() = get(4)
    val m11 inline get() = get(5)
    val m12 inline get() = get(6)
    val m13 inline get() = get(7)
    val m20 inline get() = get(8)
    val m21 inline get() = get(9)
    val m22 inline get() = get(10)
    val m23 inline get() = get(11)
    val m30 inline get() = get(12)
    val m31 inline get() = get(13)
    val m32 inline get() = get(14)
    val m33 inline get() = get(15)

    val arenaId inline get() = (handle ushr 32).toInt()
    val offset  inline get() = (handle and OFFSET_MASK).toInt()
    val data    inline get() = ARENAS[arenaId]

    inline fun get(index: Int) = data[offset + index]

    fun get(dst: FloatArray, dstOffset: Int)
    {
        System.arraycopy(data, offset, dst, dstOffset, MATRIX_SIZE)
    }

    fun getTranslation(dst: Vector3f): Vector3f
    {
        val srcArena = data
        val srcOffset = offset
        return dst.set(srcArena[srcOffset + 12], srcArena[srcOffset + 13], srcArena[srcOffset + 14])
    }

    fun set(src: Matrix4f): Mat4f
    {
        src.get(data, offset)
        return this
    }

    fun setMul(left: Matrix4f, right: Matrix4f): Mat4f
    {
        val data = data
        val offset = offset

        val lm00 = left.m00(); val lm01 = left.m01(); val lm02 = left.m02(); val lm03 = left.m03()
        val lm10 = left.m10(); val lm11 = left.m11(); val lm12 = left.m12(); val lm13 = left.m13()
        val lm20 = left.m20(); val lm21 = left.m21(); val lm22 = left.m22(); val lm23 = left.m23()
        val lm30 = left.m30(); val lm31 = left.m31(); val lm32 = left.m32(); val lm33 = left.m33()

        val rm00 = right.m00(); val rm01 = right.m01(); val rm02 = right.m02(); val rm03 = right.m03()
        val rm10 = right.m10(); val rm11 = right.m11(); val rm12 = right.m12(); val rm13 = right.m13()
        val rm20 = right.m20(); val rm21 = right.m21(); val rm22 = right.m22(); val rm23 = right.m23()
        val rm30 = right.m30(); val rm31 = right.m31(); val rm32 = right.m32(); val rm33 = right.m33()

        if (lm03 == 0f && lm13 == 0f && lm23 == 0f && lm33 == 1f && rm03 == 0f && rm13 == 0f && rm23 == 0f && rm33 == 1f)
        {
            data[offset     ] = lm00 * rm00 + lm10 * rm01 + lm20 * rm02
            data[offset +  1] = lm01 * rm00 + lm11 * rm01 + lm21 * rm02
            data[offset +  2] = lm02 * rm00 + lm12 * rm01 + lm22 * rm02
            data[offset +  3] = 0f

            data[offset +  4] = lm00 * rm10 + lm10 * rm11 + lm20 * rm12
            data[offset +  5] = lm01 * rm10 + lm11 * rm11 + lm21 * rm12
            data[offset +  6] = lm02 * rm10 + lm12 * rm11 + lm22 * rm12
            data[offset +  7] = 0f

            data[offset +  8] = lm00 * rm20 + lm10 * rm21 + lm20 * rm22
            data[offset +  9] = lm01 * rm20 + lm11 * rm21 + lm21 * rm22
            data[offset + 10] = lm02 * rm20 + lm12 * rm21 + lm22 * rm22
            data[offset + 11] = 0f

            data[offset + 12] = lm00 * rm30 + lm10 * rm31 + lm20 * rm32 + lm30
            data[offset + 13] = lm01 * rm30 + lm11 * rm31 + lm21 * rm32 + lm31
            data[offset + 14] = lm02 * rm30 + lm12 * rm31 + lm22 * rm32 + lm32
            data[offset + 15] = 1f

            return this
        }

        data[offset     ] = lm00 * rm00 + lm10 * rm01 + lm20 * rm02 + lm30 * rm03
        data[offset +  1] = lm01 * rm00 + lm11 * rm01 + lm21 * rm02 + lm31 * rm03
        data[offset +  2] = lm02 * rm00 + lm12 * rm01 + lm22 * rm02 + lm32 * rm03
        data[offset +  3] = lm03 * rm00 + lm13 * rm01 + lm23 * rm02 + lm33 * rm03

        data[offset +  4] = lm00 * rm10 + lm10 * rm11 + lm20 * rm12 + lm30 * rm13
        data[offset +  5] = lm01 * rm10 + lm11 * rm11 + lm21 * rm12 + lm31 * rm13
        data[offset +  6] = lm02 * rm10 + lm12 * rm11 + lm22 * rm12 + lm32 * rm13
        data[offset +  7] = lm03 * rm10 + lm13 * rm11 + lm23 * rm12 + lm33 * rm13

        data[offset +  8] = lm00 * rm20 + lm10 * rm21 + lm20 * rm22 + lm30 * rm23
        data[offset +  9] = lm01 * rm20 + lm11 * rm21 + lm21 * rm22 + lm31 * rm23
        data[offset + 10] = lm02 * rm20 + lm12 * rm21 + lm22 * rm22 + lm32 * rm23
        data[offset + 11] = lm03 * rm20 + lm13 * rm21 + lm23 * rm22 + lm33 * rm23

        data[offset + 12] = lm00 * rm30 + lm10 * rm31 + lm20 * rm32 + lm30 * rm33
        data[offset + 13] = lm01 * rm30 + lm11 * rm31 + lm21 * rm32 + lm31 * rm33
        data[offset + 14] = lm02 * rm30 + lm12 * rm31 + lm22 * rm32 + lm32 * rm33
        data[offset + 15] = lm03 * rm30 + lm13 * rm31 + lm23 * rm32 + lm33 * rm33

        return this
    }

    fun mul(right: Matrix4f): Mat4f
    {
        val data = data
        val offset = offset
        
        val lm00 = data[offset     ]; val lm01 = data[offset +  1]; val lm02 = data[offset +  2]; val lm03 = data[offset +  3]
        val lm10 = data[offset +  4]; val lm11 = data[offset +  5]; val lm12 = data[offset +  6]; val lm13 = data[offset +  7]
        val lm20 = data[offset +  8]; val lm21 = data[offset +  9]; val lm22 = data[offset + 10]; val lm23 = data[offset + 11]
        val lm30 = data[offset + 12]; val lm31 = data[offset + 13]; val lm32 = data[offset + 14]; val lm33 = data[offset + 15]

        val rm00 = right.m00(); val rm01 = right.m01(); val rm02 = right.m02(); val rm03 = right.m03()
        val rm10 = right.m10(); val rm11 = right.m11(); val rm12 = right.m12(); val rm13 = right.m13()
        val rm20 = right.m20(); val rm21 = right.m21(); val rm22 = right.m22(); val rm23 = right.m23()
        val rm30 = right.m30(); val rm31 = right.m31(); val rm32 = right.m32(); val rm33 = right.m33()

        if (lm03 == 0f && lm13 == 0f && lm23 == 0f && lm33 == 1f && rm03 == 0f && rm13 == 0f && rm23 == 0f && rm33 == 1f)
        {
            data[offset     ] = lm00 * rm00 + lm10 * rm01 + lm20 * rm02
            data[offset +  1] = lm01 * rm00 + lm11 * rm01 + lm21 * rm02
            data[offset +  2] = lm02 * rm00 + lm12 * rm01 + lm22 * rm02
            data[offset +  3] = 0f

            data[offset +  4] = lm00 * rm10 + lm10 * rm11 + lm20 * rm12
            data[offset +  5] = lm01 * rm10 + lm11 * rm11 + lm21 * rm12
            data[offset +  6] = lm02 * rm10 + lm12 * rm11 + lm22 * rm12
            data[offset +  7] = 0f

            data[offset +  8] = lm00 * rm20 + lm10 * rm21 + lm20 * rm22
            data[offset +  9] = lm01 * rm20 + lm11 * rm21 + lm21 * rm22
            data[offset + 10] = lm02 * rm20 + lm12 * rm21 + lm22 * rm22
            data[offset + 11] = 0f

            data[offset + 12] = lm00 * rm30 + lm10 * rm31 + lm20 * rm32 + lm30
            data[offset + 13] = lm01 * rm30 + lm11 * rm31 + lm21 * rm32 + lm31
            data[offset + 14] = lm02 * rm30 + lm12 * rm31 + lm22 * rm32 + lm32
            data[offset + 15] = 1f

            return this
        }

        data[offset     ] = lm00 * rm00 + lm10 * rm01 + lm20 * rm02 + lm30 * rm03
        data[offset +  1] = lm01 * rm00 + lm11 * rm01 + lm21 * rm02 + lm31 * rm03
        data[offset +  2] = lm02 * rm00 + lm12 * rm01 + lm22 * rm02 + lm32 * rm03
        data[offset +  3] = lm03 * rm00 + lm13 * rm01 + lm23 * rm02 + lm33 * rm03

        data[offset +  4] = lm00 * rm10 + lm10 * rm11 + lm20 * rm12 + lm30 * rm13
        data[offset +  5] = lm01 * rm10 + lm11 * rm11 + lm21 * rm12 + lm31 * rm13
        data[offset +  6] = lm02 * rm10 + lm12 * rm11 + lm22 * rm12 + lm32 * rm13
        data[offset +  7] = lm03 * rm10 + lm13 * rm11 + lm23 * rm12 + lm33 * rm13

        data[offset +  8] = lm00 * rm20 + lm10 * rm21 + lm20 * rm22 + lm30 * rm23
        data[offset +  9] = lm01 * rm20 + lm11 * rm21 + lm21 * rm22 + lm31 * rm23
        data[offset + 10] = lm02 * rm20 + lm12 * rm21 + lm22 * rm22 + lm32 * rm23
        data[offset + 11] = lm03 * rm20 + lm13 * rm21 + lm23 * rm22 + lm33 * rm23

        data[offset + 12] = lm00 * rm30 + lm10 * rm31 + lm20 * rm32 + lm30 * rm33
        data[offset + 13] = lm01 * rm30 + lm11 * rm31 + lm21 * rm32 + lm31 * rm33
        data[offset + 14] = lm02 * rm30 + lm12 * rm31 + lm22 * rm32 + lm32 * rm33
        data[offset + 15] = lm03 * rm30 + lm13 * rm31 + lm23 * rm32 + lm33 * rm33

        return this
    }

    companion object
    {
        const val MATRIX_SIZE = 16
        const val OFFSET_MASK = 0xFFFF_FFFFL
    }
}