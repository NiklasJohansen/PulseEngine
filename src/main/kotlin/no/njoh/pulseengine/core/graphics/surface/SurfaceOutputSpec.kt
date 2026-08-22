package no.njoh.pulseengine.core.graphics.surface

import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.DEPTH_STENCIL_BUFFER
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.DEPTH_TEXTURE
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling.NONE
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.gpu.texture.mipmap.MipmapGenerator
import no.njoh.pulseengine.core.shared.primitives.PackedSize
import kotlin.math.max

typealias RenderSizeFunction = (surfaceWidth: Int, surfaceHeight: Int, renderScale: Float) -> PackedSize

/** 
 * Specification of the GPU outputs to create for a [Surface]. 
 */
data class SurfaceOutputSpec(
    val attachments: List<SurfaceAttachment> = DEFAULT_ATTACHMENTS,
    val multisampling: Multisampling = NONE,
    val renderScale: Float = 1f,
    val renderSizeFunction: RenderSizeFunction = ::defaultRenderSizeFunc
) {
    init
    {
        require(renderScale.isFinite() && renderScale > 0f) { "Surface output render scale must be finite and positive" }
        require(attachments.distinctBy { it.attachmentPoint }.size == attachments.size) { "Surface output attachments must be unique" }
    }

    companion object
    {
        /** 
         * Scales the output size while ensuring a minimum size of 1x1. 
         */
        fun defaultRenderSizeFunc(width: Int, height: Int, renderScale: Float) =
            PackedSize(max(width * renderScale, 1f), max(height * renderScale, 1f))

        val DEFAULT_ATTACHMENTS = listOf(colorAttachment(), depthStencilBuffer())
        val DEFAULT = SurfaceOutputSpec()
        val EMPTY = SurfaceOutputSpec(attachments = emptyList())
    }
}

/** 
 * Describes one color texture, depth texture, or depth-stencil buffer owned by a surface. 
 */
data class SurfaceAttachment(
    val attachmentPoint: AttachmentPoint,
    val format: TextureFormat,
    val filter: TextureFilter,
    val mipmapGenerator: MipmapGenerator?
)

fun colorAttachment(
    attachmentPoint: AttachmentPoint = COLOR_TEXTURE_0,
    format: TextureFormat = RGBA16F,
    filter: TextureFilter = LINEAR,
    mipmapGenerator: MipmapGenerator? = null
): SurfaceAttachment {
    require(attachmentPoint.isColor) { "$attachmentPoint is not a color attachment" }
    return SurfaceAttachment(attachmentPoint, format, filter, mipmapGenerator)
}

fun depthTexture(mipmapGenerator: MipmapGenerator? = null) = SurfaceAttachment(DEPTH_TEXTURE, RGBA16F, NEAREST, mipmapGenerator)

fun depthStencilBuffer() = SurfaceAttachment(DEPTH_STENCIL_BUFFER, RGBA16F, NEAREST, null)