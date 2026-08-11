@file:Suppress("EqualsOrHashCode")

package no.njoh.pulseengine.core.graphics.gpu.texture

import no.njoh.pulseengine.core.graphics.gpu.texture.mipmap.MipmapGenerator
import no.njoh.pulseengine.core.shared.primitives.PackedSize
import kotlin.math.max

data class TextureDescriptor(
    var format: TextureFormat = TextureFormat.RGBA16F,
    var filter: TextureFilter = TextureFilter.LINEAR,
    var wrapping: TextureWrapping = TextureWrapping.CLAMP_TO_EDGE,
    var multisampling: Multisampling = Multisampling.NONE,
    var mipmapGenerator: MipmapGenerator? = null,
    var attachmentPoint: AttachmentPoint = AttachmentPoint.COLOR_TEXTURE_0,
    var scale: Float = 1f,
    var sizeFunc: (width: Int, height: Int, scale: Float) -> PackedSize = { w, h, s -> PackedSize(max(w * s, 1f), max(h * s, 1f)) }
) {
    override fun equals(other: Any?): Boolean =
        other is TextureDescriptor &&
        format == other.format &&
        filter == other.filter &&
        wrapping == other.wrapping &&
        multisampling == other.multisampling &&
        mipmapGenerator === other.mipmapGenerator &&
        attachmentPoint == other.attachmentPoint &&
        scale == other.scale
}