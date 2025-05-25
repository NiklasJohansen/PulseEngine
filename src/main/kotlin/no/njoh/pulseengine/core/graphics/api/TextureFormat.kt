package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL11.GL_RGBA8
import org.lwjgl.opengl.GL30.*

/**
 * Enum to wrap common OpenGL texture formats.
 */
enum class TextureFormat(val internalFormat: Int, val pixelFormat: Int)
{
    // R
    R16F(GL_R16F, GL_RED),
    R32F(GL_R32F, GL_RED),
    R16I(GL_R16I, GL_RED_INTEGER),
    R32I(GL_R32I, GL_RED_INTEGER),

    // RG
    RG16F(GL_RG16F, GL_RG),
    RG32F(GL_RG32F, GL_RG),
    RG16I(GL_RG16I, GL_RG_INTEGER),
    RG32I(GL_RG32I, GL_RG_INTEGER),

    // RGB
    RGB16F(GL_RGB16F, GL_RGB),
    RGB32F(GL_RGB32F, GL_RGB),
    RGB16I(GL_RGB16I, GL_RGB_INTEGER),
    RGB32I(GL_RGB32I, GL_RGB_INTEGER),
    R11FG11FB10F(GL_R11F_G11F_B10F, GL_RGB),

    // RGBA
    RGBA8(GL_RGBA8, GL_RGBA),
    SRGBA8(GL_SRGB8_ALPHA8, GL_RGBA),
    RGBA16F(GL_RGBA16F, GL_RGBA),
    RGBA32F(GL_RGBA32F, GL_RGBA),
    RGBA16I(GL_RGBA16I, GL_RGBA_INTEGER),
    RGBA32I(GL_RGBA32I, GL_RGBA_INTEGER),
}