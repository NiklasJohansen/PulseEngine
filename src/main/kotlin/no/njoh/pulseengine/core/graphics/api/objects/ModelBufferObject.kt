package no.njoh.pulseengine.core.graphics.api.objects

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.DrawList.RenderItem
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.*
import no.njoh.pulseengine.core.graphics.util.getSupportedModelInstanceIndexMode
import no.njoh.pulseengine.core.shared.primitives.Color
import org.joml.Matrix4f

internal class ModelBufferObject
{
    var instanceCount = 0; private set
    var instanceIndexMode = UNIFORM_OFFSET; private set
    var instanceIndexBuffer = null as DoubleBufferedIntObject?; private set

    private lateinit var instanceBuffer: DoubleBufferedFloatObject
    private lateinit var materialBuffer: DoubleBufferedFloatObject
    private lateinit var boneBuffer: DoubleBufferedFloatObject

    private val matrixData         = FloatArray(16)
    private val materials          = ArrayList<Material?>(128)
    private val bonePalettes       = ArrayList<Array<Matrix4f>>(128)
    private var bonePaletteOffsets = IntArray(128)
    private var boneMatrixCount    = 0
    private var nullMaterialId     = -1

    fun init()
    {
        if (this::instanceBuffer.isInitialized)
            return

        instanceBuffer = DoubleBufferedFloatObject.createShaderStorageBuffer(
            blockBinding = INSTANCE_BUFFER_BINDING,
            initCapacity = 20 * 512
        )
        
        materialBuffer = DoubleBufferedFloatObject.createShaderStorageBuffer(
            blockBinding = MATERIAL_BUFFER_BINDING,
            initCapacity = 32 * 128
        )
        
        boneBuffer = DoubleBufferedFloatObject.createShaderStorageBuffer(
            blockBinding = BONE_BUFFER_BINDING,
            initCapacity = 16 * 512
        )

        instanceIndexMode = getSupportedModelInstanceIndexMode()
        if (instanceIndexMode == INSTANCE_ATTRIBUTE)
            instanceIndexBuffer = DoubleBufferedIntObject.createArrayBuffer(initCapacity = 512)
    }

    fun clear()
    {
        materials.clear()
        bonePalettes.clear()
        boneMatrixCount = 0
        nullMaterialId = -1
        instanceCount = 0
    }

    fun addItem(item: RenderItem): Int
    {
        val instanceIndex = instanceCount++
        val materialId = materialIdOf(item.material) // TODO: Upload material once and give it an id
        val boneOffset = addBones(item.boneMatrices)

        instanceBuffer.fill(20) // 16 + 4
        {
            putMatrix(item.transform)
            put(materialId.toFloat(), boneOffset.toFloat(), 0f, 0f)
        }

        instanceIndexBuffer?.fill(1)
        {
            put(instanceIndex)
        }

        return instanceIndex
    }

    fun submit()
    {
        submit(instanceBuffer)
        submit(materialBuffer)
        submit(boneBuffer)
        instanceIndexBuffer?.let { submit(it) }
    }

    fun destroy()
    {
        instanceBuffer.destroy()
        materialBuffer.destroy()
        boneBuffer.destroy()
        instanceIndexBuffer?.destroy()
    }

    private fun submit(buffer: DoubleBufferedFloatObject)
    {
        buffer.swapBuffers()
        buffer.bind()
        buffer.submit()
        buffer.release()
    }

    private fun submit(buffer: DoubleBufferedIntObject)
    {
        buffer.swapBuffers()
        buffer.bind()
        buffer.submit()
        buffer.release()
    }

    private fun materialIdOf(material: Material?): Int
    {
        if (material == null)
        {
            if (nullMaterialId == -1)
                nullMaterialId = addMaterial(null)
            return nullMaterialId
        }

        for (i in 0 until materials.size)
            if (material === materials[i]) return i

        return addMaterial(material)
    }

    private fun addMaterial(material: Material?): Int
    {
        val id = materials.size
        materials += material
        val baseColor = material?.baseColor ?: Color.WHITE
        val emissiveFactor = material?.emissiveFactor ?: Color.WHITE
        val alphaCutoff = if (material?.blendMode == Material.BlendMode.MASK) material.alphaCutoff else 0f
        val flags = if (material?.name?.contains("sponza") == true) MATERIAL_FLAG_FLIP_NORMALS else 0

        materialBuffer.fill(32)
        {
            put(baseColor.red, baseColor.green, baseColor.blue, baseColor.alpha)
            put(emissiveFactor.red, emissiveFactor.green, emissiveFactor.blue, emissiveFactor.alpha)
            putTexture(material?.albedo)
            putTexture(material?.normal)
            putTexture(material?.aoMetalRough)
            putTexture(material?.emissive)
            put(
                material?.occlusionStrength ?: 1f,
                material?.roughnessFactor ?: 1f,
                material?.metallicFactor ?: 1f,
                material?.normalScale ?: 1f
            )
            put(material?.xTiling ?: 1f, material?.yTiling ?: 1f, alphaCutoff, flags.toFloat())
        }

        return id
    }

    private fun addBones(boneMatrices: Array<Matrix4f>?): Int
    {
        if (boneMatrices.isNullOrEmpty())
            return -1

        for (i in 0 until bonePalettes.size)
        {
            if (boneMatrices === bonePalettes[i]) return bonePaletteOffsets[i]
        }

        if (bonePalettes.size >= bonePaletteOffsets.size)
            bonePaletteOffsets = bonePaletteOffsets.copyOf(bonePaletteOffsets.size * 2)

        val offset = boneMatrixCount
        bonePalettes += boneMatrices
        bonePaletteOffsets[bonePalettes.lastIndex] = offset
        boneMatrixCount += boneMatrices.size
        
        boneBuffer.fill(boneMatrices.size * 16)
        {
            for (matrix in boneMatrices) putMatrix(matrix)
        }

        return offset
    }

    private fun DoubleBufferedFloatObject.putTexture(texture: Texture?)
    {
        if (texture != null)
            put(texture.handle.samplerIndex.toFloat(), texture.handle.textureIndex.toFloat(), texture.uMax, texture.vMax)
        else
            put(-1f, 0f, 0f, 0f)
    }

    private fun DoubleBufferedFloatObject.putMatrix(matrix: Matrix4f)
    {
        // TODO: Might be faster
//        put(matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03())
//        put(matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13())
//        put(matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23())
//        put(matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33())

        matrix.get(matrixData)
        for (value in matrixData)
            put(value)
    }

    companion object
    {
        const val INSTANCE_BUFFER_BINDING = 1
        const val MATERIAL_BUFFER_BINDING = 2
        const val BONE_BUFFER_BINDING     = 3

        private const val MATERIAL_FLAG_FLIP_NORMALS = 1
    }
}
