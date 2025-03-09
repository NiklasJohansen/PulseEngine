package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_CLAMP_TO_EDGE

/**
 * Enum for OpenGL texture wrapping values
 */
enum class TextureWrapping(val value: Int)
{
    /** Repeat texture */
    REPEAT(GL_REPEAT),

    /** Clamp to edge */
    CLAMP_TO_EDGE(GL_CLAMP_TO_EDGE),
}