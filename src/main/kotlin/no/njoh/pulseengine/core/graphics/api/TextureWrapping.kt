package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER
import org.lwjgl.opengl.GL13.GL_CLAMP_TO_EDGE

/**
 * Enum for OpenGL texture wrapping values
 */
enum class TextureWrapping(val value: Int)
{
    /** Repeats the sampling on the opposite side of the texture */
    REPEAT(GL_REPEAT),

    /** Clamps the sampling to the edge of the texture */
    CLAMP_TO_EDGE(GL_CLAMP_TO_EDGE),

    /** Clamps the sampling to a defined border color (default black) */
    CLAMP_TO_BORDER(GL_CLAMP_TO_BORDER),
}