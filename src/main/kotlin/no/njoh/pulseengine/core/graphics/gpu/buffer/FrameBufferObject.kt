package no.njoh.pulseengine.core.graphics.gpu.buffer

import gnu.trove.list.array.TLongArrayList
import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling.MSAA_MAX
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.*
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling.NONE
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureDescriptor
import no.njoh.pulseengine.core.graphics.gpu.texture.mipmap.MipmapGenerator
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureArray
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureHandle
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping
import no.njoh.pulseengine.core.shared.primitives.PackedSize
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachIndexedFast
import no.njoh.pulseengine.core.shared.utils.Extensions.noneMatches
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL32.GL_MAX_COLOR_TEXTURE_SAMPLES
import org.lwjgl.opengl.GL32.GL_TEXTURE_2D_MULTISAMPLE
import org.lwjgl.opengl.GL32.glFramebufferTexture
import org.lwjgl.opengl.GL32.glTexImage2DMultisample
import org.lwjgl.opengl.GL42.glTexStorage2D
import java.util.Arrays

open class FrameBufferObject(
    val id: Int,
    private val textures: List<RenderTexture>,
    private val textureSizes: TLongArrayList, // Holds PackedSize elements
    private val textureDescriptors: List<TextureDescriptor>,
    private val renderBufferIds: List<Int>
) {
    private val contextGeneration = GlCapabilities.contextGeneration
    private var destroyed = false

    private val textureBuffers = createDefaultDrawBuffers(textures)
    private val selectedDrawBuffers = IntArray(COLOR_ATTACHMENT_COUNT) { GL_NONE }

    fun bind()
    {
        glBindFramebuffer(GL_FRAMEBUFFER, id)
        glDrawBuffers(textureBuffers)
    }

    fun setDrawBuffer(attachmentPoint: AttachmentPoint)
    {
        glDrawBuffer(if (attachmentPoint.isColor) attachmentPoint.glValue else GL_NONE)
    }

    fun setDrawBuffers(first: AttachmentPoint, second: AttachmentPoint)
    {
        Arrays.fill(selectedDrawBuffers, GL_NONE)
        if (first.isColor) selectedDrawBuffers[first.glLocation] = first.glValue
        if (second.isColor) selectedDrawBuffers[second.glLocation] = second.glValue
        glDrawBuffers(selectedDrawBuffers)
    }

    fun release() = glBindFramebuffer(GL_FRAMEBUFFER, 0)

    fun clear() = glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

    fun destroy()
    {
        if (destroyed) return

        destroyed = true
        textures.forEachFast { glDeleteTextures(it.handle.glId) }
        renderBufferIds.forEachFast { glDeleteRenderbuffers(it) }

        // GLFW shares texture and renderbuffer objects with the replacement context, so these are
        // always released above. Framebuffer IDs are context-local and die with their old context.
        // Deleting a stale numeric ID here could delete an unrelated FBO in the new context.
        if (contextGeneration == GlCapabilities.contextGeneration)
            glDeleteFramebuffers(id)
    }

    fun getTextureOrNull(index: Int = 0) = textures.getOrNull(index)

    fun getTextureOrNull(attachmentPoint: AttachmentPoint): RenderTexture?
    {
        textures.forEachFast { if (it.attachmentPoint == attachmentPoint) return it }
        return null
    }

    fun getTexture(index: Int = 0) = textures[index]

    fun getTextures() = textures

    fun attachOutputTexture(texture: RenderTexture, attachmentPoint: AttachmentPoint = texture.attachmentPoint, mipLevel: Int = 0)
    {
        val target = if (texture.multisampling == NONE) GL_TEXTURE_2D else GL_TEXTURE_2D_MULTISAMPLE
        val buf = if (attachmentPoint.isColor) attachmentPoint.glValue else GL_NONE
        when (texture.multisampling) 
        {
            NONE -> glFramebufferTexture2D(GL_FRAMEBUFFER, attachmentPoint.glValue, target, texture.handle.glId, mipLevel)
            else -> glFramebufferTexture(GL_FRAMEBUFFER, attachmentPoint.glValue, texture.handle.glId, mipLevel)
        }
        glDrawBuffer(buf)
        glReadBuffer(buf)
    }

    fun attachOutputTextureArray(textureArray: TextureArray, layerIndex: Int, attachmentPoint: AttachmentPoint, mipLevel: Int = 0)
    {
        val buf = if (attachmentPoint.isColor) attachmentPoint.glValue else GL_NONE
        glFramebufferTextureLayer(GL_FRAMEBUFFER, attachmentPoint.glValue, textureArray.id, mipLevel, layerIndex)
        glDrawBuffer(buf)
        glReadBuffer(buf)
    }

    fun resolveToFBO(destinationFbo: FrameBufferObject) 
    {
        resolveColorToFBO(destinationFbo)
        resolveDepthToFBO(destinationFbo)
        release()
    }

    fun resolveColorToFBO(destinationFbo: FrameBufferObject)
    {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, this.id)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destinationFbo.id)

        textures.forEachIndexedFast { i, src ->
            if (i >= destinationFbo.textures.size)
                return@forEachIndexedFast

            val dst = destinationFbo.textures[i]
            if (!src.attachmentPoint.isColor || !dst.attachmentPoint.isColor || src.attachmentPoint != dst.attachmentPoint)
                return@forEachIndexedFast // Skip non-color attachments

            glReadBuffer(src.attachmentPoint.glValue)
            glDrawBuffer(dst.attachmentPoint.glValue)

            glBlitFramebuffer(
                0, 0, src.width, src.height,
                0, 0, dst.width, dst.height,
                GL_COLOR_BUFFER_BIT,
                GL_NEAREST
            )
        }

        glBindFramebuffer(GL_FRAMEBUFFER, this.id)
    }

    fun resolveDepthToFBO(destinationFbo: FrameBufferObject) 
    {
        if (textures.noneMatches { it.attachmentPoint.isDepth } || destinationFbo.textures.noneMatches { it.attachmentPoint.isDepth })
            return // No depth attachment to resolve

        glBindFramebuffer(GL_READ_FRAMEBUFFER, this.id)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destinationFbo.id)

        if (textures.isEmpty() || destinationFbo.textures.isEmpty())
            return

        val wSrc = textures[0].width
        val hSrc = textures[0].height
        val wDst = destinationFbo.textures[0].width
        val hDst = destinationFbo.textures[0].height

        glBlitFramebuffer(
            0, 0, wSrc, hSrc,
            0, 0, wDst, hDst,
            GL_DEPTH_BUFFER_BIT,
            GL_NEAREST
        )

        // Re-bind this
        glBindFramebuffer(GL_FRAMEBUFFER, this.id)
    }

    fun matches(width: Int, height: Int, descriptors: List<TextureDescriptor>): Boolean
    {
        if (destroyed || contextGeneration != GlCapabilities.contextGeneration)
            return false

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

    fun checkStatus() = FrameBufferObject.checkStatus()

    companion object
    {
        private const val COLOR_ATTACHMENT_COUNT = 5

        private fun createDefaultDrawBuffers(textures: List<RenderTexture>): IntArray
        {
            var maxLocation = -1
            textures.forEachFast { if (it.attachmentPoint.isColor) maxLocation = maxOf(maxLocation, it.attachmentPoint.glLocation) }

            if (maxLocation < 0)
                return intArrayOf(GL_NONE)

            val buffers = IntArray(maxLocation + 1) { GL_NONE }
            textures.forEachFast() 
            {
                val attachmentPoint = it.attachmentPoint
                if (attachmentPoint.isColor) buffers[attachmentPoint.glLocation] = attachmentPoint.glValue
            }

            return buffers
        }

        fun create(width: Int, height: Int, textureDescriptors: List<TextureDescriptor>): FrameBufferObject
        {
            val renderTextures = mutableListOf<RenderTexture>()
            val renderBufferIds = mutableListOf<Int>()
            val textureSizes = TLongArrayList()
            val frameBufferId = glGenFramebuffers()

            glBindFramebuffer(GL_FRAMEBUFFER, frameBufferId)
 
            for (texDesc in textureDescriptors)
            {
                val requestedSamples = if (texDesc.multisampling == MSAA_MAX) Int.MAX_VALUE else texDesc.multisampling.samples
                val samples = minOf(requestedSamples, glGetInteger(GL_MAX_SAMPLES), glGetInteger(GL_MAX_COLOR_TEXTURE_SAMPLES))
                val texSize = texDesc.sizeFunc(width, height, texDesc.scale)
                val (texWidth, texHeight) = texSize
                textureSizes.add(texSize.data)

                val textureId = when (texDesc.attachmentPoint)
                {
                    COLOR_TEXTURE_0,
                    COLOR_TEXTURE_1,
                    COLOR_TEXTURE_2,
                    COLOR_TEXTURE_3,
                    COLOR_TEXTURE_4 -> createColorTextureAttachment(texWidth, texHeight, texDesc.format, texDesc.filter, texDesc.wrapping, texDesc.attachmentPoint, texDesc.mipmapGenerator, samples)
                    DEPTH_TEXTURE   -> createDepthTextureAttachment(texWidth, texHeight, samples, texDesc.mipmapGenerator)
                    DEPTH_STENCIL_BUFFER -> null
                }

                if (textureId != null)
                {
                    renderTextures += RenderTexture(
                        name = "fbo_${texDesc.attachmentPoint.name.lowercase()}",
                        handle = TextureHandle.createGlHandle(textureId),
                        width = texWidth,
                        height = texHeight,
                        filter = texDesc.filter,
                        wrapping = texDesc.wrapping,
                        format = texDesc.format,
                        attachmentPoint = texDesc.attachmentPoint,
                        multisampling = texDesc.multisampling,
                        mipmapGenerator = texDesc.mipmapGenerator
                    )
                }

                val renderBufferId = when (texDesc.attachmentPoint)
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
                checkStatus()

            // Unbind frame buffer (binds default buffer)
            glBindFramebuffer(GL_FRAMEBUFFER, 0)

            return FrameBufferObject(frameBufferId, renderTextures, textureSizes, textureDescriptors.map { it.copy() }, renderBufferIds)
        }

        fun createEmpty(): FrameBufferObject
        {
            val id = glGenFramebuffers()
            return FrameBufferObject(id, emptyList(), TLongArrayList(), emptyList(), emptyList())
        }

        fun checkStatus()
        {
            val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
            if (status != GL_FRAMEBUFFER_COMPLETE)
                throw RuntimeException("Failed to create frame buffer object. Status: $status")
        }

        private fun createColorTextureAttachment(
            width: Int,
            height: Int,
            format: TextureFormat,
            filter: TextureFilter,
            wrapping: TextureWrapping,
            attachmentPoint: AttachmentPoint,
            mipmapGenerator: MipmapGenerator?,
            samples: Int,
        ): Int {
            val target = if (samples > 1) GL_TEXTURE_2D_MULTISAMPLE else GL_TEXTURE_2D
            val textureId = glGenTextures()
            glBindTexture(target, textureId)

            if (samples > 1)
            {
                glTexImage2DMultisample(target, samples, format.internalFormat, width, height, true)
                glFramebufferTexture(GL_FRAMEBUFFER, attachmentPoint.glValue, textureId, 0)
            }
            else
            {
                if (mipmapGenerator != null)
                {
                    val levels = mipmapGenerator.getLevelCount(width, height)
                    allocateTextureStorage2D(target, levels, format.internalFormat, width, height, format.pixelFormat, format.type)
                }
                else glTexImage2D(target, 0, format.internalFormat, width, height, 0, format.pixelFormat, format.type, 0L)

                glTexParameteri(target, GL_TEXTURE_MIN_FILTER, filter.minValue)
                glTexParameteri(target, GL_TEXTURE_MAG_FILTER, filter.magValue)
                glTexParameteri(target, GL_TEXTURE_WRAP_S, wrapping.value)
                glTexParameteri(target, GL_TEXTURE_WRAP_T, wrapping.value)
                glFramebufferTexture2D(GL_FRAMEBUFFER, attachmentPoint.glValue, target, textureId, 0)
            }

            glBindTexture(target, 0)
            return textureId
        }

        private fun createDepthTextureAttachment(width: Int, height: Int, samples: Int, mipmapGenerator: MipmapGenerator?): Int
        {
            val target = if (samples > 1) GL_TEXTURE_2D_MULTISAMPLE else GL_TEXTURE_2D
            val textureId = glGenTextures()
            glBindTexture(target, textureId)
            if (samples > 1)
            {
                glTexImage2DMultisample(target, samples, GL_DEPTH_COMPONENT24, width, height, true)
                glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, textureId, 0)
            }
            else
            {
                if (mipmapGenerator != null)
                {
                    val levels = mipmapGenerator.getLevelCount(width, height)
                    allocateTextureStorage2D(target, levels, GL_DEPTH_COMPONENT24, width, height, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT)
                }
                else glTexImage2D(target, 0, GL_DEPTH_COMPONENT24, width, height, 0, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, 0L)

                glTexParameteri(target, GL_TEXTURE_COMPARE_MODE, GL_NONE)
                glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
                glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
                glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
                glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, target, textureId, 0)
            }
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

        private fun allocateTextureStorage2D(target: Int, levels: Int, internalFormat: Int, width: Int, height: Int, pixelFormat: Int, type: Int)
        {
            if (GlCapabilities.immutableTextureStorage)
            {
                glTexStorage2D(target, levels, internalFormat, width, height)
            }
            else
            {
                for (level in 0 until levels)
                {
                    glTexImage2D(target, level, internalFormat, maxOf(width shr level, 1), maxOf(height shr level, 1), 0, pixelFormat, type, 0L)
                }
            }
            glTexParameteri(target, GL_TEXTURE_BASE_LEVEL, 0)
            glTexParameteri(target, GL_TEXTURE_MAX_LEVEL, levels - 1)
        }
    }
}