package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL11.*

/**
 * Enum to wrap OpenGL texture filtering values.
 */
enum class TextureFilter(val minValue: Int, val magValue: Int)
{
    /** Bilinear interpolation */
    LINEAR(GL_LINEAR, GL_LINEAR),

    /** Linear interpolation with mipmapping */
    LINEAR_MIPMAP(GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR),

    /** Nearest neighbour */
    NEAREST(GL_NEAREST, GL_NEAREST),

    /** Nearest neighbour with mipmapping */
    NEAREST_MIPMAP(GL_NEAREST_MIPMAP_NEAREST, GL_NEAREST)
}