package no.njoh.pulseengine.core.graphics.gpu.texture

import org.lwjgl.opengl.GL11.GL_RGBA8
import org.lwjgl.opengl.GL30.*

/**
 * Enum to wrap common OpenGL texture formats.
 */
enum class TextureFormat(val internalFormat: Int, val pixelFormat: Int, val type: Int)
{
    // R
    R16F(GL_R16F,  GL_RED,          GL_FLOAT),
    R32F(GL_R32F,  GL_RED,          GL_FLOAT),
    R16I (GL_R16I, GL_RED_INTEGER,  GL_SHORT),
    R32I (GL_R32I, GL_RED_INTEGER,  GL_INT),
    R32UI(GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT),

    // RG
    RG16F(GL_RG16F, GL_RG,         GL_FLOAT),
    RG32F(GL_RG32F, GL_RG,         GL_FLOAT),
    RG16I(GL_RG16I, GL_RG_INTEGER, GL_SHORT),
    RG32I(GL_RG32I, GL_RG_INTEGER, GL_INT),

    // RGB
    RGB16F(GL_RGB16F,               GL_RGB,         GL_FLOAT),
    RGB32F(GL_RGB32F,               GL_RGB,         GL_FLOAT),
    RGB16I(GL_RGB16I,               GL_RGB_INTEGER, GL_SHORT),
    RGB32I(GL_RGB32I,               GL_RGB_INTEGER, GL_INT),
    R11FG11FB10F(GL_R11F_G11F_B10F, GL_RGB,         GL_FLOAT),

    // RGBA
    RGBA8(GL_RGBA8,         GL_RGBA,         GL_UNSIGNED_BYTE),
    SRGBA8(GL_SRGB8_ALPHA8, GL_RGBA,         GL_UNSIGNED_BYTE),
    RGBA16F(GL_RGBA16F,     GL_RGBA,         GL_FLOAT),
    RGBA32F(GL_RGBA32F,     GL_RGBA,         GL_FLOAT),
    RGBA16I(GL_RGBA16I,     GL_RGBA_INTEGER, GL_SHORT),
    RGBA32I(GL_RGBA32I,     GL_RGBA_INTEGER, GL_INT);

    val componentCount get() = when (pixelFormat)
    {
        GL_RED,  GL_RED_INTEGER  -> 1
        GL_RG,   GL_RG_INTEGER   -> 2
        GL_RGB,  GL_RGB_INTEGER  -> 3
        GL_RGBA, GL_RGBA_INTEGER -> 4
        else -> error("Unsupported texture pixel format: $pixelFormat")
    }

    val isIntegerFormat get() = when (pixelFormat)
    {
        GL_RED_INTEGER, GL_RG_INTEGER, GL_RGB_INTEGER, GL_RGBA_INTEGER -> true
        else -> false
    }

    val readType get() = when
    {
        isIntegerFormat && type == GL_UNSIGNED_INT -> GL_UNSIGNED_INT
        isIntegerFormat -> GL_INT
        else -> GL_FLOAT
    }
}