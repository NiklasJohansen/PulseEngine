package no.njoh.pulseengine.modules.graphics

import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL30.GL_RGBA16F
import org.lwjgl.opengl.GL30.GL_RGBA32F

/**
 * Enum to wrap common OpenGL texture formats.
 */
enum class TextureFormat(val value: Int)
{
    NORMAL(GL_RGBA),
    HDR_16(GL_RGBA16F),
    HDR_32(GL_RGBA32F)
}