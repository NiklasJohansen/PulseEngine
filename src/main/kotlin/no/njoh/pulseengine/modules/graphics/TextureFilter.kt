package no.njoh.pulseengine.modules.graphics

import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_NEAREST

/**
 * Enum to wrap OpenGL texture filtering values.
 */
enum class TextureFilter(val value: Int)
{
    BILINEAR_INTERPOLATION(GL_LINEAR),
    NEAREST_NEIGHBOUR(GL_NEAREST)
}