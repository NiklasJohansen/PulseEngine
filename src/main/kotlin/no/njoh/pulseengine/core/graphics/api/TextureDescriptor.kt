package no.njoh.pulseengine.core.graphics.api

data class TextureDescriptor(
    var format: TextureFormat = TextureFormat.RGBA8,
    var filter: TextureFilter = TextureFilter.LINEAR,
    var multisampling: Multisampling = Multisampling.NONE,
    var attachment: Attachment = Attachment.COLOR_TEXTURE_0,
    var scale: Float = 1f,
)