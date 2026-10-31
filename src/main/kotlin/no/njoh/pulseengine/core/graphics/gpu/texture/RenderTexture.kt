package no.njoh.pulseengine.core.graphics.gpu.texture

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling.NONE
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureAlphaMode.STRAIGHT
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.*
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.RGBA8
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping.*
import no.njoh.pulseengine.core.graphics.gpu.texture.mipmap.MipmapGenerator
import no.njoh.pulseengine.core.graphics.gpu.texture.multisampling.MsaaResolver
import org.lwjgl.opengl.GL11.glDeleteTextures

class RenderTexture(
    handle: TextureHandle,
    val name: String,
    val width: Int,
    val height: Int,
    val filter: TextureFilter = LINEAR,
    val wrapping: TextureWrapping = CLAMP_TO_EDGE,
    val format: TextureFormat = RGBA8,
    val attachmentPoint: AttachmentPoint = COLOR_TEXTURE_0,
    val multisampling: Multisampling = NONE,
    val sampleCount: Int = 1,
    val msaaResolver: MsaaResolver? = null,
    val mipmapGenerator: MipmapGenerator? = null,
    var alphaMode: TextureAlphaMode = STRAIGHT
) {
    init { require(handle.isGlTexture || handle.isNone) { "Render textures must use a GL texture handle or NONE" } }

    var handle = handle; private set

    fun generateMips(engine: PulseEngineInternal) = mipmapGenerator?.generateMipmaps(engine, this)

    fun destroy()
    {
        if (!handle.isGlTexture) return
        glDeleteTextures(handle.glId)
        handle = TextureHandle.INVALID
    }

    companion object
    {
        val BLANK = RenderTexture(handle = TextureHandle.NONE, name = "BLANK", width = 1, height = 1)
    }
}