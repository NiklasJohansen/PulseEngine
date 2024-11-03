package no.njoh.pulseengine.core.graphics.api

import com.fasterxml.jackson.annotation.JsonAlias
import org.lwjgl.opengl.GL11.GL_RGBA8
import org.lwjgl.opengl.GL30.*

/**
 * Enum to wrap common OpenGL texture formats.
 */
enum class TextureFormat(val internalFormat: Int, val pixelFormat: Int)
{
    @JsonAlias("NORMAL")
    RGBA8(GL_RGBA8, GL_RGBA),

    @JsonAlias("HDR_16")
    RGBA16F(GL_RGBA16F, GL_RGBA),

    @JsonAlias("HDR_32")
    RGBA32F(GL_RGBA32F, GL_RGBA),

    R16F(GL_R16F, GL_RED),

    RG16F(GL_RG16F, GL_RG)
}