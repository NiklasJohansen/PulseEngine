package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL11.*

/**
 * Enum for OpenGL texture wrapping values
 */
enum class TextureWrapping(val value: Int)
{
    /** Repeat texture */
    REPEAT(GL_REPEAT),

    /** Clamp to edge */
    CLAMP(GL_CLAMP),
}