package no.njoh.pulseengine.core.graphics.api.mipmap

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.glGenerateMipmap

/**
 * A native mipmap generator implementation that uses OpenGL's built-in mipmap generation.
 */
class NativeMipmapGenerator : MipmapGenerator()
{
    override fun onInit(engine: PulseEngineInternal) {}
    override fun onDestroy() {}
    override fun onGenerate(engine: PulseEngineInternal, texture: RenderTexture)
    {
        glBindTexture(GL_TEXTURE_2D, texture.handle.textureIndex)
        glGenerateMipmap(GL_TEXTURE_2D)
    }
}