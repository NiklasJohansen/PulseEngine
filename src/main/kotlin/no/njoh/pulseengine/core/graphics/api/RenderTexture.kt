package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.*

class RenderTexture(
    val name: String,
    val handle: TextureHandle,
    val width: Int,
    val height: Int,
    val filter: TextureFilter = LINEAR,
    val wrapping: TextureWrapping = CLAMP_TO_EDGE,
    val format: TextureFormat = TextureFormat.RGBA8,
    val attachment: Attachment = Attachment.COLOR_TEXTURE_0,
    val multisampling: Multisampling = Multisampling.NONE
) {
    companion object
    {
        val BLANK = RenderTexture(name = "BLANK", handle = TextureHandle.NONE, width = 1, height = 1)
    }
}