package engine.modules.graphics

import org.lwjgl.opengl.ARBFramebufferObject.*
import org.lwjgl.opengl.GL11.*
import java.lang.RuntimeException

class FrameBufferObject(
    private val frameBufferId: Int,
    private val renderBufferId: Int,
    val textureId: Int
) {
    fun bind() = glBindFramebuffer(GL_FRAMEBUFFER, frameBufferId)
    fun release() = glBindFramebuffer(GL_FRAMEBUFFER, 0)
    fun delete()
    {
        glDeleteFramebuffers(frameBufferId)
        glDeleteRenderbuffers(renderBufferId)
        glDeleteTextures(textureId)
    }

    companion object
    {
        fun create(width: Int, height: Int): FrameBufferObject
        {
            // Generate frame buffer
            val frameBufferId = glGenFramebuffers()
            glBindFramebuffer(GL_FRAMEBUFFER, frameBufferId);

            // Color attachment
            val textureId = glGenTextures()
            val nullPixels: FloatArray? = null
            glBindTexture(GL_TEXTURE_2D, textureId)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, nullPixels)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureId, 0)
            glBindTexture(GL_TEXTURE_2D, 0)

            // Depth and stencil attachment
            val renderBufferId = glGenRenderbuffers()
            glBindRenderbuffer(GL_RENDERBUFFER, renderBufferId)
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, renderBufferId)
            glBindRenderbuffer(GL_RENDERBUFFER, 0)

            // Check if frame buffer is complete
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw RuntimeException("Failed to create frame buffer object")

            // Unbind frame buffer (binds default buffer)
            glBindFramebuffer(GL_FRAMEBUFFER, 0)

            return FrameBufferObject(frameBufferId, renderBufferId, textureId)
        }
    }
}