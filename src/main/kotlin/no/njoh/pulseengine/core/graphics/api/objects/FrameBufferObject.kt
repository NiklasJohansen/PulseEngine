package no.njoh.pulseengine.core.graphics.api.objects

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.api.Multisampling.MSAA_MAX
import no.njoh.pulseengine.core.graphics.api.Multisampling.NONE
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA8
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24
import org.lwjgl.opengl.GL20.glDrawBuffers
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL32.GL_TEXTURE_2D_MULTISAMPLE
import org.lwjgl.opengl.GL32.glTexImage2DMultisample
import java.nio.ByteBuffer
import kotlin.math.min

open class FrameBufferObject(
    val id: Int,
    val width: Int,
    val height: Int,
    private val renderBufferIds: List<Int>,
    private val textures: List<Texture>
) {
    fun bind() = glBindFramebuffer(GL_FRAMEBUFFER, id)
    fun release() = glBindFramebuffer(GL_FRAMEBUFFER, 0)
    fun clear() = glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    fun delete()
    {
        textures.forEachFast { glDeleteTextures(it.handle.textureIndex) }
        renderBufferIds.forEachFast { glDeleteRenderbuffers(it) }
        glDeleteFramebuffers(id)
    }

    fun getTexture(index: Int = 0): Texture? =
        textures.getOrNull(index)

    fun getTextures() = textures

    fun resolveToFBO(destinationFbo: FrameBufferObject)
    {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, this.id)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destinationFbo.id)

        for (i in 0 until min(this.textures.size, destinationFbo.textures.size))
        {
            val sourceTexture = textures[i]
            val destinationTexture = destinationFbo.textures[i]
            glReadBuffer(sourceTexture.attachment)
            glDrawBuffers(destinationTexture.attachment)
            glBlitFramebuffer(
                0, 0, sourceTexture.width, sourceTexture.height,
                0, 0, destinationTexture.width, destinationTexture.height,
                GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT,
                GL_NEAREST
            )
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    companion object
    {
        fun create(
            width: Int,
            height: Int,
            textureScale: Float = 1f,
            textureFormat: TextureFormat = RGBA8,
            textureFilter: TextureFilter = LINEAR,
            multisampling: Multisampling = NONE,
            attachments: List<Attachment> = listOf(COLOR_TEXTURE_0, DEPTH_STENCIL_BUFFER)
        ): FrameBufferObject {
            // Generate and bind frame buffer
            val frameBufferId = glGenFramebuffers()
            glBindFramebuffer(GL_FRAMEBUFFER, frameBufferId)

            val samples = if (multisampling == MSAA_MAX) glGetInteger(GL_MAX_SAMPLES) else multisampling.samples
            val texWidth = (width * textureScale).toInt()
            val texHeight = (height * textureScale).toInt()
            val textures = mutableListOf<Texture>()
            val bufferIds = mutableListOf<Int>()

            // Create attachments
            for (attachment in attachments)
            {
                val textureId = when (attachment)
                {
                    COLOR_TEXTURE_0, COLOR_TEXTURE_1, COLOR_TEXTURE_2, COLOR_TEXTURE_3, COLOR_TEXTURE_4 ->
                        createColorTextureAttachment(texWidth, texHeight, textureFormat, textureFilter, attachment, samples)
                    DEPTH_TEXTURE ->
                        createDepthTextureAttachment(texWidth, texHeight, samples)
                    else -> null
                }

                if (textureId != null)
                {
                    val texture = Texture("", "fbo_${attachment.name.toLowerCase()}")
                    val handle = TextureHandle.create(0, textureId)
                    texture.stage(null as ByteBuffer?, texWidth, texHeight)
                    texture.finalize(handle, isBindless = false, attachment = attachment.value)
                    textures.add(texture)
                }

                val bufferId = when (attachment)
                {
                    DEPTH_STENCIL_BUFFER -> createDepthBufferAttachment(texWidth, texHeight, samples)
                    else -> null
                }

                if (bufferId != null)
                    bufferIds.add(bufferId)
            }

            // Check if frame buffer is complete
            val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
            if (status != GL_FRAMEBUFFER_COMPLETE)
                throw RuntimeException("Failed to create frame buffer object. Status: $status")

            // Unbind frame buffer (binds default buffer)
            glBindFramebuffer(GL_FRAMEBUFFER, 0)

            return FrameBufferObject(frameBufferId, width, height, bufferIds, textures)
        }

        private fun createColorTextureAttachment(width: Int, height: Int, format: TextureFormat, filter: TextureFilter, attachment: Attachment, samples: Int): Int
        {
            val target = if (samples > 1) GL_TEXTURE_2D_MULTISAMPLE else GL_TEXTURE_2D
            val textureId = glGenTextures()
            glBindTexture(target, textureId)
            if (samples > 1)
                glTexImage2DMultisample(target, samples, format.value, width, height, true)
            else
            {
                glTexImage2D(target, 0, format.value, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as FloatArray?)
                glTexParameteri(target, GL_TEXTURE_MIN_FILTER, filter.value)
                glTexParameteri(target, GL_TEXTURE_MAG_FILTER, filter.value)
                glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
                glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            }
            glFramebufferTexture2D(GL_FRAMEBUFFER, attachment.value, target, textureId, 0)
            glBindTexture(target, 0)
            return textureId
        }

        private fun createDepthTextureAttachment(width: Int, height: Int, samples: Int): Int
        {
            val target = if (samples > 1) GL_TEXTURE_2D_MULTISAMPLE else GL_TEXTURE_2D
            val textureId = glGenTextures()
            glBindTexture(target, textureId)
            if (samples > 1)
                glTexImage2DMultisample(target, samples, GL_DEPTH_COMPONENT24, width, height, true)
            else
            {
                glTexImage2D(target, 0, GL_DEPTH_COMPONENT24, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0)
                glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
                glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
                glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
                glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            }
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, target, textureId, 0)
            glBindTexture(target, 0)
            return textureId
        }

        private fun createDepthBufferAttachment(width: Int, height: Int, samples: Int): Int
        {
            val depthBufferId = glGenRenderbuffers()
            glBindRenderbuffer(GL_RENDERBUFFER, depthBufferId)
            if (samples > 1)
                glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH24_STENCIL8, width, height)
            else
                glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthBufferId)
            glBindRenderbuffer(GL_FRAMEBUFFER, 0)
            return depthBufferId
        }
    }
}