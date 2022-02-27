package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_NEAREST

/**
 * Enum to wrap OpenGL texture filtering values.
 */
enum class TextureFilter(val value: Int)
{
    /** Bilinear interpolation */
    LINEAR(GL_LINEAR),

    /** Nearest neighbour */
    NEAREST(GL_NEAREST)
}