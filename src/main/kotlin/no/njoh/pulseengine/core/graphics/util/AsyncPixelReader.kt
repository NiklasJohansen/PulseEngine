package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER
import org.lwjgl.opengl.GL30.GL_MAP_READ_BIT
import org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glCheckFramebufferStatus
import org.lwjgl.opengl.GL30.glDeleteFramebuffers
import org.lwjgl.opengl.GL30.glGenFramebuffers
import org.lwjgl.opengl.GL30.nglMapBufferRange
import org.lwjgl.opengl.GL32.*
import org.lwjgl.system.MemoryUtil.memGetFloat
import org.lwjgl.system.MemoryUtil.memGetInt

class AsyncPixelReader
{
    private val requestLock       = Any()
    private val pendingXPos       = IntArray(MAX_PENDING_READS)
    private val pendingYPos       = IntArray(MAX_PENDING_READS)
    private val pendingVersion    = LongArray(MAX_PENDING_READS)
    private val pendingResult     = arrayOfNulls<PixelReadResult>(MAX_PENDING_READS)
    private var pendingReadIndex  = 0
    private var pendingWriteIndex = 0

    @Volatile
    private var pendingCount = 0

    private var bufferIds   = null as IntArray?
    private var fences      = null as LongArray?
    private var batchCounts = null as IntArray?
    private var versions    = null as LongArray?
    private var results     = null as Array<PixelReadResult?>?

    private var pixelFormat       = 0
    private var pixelType         = 0
    private var componentCount    = 0
    private var readFloat        = false
    private var initializedFormat = null as TextureFormat?
    private var readFramebufferId = 0
    private var nextSlot          = 0

    fun readPixel(x: Int, y: Int, dstResult: PixelReadResult): PixelReadResult
    {
        synchronized(requestLock)
        {
            if (dstResult.isPending) return dstResult
            check(pendingCount < MAX_PENDING_READS) { "Too many pending surface pixel reads. Maximum is $MAX_PENDING_READS" }

            val version = dstResult.tryPrepare() ?: return dstResult // dstResult is already pending
            pendingXPos[pendingWriteIndex] = x
            pendingYPos[pendingWriteIndex] = y
            pendingVersion[pendingWriteIndex] = version
            pendingResult[pendingWriteIndex] = dstResult
            pendingWriteIndex = (pendingWriteIndex + 1) % MAX_PENDING_READS
            pendingCount++
        }
        return dstResult
    }

    fun hasPendingWork() = if (pendingCount > 0) true else hasInFlightReads()

    fun update(texture: RenderTexture?)
    {
        if (bufferIds != null)
        {
            pollGpuResults()
            if (texture != null && texture.format != initializedFormat)
            {
                if (hasInFlightReads()) return
                releaseGpuResources()
            }
        }

        if (bufferIds == null)
        {
            if (pendingCount == 0) return
            initialize(texture ?: error("Surface has no color texture to read"))
        }

        val activeTexture = texture ?: return

        if (pendingCount == 0) return
        bindTextureForRead(activeTexture)
        try
        {
            while (pendingCount > 0)
            {
                val slot = findFreeSlot()
                if (slot < 0) return
                submitBatch(slot, activeTexture)
            }
        }
        finally { glBindFramebuffer(GL_READ_FRAMEBUFFER, 0) }
    }

    fun destroy()
    {
        cancelPendingRequests()
        cancelInFlightRequests()
        releaseGpuResources()
    }
    
    private fun initialize(texture: RenderTexture)
    {
        componentCount = texture.format.componentCount
        pixelFormat = texture.format.pixelFormat
        pixelType = texture.format.readType
        readFloat = !texture.format.isIntegerFormat
        initializedFormat = texture.format

        val newBuffers = IntArray(PBO_SLOT_COUNT)
        val batchSizeBytes = MAX_READS_PER_BATCH * componentCount * Int.SIZE_BYTES
        for (i in newBuffers.indices)
        {
            newBuffers[i] = glGenBuffers()
            glBindBuffer(GL_PIXEL_PACK_BUFFER, newBuffers[i])
            glBufferData(GL_PIXEL_PACK_BUFFER, batchSizeBytes.toLong(), GL_STREAM_READ)
        }
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)

        bufferIds = newBuffers
        fences = LongArray(PBO_SLOT_COUNT)
        batchCounts = IntArray(PBO_SLOT_COUNT)
        versions = LongArray(PBO_SLOT_COUNT * MAX_READS_PER_BATCH)
        results = arrayOfNulls(PBO_SLOT_COUNT * MAX_READS_PER_BATCH)
    }

    private fun submitBatch(slot: Int, texture: RenderTexture)
    {
        val activeBuffers = bufferIds ?: return
        val activeFences = fences ?: return
        val activeBatchCounts = batchCounts ?: return
        val activeVersions = versions ?: return
        val activeResults = results ?: return
        val pixelSizeBytes = componentCount * Int.SIZE_BYTES
        val batchOffset = slot * MAX_READS_PER_BATCH
        var processedCount = 0
        var submittedCount = 0

        glBindBuffer(GL_PIXEL_PACK_BUFFER, activeBuffers[slot])

        while (processedCount < MAX_READS_PER_BATCH)
        {
            var x = 0
            var y = 0
            var version = 0L
            var result: PixelReadResult? = null
            synchronized(requestLock)
            {
                if (pendingCount > 0)
                {
                    x = pendingXPos[pendingReadIndex]
                    y = pendingYPos[pendingReadIndex]
                    version = pendingVersion[pendingReadIndex]
                    result = pendingResult[pendingReadIndex]
                    pendingResult[pendingReadIndex] = null
                    pendingReadIndex = (pendingReadIndex + 1) % MAX_PENDING_READS
                    pendingCount--
                }
            }

            val dstResult = result ?: break
            processedCount++
            if (x !in 0 until texture.width || y !in 0 until texture.height)
            {
                completeWithZeros(dstResult, version)
                continue
            }

            val resultIndex = batchOffset + submittedCount
            val bufferOffset = submittedCount.toLong() * pixelSizeBytes
            glReadPixels(x, texture.height - y - 1, 1, 1, pixelFormat, pixelType, bufferOffset)
            activeVersions[resultIndex] = version
            activeResults[resultIndex] = dstResult
            submittedCount++
        }

        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
        if (submittedCount == 0) return

        activeBatchCounts[slot] = submittedCount
        activeFences[slot] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
        nextSlot = (slot + 1) % activeBuffers.size
    }

    private fun pollGpuResults()
    {
        val activeBuffers = bufferIds ?: return
        val activeFences = fences ?: return
        val activeBatchCounts = batchCounts ?: return
        val activeVersions = versions ?: return
        val activeResults = results ?: return
        val pixelSizeBytes = componentCount * Int.SIZE_BYTES

        for (slot in activeFences.indices)
        {
            val fence = activeFences[slot]
            if (fence == 0L) continue

            val status = glClientWaitSync(fence, 0, 0L)
            if (status != GL_ALREADY_SIGNALED && status != GL_CONDITION_SATISFIED) continue

            val count = activeBatchCounts[slot]
            val batchOffset = slot * MAX_READS_PER_BATCH
            glBindBuffer(GL_PIXEL_PACK_BUFFER, activeBuffers[slot])
            val address = nglMapBufferRange(GL_PIXEL_PACK_BUFFER, 0L, (count * pixelSizeBytes).toLong(), GL_MAP_READ_BIT)

            for (readIndex in 0 until count)
            {
                val resultIndex = batchOffset + readIndex
                val pixelAddress = address + readIndex.toLong() * pixelSizeBytes
                if (address != 0L)
                {
                    val result = activeResults[resultIndex]
                    val version = activeVersions[resultIndex]
                    if (readFloat)
                    {
                        val red = memGetFloat(pixelAddress)
                        val green = if (componentCount > 1) memGetFloat(pixelAddress + Float.SIZE_BYTES) else 0f
                        val blue  = if (componentCount > 2) memGetFloat(pixelAddress + 2L * Float.SIZE_BYTES) else 0f
                        val alpha = if (componentCount > 3) memGetFloat(pixelAddress + 3L * Float.SIZE_BYTES) else 0f
                        result?.completeFloat(version, red, green, blue, alpha)
                    }
                    else
                    {
                        val red = memGetInt(pixelAddress)
                        val green = if (componentCount > 1) memGetInt(pixelAddress + Int.SIZE_BYTES) else 0
                        val blue  = if (componentCount > 2) memGetInt(pixelAddress + 2L * Int.SIZE_BYTES) else 0
                        val alpha = if (componentCount > 3) memGetInt(pixelAddress + 3L * Int.SIZE_BYTES) else 0
                        result?.completeInt(version, red, green, blue, alpha)
                    }
                }
                else
                {
                    activeResults[resultIndex]?.let { completeWithZeros(it, activeVersions[resultIndex]) }
                }
                activeResults[resultIndex] = null
            }

            if (address != 0L) glUnmapBuffer(GL_PIXEL_PACK_BUFFER)
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)

            activeBatchCounts[slot] = 0
            glDeleteSync(fence)
            activeFences[slot] = 0L
        }
    }

    private fun findFreeSlot(): Int
    {
        val activeFences = fences ?: return -1
        for (offset in activeFences.indices)
        {
            val slot = (nextSlot + offset) % activeFences.size
            if (activeFences[slot] == 0L) return slot
        }
        return -1
    }

    private fun completeWithZeros(result: PixelReadResult, version: Long)
    {
        if (readFloat) result.completeFloat(version, 0f, 0f, 0f, 0f) else result.completeInt(version, 0, 0, 0, 0)
    }

    private fun bindTextureForRead(texture: RenderTexture)
    {
        require(texture.attachmentPoint.isColor) { "Surface pixel readback requires a color texture" }
        if (readFramebufferId == 0) readFramebufferId = glGenFramebuffers()
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebufferId)
        glFramebufferTexture(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, texture.handle.glId, 0)
        glReadBuffer(GL_COLOR_ATTACHMENT0)
        check(glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) { "Failed to attach surface texture for pixel readback" }
    }

    private fun hasInFlightReads() = fences?.any { it != 0L } == true

    private fun cancelPendingRequests()
    {
        synchronized(requestLock)
        {
            var index = pendingReadIndex
            var remaining = pendingCount
            while (remaining > 0)
            {
                pendingResult[index]?.cancel(pendingVersion[index])
                pendingResult[index] = null
                index = (index + 1) % MAX_PENDING_READS
                remaining--
            }
            pendingReadIndex = 0
            pendingWriteIndex = 0
            pendingCount = 0
        }
    }

    private fun cancelInFlightRequests()
    {
        val activeResults = results ?: return
        val activeVersions = versions ?: return
        for (i in activeResults.indices)
        {
            activeResults[i]?.cancel(activeVersions[i])
            activeResults[i] = null
        }
    }
    
    private fun releaseGpuResources()
    {
        val activeBuffers = bufferIds
        val activeFences = fences
        if (activeBuffers != null && activeFences != null)
        {
            for (i in activeBuffers.indices)
            {
                if (activeFences[i] != 0L) glDeleteSync(activeFences[i])
                if (activeBuffers[i] != 0) glDeleteBuffers(activeBuffers[i])
            }
        }
        if (readFramebufferId != 0) glDeleteFramebuffers(readFramebufferId)
        bufferIds = null
        fences = null
        batchCounts = null
        versions = null
        results = null
        initializedFormat = null
        readFramebufferId = 0
        nextSlot = 0
    }

    private companion object
    {
        const val PBO_SLOT_COUNT = 3
        const val MAX_READS_PER_BATCH = 256
        const val MAX_PENDING_READS = 1024
    }
}