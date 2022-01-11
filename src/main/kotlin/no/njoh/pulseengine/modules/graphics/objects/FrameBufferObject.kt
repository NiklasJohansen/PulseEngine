package no.njoh.pulseengine.modules.graphics.objects

import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.AntiAliasingType
import no.njoh.pulseengine.modules.graphics.AntiAliasingType.MSAA_MAX
import no.njoh.pulseengine.modules.graphics.AntiAliasingType.NONE
import org.lwjgl.opengl.ARBFramebufferObject.*
import org.lwjgl.opengl.ARBTextureMultisample.GL_TEXTURE_2D_MULTISAMPLE
import org.lwjgl.opengl.ARBTextureMultisample.glTexImage2DMultisample
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.GL_RGBA32F

open class FrameBufferObject(
    private val frameBufferId: Int,
    private val depthBufferId: Int,
    val texture: Texture
) {
    fun bind() = glBindFramebuffer(GL_FRAMEBUFFER, frameBufferId)
    fun release() = glBindFramebuffer(GL_FRAMEBUFFER, 0)
    fun clear() = glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    fun delete()
    {
        texture.delete()
        glDeleteRenderbuffers(depthBufferId)
        glDeleteFramebuffers(frameBufferId)
    }

    fun resolveToFBO(outputFbo: FrameBufferObject)
    {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, outputFbo.frameBufferId)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, this.frameBufferId)
        glBlitFramebuffer(
            0, 0, texture.width, texture.height,
            0, 0, outputFbo.texture.width, outputFbo.texture.height,
            GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT, GL_NEAREST
        )
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    companion object
    {
        fun create(
            width: Int,
            height: Int,
            textureScale: Float,
            hdr: Boolean = false,
            antiAliasing: AntiAliasingType = NONE
        ): FrameBufferObject
        {
            // Generate and bind frame buffer
            val frameBufferId = glGenFramebuffers()
            glBindFramebuffer(GL_FRAMEBUFFER, frameBufferId)

            val texWidth = (width * textureScale).toInt()
            val texHeight = (height * textureScale).toInt()
            var colorTextureId = 0
            var depthBufferId = 0
            if (antiAliasing != NONE)
            {
                // Create multi sampled color and depth attachments
                val samples = if (antiAliasing == MSAA_MAX) glGetInteger(GL_MAX_SAMPLES) else antiAliasing.samples
                colorTextureId = createMultiSampledColorTextureAttachment(texWidth, texHeight, hdr, samples)
                depthBufferId = createMultiSampledDepthBufferAttachment(texWidth, texHeight, samples)
            }
            else
            {
                // Create color and depth attachments
                colorTextureId = createColorTextureAttachment(texWidth, texHeight, hdr)
                depthBufferId = createDepthBufferAttachment(texWidth, texHeight)
            }

            // Check if frame buffer is complete
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw RuntimeException("Failed to create frame buffer object")

            // Unbind frame buffer (binds default buffer)
            glBindFramebuffer(GL_FRAMEBUFFER, 0)

            // Create texture asset
            val texture = Texture("", "")
            texture.load(null, texWidth, texHeight, GL_RGBA8)
            texture.finalize(colorTextureId)

            return FrameBufferObject(frameBufferId, depthBufferId, texture)
        }

        private fun createColorTextureAttachment(width: Int, height: Int, hdr: Boolean): Int
        {
            val textureId = glGenTextures()
            val nullPixels: FloatArray? = null
            val format = if (hdr) GL_RGBA32F else GL_RGBA
            glBindTexture(GL_TEXTURE_2D, textureId)
            glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullPixels)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureId, 0)
            glBindTexture(GL_TEXTURE_2D, 0)
            return textureId
        }

        private fun createDepthBufferAttachment(width: Int, height: Int): Int
        {
            val depthBufferId = glGenRenderbuffers()
            glBindRenderbuffer(GL_RENDERBUFFER, depthBufferId)
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthBufferId)
            return depthBufferId
        }

        private fun createMultiSampledColorTextureAttachment(width: Int, height: Int, hdr: Boolean, samples: Int): Int
        {
            val textureId = glGenTextures()
            val format = if (hdr) GL_RGBA32F else GL_RGBA8
            glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, textureId)
            glTexImage2DMultisample(GL_TEXTURE_2D_MULTISAMPLE, samples, format, width, height, true)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D_MULTISAMPLE, textureId, 0)
            glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, 0)
            return textureId
        }

        private fun createMultiSampledDepthBufferAttachment(width: Int, height: Int, samples: Int): Int
        {
            val depthBufferId = glGenRenderbuffers()
            glBindRenderbuffer(GL_RENDERBUFFER, depthBufferId)
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH24_STENCIL8, width, height)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthBufferId)
            return depthBufferId
        }
    }
}