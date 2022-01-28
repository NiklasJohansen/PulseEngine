package no.njoh.pulseengine.modules.graphics

import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_NEAREST

/**
 * Enum to wrap OpenGL texture filtering values.
 */
enum class TextureFilter(val value: Int)
{
    LINEAR(GL_LINEAR),
    NEAREST(GL_NEAREST)
}