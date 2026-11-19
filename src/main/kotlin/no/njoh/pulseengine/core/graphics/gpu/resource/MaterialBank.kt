package no.njoh.pulseengine.core.graphics.gpu.resource

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import no.njoh.pulseengine.core.graphics.gpu.buffer.ShaderStorageBufferObject
import no.njoh.pulseengine.core.graphics.gpu.resource.MaterialBufferData.Companion.MATERIAL_FLOATS
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.incrementUploadedBytes
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL30.glBindBufferBase
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER
import java.util.ArrayDeque
import java.util.BitSet

class MaterialBank : ShaderStorageBufferObject
{
    private var bufferId       = 0
    private var bufferCapacity = 0
    private var buffer         = null as MaterialBufferData?
    private val materials      = ArrayList<Material?>(128).apply { add(null) }
    private val freeIds        = ArrayDeque<Int>(128)
    private val dirtySlots     = BitSet()
    private var dirty          = false

    fun upload(material: Material)
    {
        ensureBuffer()

        if (materials.getOrNull(material.id) === material)
        {
            dirtySlots.set(material.id)
            material.onUploaded(material.id)
            return
        }
 
        val newId = if (freeIds.isNotEmpty()) freeIds.removeFirst() else materials.size.also { materials += null }
        materials[newId] = material
        dirtySlots.set(newId)
        material.onUploaded(newId)
    }

    fun delete(material: Material)
    {
        if (materials.getOrNull(material.id) === material)
        {
            dirtySlots.set(material.id)
            materials[material.id] = null
            freeIds.add(material.id)
        }
        material.onDeleted()
    }

    fun submit()
    {
        val buffer = buffer ?: return

        for (id in 0 until materials.size)
        {
            val material = materials[id]
            if (dirty || dirtySlots[id] || material?.isDirty == true)
            {
                buffer.writeMaterial(id, material)
                material?.isDirty = false
            }
        }
        
        if (buffer.dirtySlots.isEmpty) 
            return

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferId)
        
        val wasResized = bufferCapacity != buffer.data.capacity()
        if (wasResized)
        {
            bufferCapacity = buffer.data.capacity()
            glBufferData(GL_SHADER_STORAGE_BUFFER, bufferCapacity.toLong() * Float.SIZE_BYTES, GL_DYNAMIC_DRAW)
        }

        if (wasResized || buffer.shouldUseBulkUpload(materials.size))
        {
            uploadRange(buffer, 0, materials.size)
        }
        else
        {
            var start = buffer.dirtySlots.nextSetBit(0)
            while (start >= 0)
            {
                val end = buffer.nextUploadRangeEnd(start)
                uploadRange(buffer, start, end)
                start = buffer.dirtySlots.nextSetBit(end)
            }
        }

        buffer.data.clear()
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0)

        buffer.dirtySlots.clear()
        dirtySlots.clear()
        dirty = false
    }

    override fun bindStorageBuffer(binding: Int)
    {
        if (bufferId != 0) glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, bufferId)
    }

    fun destroy()
    {
        if (bufferId != 0) 
            glDeleteBuffers(bufferId)

        bufferId = 0
        bufferCapacity = 0
        buffer?.destroy()
        buffer = null
        materials.forEachFast { it?.onDeleted() }
        materials.clear()
        materials += null // Default material
        freeIds.clear()
        dirtySlots.clear()
        dirty = false
    }

    fun markDirty()
    {
        dirty = true
    }

    private fun uploadRange(buffer: MaterialBufferData, start: Int, end: Int)
    {
        val firstFloat = start * MATERIAL_FLOATS
        val endFloat   = end * MATERIAL_FLOATS

        buffer.data.limit(endFloat).position(firstFloat)
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, firstFloat.toLong() * Float.SIZE_BYTES, buffer.data)

        incrementUploadedBytes((endFloat - firstFloat).toLong() * Float.SIZE_BYTES)
    }
    
    private fun ensureBuffer()
    {
        if (bufferId != 0) return
        GlCapabilities.requireFullGraphics("Shader storage buffers")
        buffer = MaterialBufferData()
        bufferId = glGenBuffers()
        dirty = true
    }
}
