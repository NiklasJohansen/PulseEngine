package no.njoh.pulseengine.core.graphics.gpu.resource

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.MASK
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.gpu.buffer.DoubleBufferedFloatObject
import no.njoh.pulseengine.core.shared.primitives.Color
import java.util.ArrayDeque

class MaterialBank
{
    private var buffer: DoubleBufferedFloatObject? = null
    private val materials = ArrayList<Material?>(128).apply { add(null) }
    private val freeIds = ArrayDeque<Int>(128)
    private var dirty = false

    fun upload(material: Material)
    {
        ensureBuffer()

        if (materials.getOrNull(material.id) === material)
        {
            material.onUploaded(material.id)
            dirty = true
            return
        }

        val newId = if (freeIds.isNotEmpty()) freeIds.removeFirst() else materials.size.also { materials += null }

        dirty = true
        materials[newId] = material
        material.onUploaded(newId)
    }

    fun delete(material: Material)
    {
        if (materials.getOrNull(material.id) === material)
        {
            dirty = true
            materials[material.id] = null
            freeIds.add(material.id)
        }
        material.onDeleted()
    }

    fun submitAndBind()
    {
        val buffer = buffer ?: return

        if (dirty || hasDirtyMaterials())
        {
            for (material in materials)
                buffer.writeMaterial(material)

            buffer.swapBuffers()
            buffer.bind()
            buffer.submit()
            buffer.release()

            dirty = false
        }
        else
        {
            // Rebind the SSBO binding point even when no upload is needed.
            // release() only clears the generic buffer target, not glBindBufferBase binding.
            buffer.bind()
            buffer.release()
        }
    }

    fun destroy()
    {
        buffer?.destroy()
        buffer = null
        materials.forEach { it?.onDeleted() }
        materials.clear()
        materials += null // Default material
        freeIds.clear()
        dirty = false
    }

    private fun ensureBuffer()
    {
        if (buffer != null) return

        dirty = true
        buffer = DoubleBufferedFloatObject.createShaderStorageBuffer(
            blockBinding = BUFFER_BINDING,
            initCapacity = MATERIAL_FLOATS * 128
        )
    }

    private fun hasDirtyMaterials(): Boolean
    {
        var hasDirtyMaterials = false
        for (material in materials)
        {
            if (material?.isDirty == true)
            {
                material.isDirty = false
                hasDirtyMaterials = true
            }
        }
        return hasDirtyMaterials
    }

    private fun DoubleBufferedFloatObject.writeMaterial(material: Material?)
    {
        val baseColor = material?.baseColor ?: DEFAULT_MATERIAL_COLOR
        val emissiveFactor = material?.emissiveFactor ?: Color.WHITE
        val alphaCutoff = if (material?.blendMode == MASK) material.alphaCutoff else 0f

        // TODO: Temporary fix for sponza normals
        val flags = if (material?.name?.contains("sponza") == true) MATERIAL_FLAG_FLIP_NORMALS else 0

        fill(MATERIAL_FLOATS)
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
    }

    private fun DoubleBufferedFloatObject.putTexture(texture: Texture?)
    {
        if (texture != null)
            put(texture.handle.samplerIndex.toFloat(), texture.handle.textureIndex.toFloat(), texture.uMax, texture.vMax)
        else
            put(-1f, 0f, 0f, 0f)
    }

    companion object
    {
        const val BUFFER_BINDING = 2

        private val DEFAULT_MATERIAL_COLOR = Color(1f, 0f, 1f, 1f)

        private const val MATERIAL_FLOATS = 32
        private const val MATERIAL_FLAG_FLIP_NORMALS = 1
    }
}
