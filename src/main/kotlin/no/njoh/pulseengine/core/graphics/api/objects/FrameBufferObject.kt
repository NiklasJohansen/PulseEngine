package no.njoh.pulseengine.core.graphics.api.objects

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.api.Multisampling.MSAA_MAX
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachIndexedFast
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
    private val textures: List<Texture>,
    private val textureDescriptors: List<TextureDescriptor>
) {
    private val textureBuffers = textures.mapNotNull { it.attachment }.filter { it.isDrawable }.map { it.value }.toIntArray()

    fun bind()
    {
        glBindFramebuffer(GL_FRAMEBUFFER, id)
        glDrawBuffers(textureBuffers)
    }

    fun release() = glBindFramebuffer(GL_FRAMEBUFFER, 0)

    fun clear() = glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

    fun delete()
    {
        textures.forEachFast { glDeleteTextures(it.handle.textureIndex) }
        renderBufferIds.forEachFast { glDeleteRenderbuffers(it) }
        glDeleteFramebuffers(id)
    }

    fun getTexture(index: Int = 0) = textures.getOrNull(index)

    fun getTextures() = textures

    fun resolveToFBO(destinationFbo: FrameBufferObject)
    {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, this.id)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destinationFbo.id)

        for (i in 0 until min(this.textures.size, destinationFbo.textures.size))
        {
            val sourceTexture = textures[i]
            val destinationTexture = destinationFbo.textures[i]
            glReadBuffer(sourceTexture.attachment!!.value)
            glDrawBuffers(destinationTexture.attachment!!.value)
            glBlitFramebuffer(
                0, 0, sourceTexture.width, sourceTexture.height,
                0, 0, destinationTexture.width, destinationTexture.height,
                GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT,
                GL_NEAREST
            )
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    fun matches(width: Int, height: Int, descriptors: List<TextureDescriptor>): Boolean
    {
        if (width != this.width || height != this.height)
            return false

        if (textureDescriptors.size != descriptors.size)
            return false

        textureDescriptors.forEachIndexedFast { i, desc -> if (desc != descriptors[i]) return false }

        return true
    }

    companion object
    {
        fun create(width: Int, height: Int, textureDescriptors: List<TextureDescriptor>): FrameBufferObject
        {
            val textures = mutableListOf<Texture>()
            val bufferIds = mutableListOf<Int>()
            val frameBufferId = glGenFramebuffers()
            glBindFramebuffer(GL_FRAMEBUFFER, frameBufferId)

            for (tex in textureDescriptors)
            {
                val samples = if (tex.multisampling == MSAA_MAX) glGetInteger(GL_MAX_SAMPLES) else tex.multisampling.samples
                val texWidth = (width * tex.scale).toInt()
                val texHeight = (height * tex.scale).toInt()

                val textureId = when (tex.attachment)
                {
                    COLOR_TEXTURE_0, COLOR_TEXTURE_1, COLOR_TEXTURE_2, COLOR_TEXTURE_3, COLOR_TEXTURE_4 ->
                        createColorTextureAttachment(texWidth, texHeight, tex.format, tex.filter, tex.wrapping, tex.attachment, samples)
                    DEPTH_TEXTURE ->
                        createDepthTextureAttachment(texWidth, texHeight, samples)
                    else -> null
                }

                if (textureId != null)
                {
                    val texture = Texture("", name = "fbo_${tex.attachment.name.lowercase()}", tex.filter, tex.wrapping, mipLevels = 1)
                    val handle = TextureHandle.create(0, textureId)
                    texture.stage(null as ByteBuffer?, texWidth, texHeight, tex.format)
                    texture.finalize(handle, isBindless = false, attachment = tex.attachment)
                    textures.add(texture)
                }

                val bufferId = when (tex.attachment)
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

            return FrameBufferObject(frameBufferId, width, height, bufferIds, textures, textureDescriptors.map { it.copy() })
        }

        private fun createColorTextureAttachment(width: Int, height: Int, format: TextureFormat, filter: TextureFilter, wrapping: TextureWrapping, attachment: Attachment, samples: Int): Int
        {
            val target = if (samples > 1) GL_TEXTURE_2D_MULTISAMPLE else GL_TEXTURE_2D
            val textureId = glGenTextures()
            glBindTexture(target, textureId)
            if (samples > 1)
            {
                glTexImage2DMultisample(target, samples, format.internalFormat, width, height, true)
            }
            else
            {
                glTexImage2D(target, 0, format.internalFormat, width, height, 0, format.pixelFormat, GL_UNSIGNED_BYTE, null as FloatArray?)
                glTexParameteri(target, GL_TEXTURE_MIN_FILTER, filter.minValue)
                glTexParameteri(target, GL_TEXTURE_MAG_FILTER, filter.magValue)
                glTexParameteri(target, GL_TEXTURE_WRAP_S, wrapping.value)
                glTexParameteri(target, GL_TEXTURE_WRAP_T, wrapping.value)
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
            glBindRenderbuffer(GL_RENDERBUFFER, 0)
            return depthBufferId
        }
    }
}