package no.njoh.pulseengine.core.graphics.api.objects

import gnu.trove.list.array.TLongArrayList
import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.api.Multisampling.MSAA_MAX
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.Multisampling.NONE
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.shared.primitives.PackedSize
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachIndexedFast
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL32.GL_TEXTURE_2D_MULTISAMPLE
import org.lwjgl.opengl.GL32.glTexImage2DMultisample
import org.lwjgl.opengl.GL42.glTexStorage2D
import kotlin.math.min

open class FrameBufferObject(
    val id: Int,
    private val textures: List<RenderTexture>,
    private val textureSizes: TLongArrayList, // Holds PackedSize elements
    private val textureDescriptors: List<TextureDescriptor>,
    private val renderBufferIds: List<Int>
) {
    private val textureBuffers = textures
        .map { it.attachment }
        .distinct()
        .filter { it.isDrawable }
        .ifEmpty { listOf(COLOR_TEXTURE_0) }
        .map { it.value }
        .toIntArray()

    fun bind()
    {
        glBindFramebuffer(GL_FRAMEBUFFER, id)
        glDrawBuffers(textureBuffers)
    }

    fun release() = glBindFramebuffer(GL_FRAMEBUFFER, 0)

    fun clear() = glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

    fun destroy()
    {
        textures.forEachFast { glDeleteTextures(it.handle.textureIndex) }
        renderBufferIds.forEachFast { glDeleteRenderbuffers(it) }
        glDeleteFramebuffers(id)
    }

    fun getTextureOrNull(index: Int = 0) = textures.getOrNull(index)

    fun getTexture(index: Int = 0) = textures[index]

    fun getTextures() = textures

    fun attachOutputTexture(texture: RenderTexture, attachment: Attachment = texture.attachment, mipLevel: Int = 0)
    {
        val target = if (texture.multisampling == NONE) GL_TEXTURE_2D else GL_TEXTURE_2D_MULTISAMPLE
        glFramebufferTexture2D(GL_FRAMEBUFFER, attachment.value, target, texture.handle.textureIndex, mipLevel)
    }

    fun resolveToFBO(destinationFbo: FrameBufferObject)
    {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, this.id)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destinationFbo.id)

        for (i in 0 until min(this.textures.size, destinationFbo.textures.size))
        {
            val sourceTexture = textures[i]
            val destinationTexture = destinationFbo.textures[i]
            glReadBuffer(sourceTexture.attachment.value)
            glDrawBuffers(destinationTexture.attachment.value)
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
        if (textureDescriptors.size != descriptors.size)
            return false

        textureDescriptors.forEachIndexedFast { i, aDesc ->
            val bDesc = descriptors[i]
            if (aDesc != bDesc)
                return false
            val bSize = bDesc.sizeFunc(width, height, bDesc.scale)
            val aSize = PackedSize(textureSizes[i])
            if (aSize != bSize)
                return false
        }

        return true
    }

    companion object
    {
        fun create(width: Int, height: Int, textureDescriptors: List<TextureDescriptor>): FrameBufferObject
        {
            val renderTextures = mutableListOf<RenderTexture>()
            val renderBufferIds = mutableListOf<Int>()
            val textureSizes = TLongArrayList()
            val frameBufferId = glGenFramebuffers()

            glBindFramebuffer(GL_FRAMEBUFFER, frameBufferId)

            for (texDesc in textureDescriptors)
            {
                val samples = if (texDesc.multisampling == MSAA_MAX) glGetInteger(GL_MAX_SAMPLES) else texDesc.multisampling.samples
                val texSize = texDesc.sizeFunc(width, height, texDesc.scale)
                val (texWidth, texHeight) = texSize
                textureSizes.add(texSize.data)

                val textureId = when (texDesc.attachment)
                {
                    COLOR_TEXTURE_0,
                    COLOR_TEXTURE_1,
                    COLOR_TEXTURE_2,
                    COLOR_TEXTURE_3,
                    COLOR_TEXTURE_4 -> createColorTextureAttachment(texWidth, texHeight, texDesc.format, texDesc.filter, texDesc.wrapping, texDesc.attachment, texDesc.mipmapGenerator, samples)
                    DEPTH_TEXTURE   -> createDepthTextureAttachment(texWidth, texHeight, samples)
                    DEPTH_STENCIL_BUFFER -> null
                }

                if (textureId != null)
                {
                    renderTextures += RenderTexture(
                        name = "fbo_${texDesc.attachment.name.lowercase()}",
                        handle = TextureHandle.create(0, textureId),
                        width = texWidth,
                        height = texHeight,
                        filter = texDesc.filter,
                        wrapping = texDesc.wrapping,
                        format = texDesc.format,
                        attachment = texDesc.attachment,
                        multisampling = texDesc.multisampling,
                        mipmapGenerator = texDesc.mipmapGenerator
                    )
                }

                val renderBufferId = when (texDesc.attachment)
                {
                    DEPTH_STENCIL_BUFFER -> createDepthBufferAttachment(texWidth, texHeight, samples)
                    else -> null
                }

                if (renderBufferId != null)
                    renderBufferIds += renderBufferId
            }

            // Check if the frame buffer is complete. It will not be complete if it has no textures attached,
            // so we skip this check. A texture must be attached to the buffer at a later point for it to be usable.
            if (textureDescriptors.isNotEmpty())
            {
                val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
                if (status != GL_FRAMEBUFFER_COMPLETE)
                    throw RuntimeException("Failed to create frame buffer object. Status: $status")
            }

            // Unbind frame buffer (binds default buffer)
            glBindFramebuffer(GL_FRAMEBUFFER, 0)

            return FrameBufferObject(frameBufferId, renderTextures, textureSizes, textureDescriptors.map { it.copy() }, renderBufferIds)
        }

        private fun createColorTextureAttachment(
            width: Int,
            height: Int,
            format: TextureFormat,
            filter: TextureFilter,
            wrapping: TextureWrapping,
            attachment: Attachment,
            mipmapGenerator: MipmapGenerator?,
            samples: Int,
        ): Int {
            val target = if (samples > 1) GL_TEXTURE_2D_MULTISAMPLE else GL_TEXTURE_2D
            val textureId = glGenTextures()
            glBindTexture(target, textureId)

            if (samples > 1)
            {
                glTexImage2DMultisample(target, samples, format.internalFormat, width, height, true)
            }
            else
            {
                if (mipmapGenerator != null)
                {
                    val levels = mipmapGenerator.getLevelCount(width, height)
                    glTexStorage2D(target, levels, format.internalFormat, width, height)
                }
                else glTexImage2D(target, 0, format.internalFormat, width, height, 0, format.pixelFormat, GL_UNSIGNED_BYTE, null as FloatArray?)

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
            {
                glTexImage2DMultisample(target, samples, GL_DEPTH_COMPONENT24, width, height, true)
            }
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
            {
                glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH24_STENCIL8, width, height)
            }
            else
            {
                glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height)
            }
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthBufferId)
            glBindRenderbuffer(GL_RENDERBUFFER, 0)
            return depthBufferId
        }
    }
}