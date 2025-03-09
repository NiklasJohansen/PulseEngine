package no.njoh.pulseengine.core.graphics.api

data class TextureDescriptor(
    var format: TextureFormat = TextureFormat.RGBA16F,
    var filter: TextureFilter = TextureFilter.LINEAR,
    var wrapping: TextureWrapping = TextureWrapping.CLAMP_TO_EDGE,
    var multisampling: Multisampling = Multisampling.NONE,
    var attachment: Attachment = Attachment.COLOR_TEXTURE_0,
    var scale: Float = 1f,
)