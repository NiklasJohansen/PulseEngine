package no.njoh.pulseengine.core.graphics.gpu.resource

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.MASK
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.BLEND
import no.njoh.pulseengine.core.asset.types.Material.NormalOrientation.*
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.srgbToLinear
import java.util.BitSet
import org.lwjgl.system.MemoryUtil.memAllocFloat
import org.lwjgl.system.MemoryUtil.memFree

class MaterialBufferData(initialCapacity: Int = 128)
{
    var data = memAllocFloat(initialCapacity * MATERIAL_FLOATS); private set
    val dirtySlots = BitSet()

    private var sourceRgb = FloatArray(initialCapacity * 6) { Float.NaN }

    fun writeMaterial(id: Int, material: Material?)
    {
        val baseColor      = material?.baseColor ?: DEFAULT_MATERIAL_COLOR
        val emissiveFactor = material?.emissiveFactor ?: Color.WHITE
        val heightScale    = material?.heightScale?.coerceAtLeast(0f) ?: 0f
        val alphaCutoff    = if (material?.blendMode == MASK) material.alphaCutoff else 0f
        val uNormalDir     = if (material?.normalOrientation == INVERT_U || material?.normalOrientation == INVERT_UV) -1f else 1f
        val vNormalDir     = if (material?.normalOrientation == INVERT_V || material?.normalOrientation == INVERT_UV) -1f else 1f

        ensureCapacity(id + 1)
        data.position(id * MATERIAL_FLOATS)
        dirtySlots.set(id)

        putTexture(material?.albedoMap)
        putTexture(material?.normalMap)
        putTexture(material?.aoRoughMetalMap)
        putTexture(material?.emissiveMap)
        putTexture(material?.heightMap)
        putColor(id * 6, baseColor)
        putColor(id * 6 + 3, emissiveFactor)
        put(
            material?.occlusionStrength ?: 1f,
            material?.roughnessFactor ?: 1f,
            material?.metallicFactor ?: 0f,
            heightScale
        )
        put(uNormalDir, vNormalDir, material?.normalScale ?: 1f, alphaCutoff)
        put(
            material?.xTiling ?: 1f, 
            material?.yTiling ?: 1f, 
            Float.fromBits(material?.layerMask ?: 1), 
            if (material?.blendMode == BLEND) 1f else 0f
        )
    }

    fun shouldUseBulkUpload(slotCount: Int): Boolean
    {
        if (dirtySlots.cardinality() * 2 >= slotCount) 
            return true // At least half the records are dirty, reupload the whole buffer in one call
        
        var ranges = 0
        var uploadedSlots = 0
        var start = dirtySlots.nextSetBit(0)
        
        while (start >= 0)
        {
            if (++ranges > MAX_UPLOAD_RANGES) 
                return true // Too many ranges for separate uploads, reupload the whole buffer in one call
 
            val end = nextUploadRangeEnd(start)
            uploadedSlots += end - start
            if (uploadedSlots * 2 >= slotCount)
                return true // At least half the records are dirty, reupload the whole buffer in one call

            start = dirtySlots.nextSetBit(end)
        }

        return false
    }

    fun nextUploadRangeEnd(start: Int): Int
    {
        var end = dirtySlots.nextClearBit(start)
        while (true)
        {
            val next = dirtySlots.nextSetBit(end)
            if (next < 0 || next - end > MAX_UPLOAD_GAP)
                return end // The next dirty slot is too far away, stop the range here

            // Bridge a small clean gap to save an upload call
            end = dirtySlots.nextClearBit(next)
        }
    }

    fun destroy() = memFree(data)

    private fun ensureCapacity(count: Int)
    {
        if (count * MATERIAL_FLOATS <= data.capacity()) 
            return
        
        val capacity = maxOf(count, data.capacity() / MATERIAL_FLOATS * 2)
        val previous = data

        data = memAllocFloat(capacity * MATERIAL_FLOATS)
        previous.clear() // Copy the entire capacity. clear() resets bounds without erasing data.
        data.put(previous)
        memFree(previous)

        val oldSize = sourceRgb.size
        sourceRgb = sourceRgb.copyOf(capacity * 6)
        sourceRgb.fill(Float.NaN, oldSize)
    }

    private fun putColor(rgbOffset: Int, color: Color)
    {
        putColorChannel(rgbOffset + 0, color.red)
        putColorChannel(rgbOffset + 1, color.green)
        putColorChannel(rgbOffset + 2, color.blue)
        data.put(color.alpha)
    }

    private fun putColorChannel(index: Int, value: Float)
    {
        val position = data.position()
        if (sourceRgb[index] != value) 
        {
            // Only convert to linear space if the value has changed since the last upload
            sourceRgb[index] = value
            data.put(position, value.srgbToLinear())
        }
        data.position(position + 1)
    }

    private fun put(a: Float, b: Float, c: Float, d: Float)
    {
        data.put(a).put(b).put(c).put(d)
    }

    private fun putTexture(texture: Texture?)
    {
        if (texture?.handle?.isArrayTexture == true)
            put(texture.handle.textureArraySlot.toFloat(), texture.handle.textureArrayLayer.toFloat(), texture.uMax, texture.vMax)
        else
            put(-1f, 0f, 0f, 0f)
    }

    companion object
    {
        const val MATERIAL_FLOATS           = 40
        private const val MAX_UPLOAD_RANGES = 16
        private const val MAX_UPLOAD_GAP    = 4 // At most 640 extra bytes per merged gap.
        private val DEFAULT_MATERIAL_COLOR  = Color(1f, 0f, 1f, 1f)
    }
}