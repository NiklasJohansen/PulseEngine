package no.njoh.pulseengine.core.graphics.api

import com.fasterxml.jackson.annotation.JsonAlias
import org.lwjgl.opengl.GL11.GL_RGBA8
import org.lwjgl.opengl.GL30.GL_RGBA16F
import org.lwjgl.opengl.GL30.GL_RGBA32F

/**
 * Enum to wrap common OpenGL texture formats.
 */
enum class TextureFormat(val value: Int)
{
    @JsonAlias("NORMAL")
    RGBA8(GL_RGBA8),

    @JsonAlias("HDR_16")
    RGBA16F(GL_RGBA16F),

    @JsonAlias("HDR_32")
    RGBA32F(GL_RGBA32F)
}