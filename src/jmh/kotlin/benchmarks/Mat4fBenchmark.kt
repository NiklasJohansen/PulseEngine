package benchmarks

import no.njoh.pulseengine.core.shared.primitives.Mat4f
import no.njoh.pulseengine.core.shared.primitives.Mat4f.Companion.MATRIX_SIZE
import no.njoh.pulseengine.core.shared.primitives.Mat4fArena
import org.joml.Matrix4f
import org.joml.Vector3f
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import kotlin.math.abs

fun main() = runBenchmark<Mat4fBenchmark>(accuracy = BenchmarkAccuracy.LOW)

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class Mat4fBenchmark
{
    @Param("256", "4096", "16384")
    private var matrixCount = 0

    private var buildArena = Mat4fArena(-1)
    private var fixtureArena = Mat4fArena(-1)
    private lateinit var worldMatrices: Array<Matrix4f>
    private lateinit var localMatrices: Array<Matrix4f>
    private lateinit var mat4fs: Array<Mat4f>
    private lateinit var prebuiltJomlMatrices: Array<Matrix4f>
    private lateinit var allocatedJomlMatrices: Array<Matrix4f?>
    private lateinit var uploadBuffer: FloatArray
    private val translation = Vector3f()

    @Setup(Level.Trial)
    fun setUp()
    {
        buildArena = Mat4fArena()
        fixtureArena = Mat4fArena()
        uploadBuffer = FloatArray(matrixCount * MATRIX_SIZE)
        worldMatrices = Array(matrixCount) { createMatrix(it, 0.25f) }
        localMatrices = Array(matrixCount) { createMatrix(it, 1.5f) }
        allocatedJomlMatrices = arrayOfNulls(matrixCount)

        mat4fs = Array(matrixCount) { i -> Mat4f(fixtureArena).setMul(worldMatrices[i], localMatrices[i]) }
        prebuiltJomlMatrices = Array(matrixCount) { i -> Matrix4f(worldMatrices[i]).mul(localMatrices[i]) }

        var i = 0
        while (i < matrixCount)
        {
            Mat4f(buildArena).setMul(worldMatrices[i], localMatrices[i])
            i++
        }
        buildArena.reset()
    }

    @Benchmark
    fun mat4fSet(bh: Blackhole)
    {
        buildArena.reset()

        val arena = buildArena
        val sources = worldMatrices
        val size = matrixCount
        var checksum = 0f
        var i = 0
        while (i < size)
        {
            val matrix = Mat4f(arena).set(sources[i])
            checksum += matrix.m30 + matrix.m31 + matrix.m32
            i++
        }
        bh.consume(checksum)
    }

    @Benchmark
    fun jomlMatrixSet(bh: Blackhole)
    {
        val sources = worldMatrices
        val matrices = allocatedJomlMatrices
        val size = matrixCount
        var checksum = 0f
        var i = 0
        while (i < size)
        {
            val matrix = Matrix4f(sources[i])
            matrices[i] = matrix
            checksum += matrix.m30() + matrix.m31() + matrix.m32()
            i++
        }
        bh.consume(checksum)
    }

    @Benchmark
    fun mat4fSetAndMul(bh: Blackhole)
    {
        buildArena.reset()

        val arena = buildArena
        val left = worldMatrices
        val right = localMatrices
        val size = matrixCount
        var checksum = 0f
        var i = 0
        while (i < size)
        {
            val matrix = Mat4f(arena).setMul(left[i], right[i])
            checksum += matrix.m00 + matrix.m11 + matrix.m22 + matrix.m30
            i++
        }
        bh.consume(checksum)
    }

    @Benchmark
    fun jomlMatrixSetAndMul(bh: Blackhole)
    {
        val left = worldMatrices
        val right = localMatrices
        val matrices = allocatedJomlMatrices
        val size = matrixCount
        var checksum = 0f
        var i = 0
        while (i < size)
        {
            val matrix = Matrix4f(left[i]).mul(right[i])
            matrices[i] = matrix
            checksum += matrix.m00() + matrix.m11() + matrix.m22() + matrix.m30()
            i++
        }
        bh.consume(checksum)
    }

    @Benchmark
    fun mat4fUpload(bh: Blackhole)
    {
        val matrices = mat4fs
        val buffer = uploadBuffer
        val size = matrixCount
        var dstOffset = 0
        var checksum = 0f
        var i = 0
        while (i < size)
        {
            matrices[i].get(buffer, dstOffset)
            checksum += buffer[dstOffset + 12]
            dstOffset += MATRIX_SIZE
            i++
        }
        bh.consume(checksum)
    }

    @Benchmark
    fun jomlMatrixUpload(bh: Blackhole)
    {
        val matrices = prebuiltJomlMatrices
        val buffer = uploadBuffer
        val size = matrixCount
        var dstOffset = 0
        var checksum = 0f
        var i = 0
        while (i < size)
        {
            matrices[i].get(buffer, dstOffset)
            checksum += buffer[dstOffset + 12]
            dstOffset += MATRIX_SIZE
            i++
        }
        bh.consume(checksum)
    }

    @Benchmark
    fun mat4fTranslationRead(bh: Blackhole)
    {
        val matrices = mat4fs
        val dst = translation
        val size = matrixCount
        var checksum = 0f
        var i = 0
        while (i < size)
        {
            matrices[i].getTranslation(dst)
            checksum += dst.x + dst.y + dst.z
            i++
        }
        bh.consume(checksum)
    }

    @Benchmark
    fun jomlMatrixTranslationRead(bh: Blackhole)
    {
        val matrices = prebuiltJomlMatrices
        val dst = translation
        val size = matrixCount
        var checksum = 0f
        var i = 0
        while (i < size)
        {
            matrices[i].getTranslation(dst)
            checksum += dst.x + dst.y + dst.z
            i++
        }
        bh.consume(checksum)
    }

    @Benchmark
    fun mat4fAabbRead(bh: Blackhole)
    {
        val matrices = mat4fs
        val size = matrixCount
        var checksum = 0f
        var i = 0
        while (i < size)
        {
            val matrix = matrices[i]
            val whx = abs(matrix.m00) + abs(matrix.m10) + abs(matrix.m20)
            val why = abs(matrix.m01) + abs(matrix.m11) + abs(matrix.m21)
            val whz = abs(matrix.m02) + abs(matrix.m12) + abs(matrix.m22)
            checksum += whx + why + whz + matrix.m30 + matrix.m31 + matrix.m32
            i++
        }
        bh.consume(checksum)
    }

    @Benchmark
    fun jomlMatrixAabbRead(bh: Blackhole)
    {
        val matrices = prebuiltJomlMatrices
        val size = matrixCount
        var checksum = 0f
        var i = 0
        while (i < size)
        {
            val matrix = matrices[i]
            val whx = abs(matrix.m00()) + abs(matrix.m10()) + abs(matrix.m20())
            val why = abs(matrix.m01()) + abs(matrix.m11()) + abs(matrix.m21())
            val whz = abs(matrix.m02()) + abs(matrix.m12()) + abs(matrix.m22())
            checksum += whx + why + whz + matrix.m30() + matrix.m31() + matrix.m32()
            i++
        }
        bh.consume(checksum)
    }

    private fun createMatrix(index: Int, phase: Float): Matrix4f
    {
        val base = index + phase
        val x = ((index * 13) % 97 - 48) * 0.125f
        val y = ((index * 17) % 53 - 26) * 0.25f
        val z = ((index * 19) % 31 - 15) * 0.5f
        val scale = 0.75f + ((index * 23) % 29) * 0.025f

        return Matrix4f()
            .translation(x, y, z)
            .rotateXYZ(base * 0.013f, base * 0.017f, base * 0.019f)
            .scale(scale)
    }
}
