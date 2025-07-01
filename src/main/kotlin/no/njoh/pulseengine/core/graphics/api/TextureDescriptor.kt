@file:Suppress("EqualsOrHashCode")

package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.shared.primitives.PackedSize
import kotlin.math.max

data class TextureDescriptor(
    var format: TextureFormat = TextureFormat.RGBA16F,
    var filter: TextureFilter = TextureFilter.LINEAR,
    var wrapping: TextureWrapping = TextureWrapping.CLAMP_TO_EDGE,
    var multisampling: Multisampling = Multisampling.NONE,
    var mipmapGenerator: MipmapGenerator? = null,
    var attachment: Attachment = Attachment.COLOR_TEXTURE_0,
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
        attachment == other.attachment &&
        scale == other.scale
}