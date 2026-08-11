package no.njoh.pulseengine.core.graphics.gpu.texture

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling.NONE
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureAlphaMode.STRAIGHT
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.*
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.RGBA8
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping.*
import no.njoh.pulseengine.core.graphics.gpu.texture.mipmap.MipmapGenerator

class RenderTexture(
    val name: String,
    val handle: TextureHandle,
    val width: Int,
    val height: Int,
    val filter: TextureFilter = LINEAR,
    val wrapping: TextureWrapping = CLAMP_TO_EDGE,
    val format: TextureFormat = RGBA8,
    val attachmentPoint: AttachmentPoint = COLOR_TEXTURE_0,
    val multisampling: Multisampling = NONE,
    val mipmapGenerator: MipmapGenerator? = null,
    var alphaMode: TextureAlphaMode = STRAIGHT
) {
    fun generateMips(engine: PulseEngineInternal) = mipmapGenerator?.generateMipmaps(engine, this)

    companion object
    {
        val BLANK = RenderTexture(name = "BLANK", handle = TextureHandle.NONE, width = 1, height = 1)
    }
}