package no.njoh.pulseengine.core.graphics.gpu.texture

import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.LINEAR_MIPMAP
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.RGBA16F
import org.lwjgl.opengl.GL33.*
import org.lwjgl.opengl.GL42.glTexStorage2D

/**
 * GPU storage for six square faces following OpenGL order: +X, -X, +Y, -Y, +Z, -Z.
 */
class CubeMap(
    val resolution: Int,
    val format: TextureFormat = RGBA16F,
    val filter: TextureFilter = LINEAR_MIPMAP,
    val mipLevels: Int = Integer.SIZE - Integer.numberOfLeadingZeros(resolution)
) {
    var handle = TextureHandle.NONE; private set

    init
    {
        require(resolution > 0) { "Cubemap resolution must be positive" }
        require(mipLevels in 1..Integer.SIZE - Integer.numberOfLeadingZeros(resolution)) { "Invalid cubemap mip count: $mipLevels" }
    }

    fun init()
    {
        if (!handle.isNone) return

        require(resolution <= GlCapabilities.limits.maxCubeMapTextureSize) { "Cubemap resolution exceeds the device limit: $resolution" }

        val textureId = glGenTextures()
        glBindTexture(GL_TEXTURE_CUBE_MAP, textureId)

        if (GlCapabilities.immutableTextureStorage)
        {
            glTexStorage2D(GL_TEXTURE_CUBE_MAP, mipLevels, format.internalFormat, resolution, resolution)
        }
        else
        {
            for (mip in 0 until mipLevels)
            {
                val size = maxOf(resolution shr mip, 1)
                for (face in 0 until 6)
                    glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, mip, format.internalFormat, size, size, 0, format.pixelFormat, format.type, 0L)
            }
        }

        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_BASE_LEVEL, 0)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAX_LEVEL, mipLevels - 1)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, filter.minValue)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, filter.magValue)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)

        val error = glGetError()
        check(error == GL_NO_ERROR) { "Failed to allocate cubemap storage: ${resolution}px, $format, $mipLevels mips, error: $error" }

        handle = TextureHandle.createGlHandle(textureId)
    }

    fun generateMips()
    {
        check(!handle.isNone) { "Cubemap is not initialized" }
        glBindTexture(GL_TEXTURE_CUBE_MAP, handle.glId)
        glGenerateMipmap(GL_TEXTURE_CUBE_MAP)
    }

    fun destroy()
    {
        if (!handle.isNone)
            glDeleteTextures(handle.glId)
    
        handle = TextureHandle.NONE
    }
}