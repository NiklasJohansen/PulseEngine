package no.njoh.pulseengine.core.graphics.gpu.texture

import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.LINEAR_MIPMAP
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.RGBA16F
import org.lwjgl.opengl.GL33.*
import org.lwjgl.opengl.GL40.GL_TEXTURE_CUBE_MAP_ARRAY
import org.lwjgl.opengl.GL42.glTexStorage3D
import org.lwjgl.opengl.GL43.glCopyImageSubData

/**
 * Growable cubemap storage. Capacity and indices count complete cubemaps, each containing six faces.
 */
class CubeMapArray(
    capacity: Int,
    val resolution: Int,
    val format: TextureFormat = RGBA16F,
    val filter: TextureFilter = LINEAR_MIPMAP,
    val mipLevels: Int = Integer.SIZE - Integer.numberOfLeadingZeros(resolution)
) {
    var handle   = TextureHandle.NONE; private set
    var capacity = capacity;           private set

    init
    {
        require(capacity > 0) { "Cubemap array capacity must be positive" }
        require(resolution > 0) { "Cubemap array resolution must be positive" }
        require(mipLevels in 1..Integer.SIZE - Integer.numberOfLeadingZeros(resolution)) { "Invalid cubemap array mip count: $mipLevels" }
    }

    fun init()
    {
        if (!handle.isNone) return

        require(resolution <= GlCapabilities.limits.maxCubeMapTextureSize) { "Cubemap array resolution exceeds the device limit: $resolution" }
        require(capacity <= GlCapabilities.limits.maxArrayTextureLayers / 6) { "Cubemap array capacity exceeds the device limit: $capacity" }

        val textureId = glGenTextures()
        try
        {
            glBindTexture(GL_TEXTURE_CUBE_MAP_ARRAY, textureId)

            if (GlCapabilities.immutableTextureStorage)
            {
                glTexStorage3D(GL_TEXTURE_CUBE_MAP_ARRAY, mipLevels, format.internalFormat, resolution, resolution, capacity * 6)
            }
            else
            {
                for (mip in 0 until mipLevels)
                {
                    val size = maxOf(resolution shr mip, 1)
                    glTexImage3D(GL_TEXTURE_CUBE_MAP_ARRAY, mip, format.internalFormat, size, size, capacity * 6, 0, format.pixelFormat, format.type, 0L)
                }
            }

            glTexParameteri(GL_TEXTURE_CUBE_MAP_ARRAY, GL_TEXTURE_BASE_LEVEL, 0)
            glTexParameteri(GL_TEXTURE_CUBE_MAP_ARRAY, GL_TEXTURE_MAX_LEVEL, mipLevels - 1)
            glTexParameteri(GL_TEXTURE_CUBE_MAP_ARRAY, GL_TEXTURE_MIN_FILTER, filter.minValue)
            glTexParameteri(GL_TEXTURE_CUBE_MAP_ARRAY, GL_TEXTURE_MAG_FILTER, filter.magValue)
            glTexParameteri(GL_TEXTURE_CUBE_MAP_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_CUBE_MAP_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_CUBE_MAP_ARRAY, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)

            val error = glGetError()
            check(error == GL_NO_ERROR) { "Failed to allocate cubemap array storage: ${resolution}px, $capacity cubes, $format, $mipLevels mips, error: $error" }

            handle = TextureHandle.createGlHandle(textureId)
        }
        catch (e: Exception)
        {
            glDeleteTextures(textureId)
            throw e
        }
    }

    /** 
     * Preserves all faces and mips. Callers must rebind the handle and reattach faces after growth. 
     */
    fun ensureCapacity(requiredCapacity: Int)
    {
        if (requiredCapacity <= capacity) return

        check(!handle.isNone) { "Cubemap array is not initialized" }
        
        val maximumCapacity = GlCapabilities.limits.maxArrayTextureLayers / 6
        require(requiredCapacity <= maximumCapacity) { "Cubemap array requires $requiredCapacity cubes, but this device supports $maximumCapacity" }

        val newCapacity = maxOf(requiredCapacity, minOf(capacity * 2, maximumCapacity))
        val replacement = CubeMapArray(newCapacity, resolution, format, filter, mipLevels)
        replacement.init()

        try
        {
            copyTo(replacement)
        }
        catch (e: Exception)
        {
            replacement.destroy()
            throw e
        }

        glDeleteTextures(handle.glId)
        handle = replacement.handle
        capacity = newCapacity
        replacement.handle = TextureHandle.NONE
    }

    /** 
     * Copies every face and mip into one stable slot without changing texture bindings. 
     */
    fun copyFrom(source: CubeMap, targetIndex: Int)
    {
        GlCapabilities.requireFullGraphics("Cubemap image copy")
        check(!handle.isNone && !source.handle.isNone) { "Cubemap copy requires initialized storage" }
        require(targetIndex in 0 until capacity) { "Cubemap index out of range: $targetIndex" }
        require(source.resolution == resolution && source.format == format && source.mipLevels == mipLevels) { "Cubemap copy requires matching resolution, format and mip count" }

        for (mip in 0 until mipLevels)
        {
            val size = resolution shr mip
            glCopyImageSubData(
                source.handle.glId, GL_TEXTURE_CUBE_MAP, mip, 0, 0, 0,
                handle.glId, GL_TEXTURE_CUBE_MAP_ARRAY, mip, 0, 0, targetIndex * 6,
                size, size, 6
            )
        }
    }

    fun generateMips()
    {
        check(!handle.isNone) { "Cubemap array is not initialized" }
        glBindTexture(GL_TEXTURE_CUBE_MAP_ARRAY, handle.glId)
        glGenerateMipmap(GL_TEXTURE_CUBE_MAP_ARRAY)
    }

    fun getLayerIndex(cubeIndex: Int, face: Int): Int
    {
        require(cubeIndex in 0 until capacity) { "Cubemap index out of range: $cubeIndex" }
        require(face in 0 until 6) { "Cubemap face out of range: $face" }
        return cubeIndex * 6 + face
    }

    fun destroy()
    {
        if (!handle.isNone)
            glDeleteTextures(handle.glId)
        handle = TextureHandle.NONE
    }

    private fun copyTo(destination: CubeMapArray)
    {
        val previousReadFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDrawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val framebufferSrgbEnabled = glIsEnabled(GL_FRAMEBUFFER_SRGB)
        val scissorTestEnabled = glIsEnabled(GL_SCISSOR_TEST)
        val readFramebuffer = glGenFramebuffers()
        val drawFramebuffer = glGenFramebuffers()
        try
        {
            glDisable(GL_FRAMEBUFFER_SRGB)
            glDisable(GL_SCISSOR_TEST)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            glDrawBuffer(GL_COLOR_ATTACHMENT0)

            for (mip in 0 until mipLevels)
            {
                val size = maxOf(resolution shr mip, 1)
                for (layer in 0 until capacity * 6)
                {
                    glFramebufferTextureLayer(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, handle.glId, mip, layer)
                    glFramebufferTextureLayer(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, destination.handle.glId, mip, layer)
                    check(glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) { "Cannot copy cubemap array layer $layer mip $mip" }
                    check(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) { "Cannot attach new cubemap array layer $layer mip $mip" }
                    glBlitFramebuffer(0, 0, size, size, 0, 0, size, size, GL_COLOR_BUFFER_BIT, GL_NEAREST)
                }
            }
            check(glGetError() == GL_NO_ERROR) { "Failed to copy cubemap array storage" }
        }
        finally
        {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer)
            if (framebufferSrgbEnabled) glEnable(GL_FRAMEBUFFER_SRGB)
            if (scissorTestEnabled) glEnable(GL_SCISSOR_TEST)
            glDeleteFramebuffers(readFramebuffer)
            glDeleteFramebuffers(drawFramebuffer)
        }
    }
}
