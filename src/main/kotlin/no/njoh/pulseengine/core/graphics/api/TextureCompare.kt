package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.GL_COMPARE_REF_TO_TEXTURE

/**
 * Enum for OpenGL texture comparison modes, used when sampling from a depth texture.
 */
enum class TextureCompare(val mode: Int, val func: Int) 
{
    /**
     * NONE is an OpenGL texture comparison mode that disables texture comparison.
     * When this mode is active, the `func` parameter is ignored.
     * Commonly used when no specific comparison behavior is required for sampling from a depth texture.
     */
    NONE(GL_NONE, GL_LEQUAL), // func ignored when mode == NONE

    /**
     * LEQUAL is an OpenGL texture comparison mode that enables depth comparison.
     * When this mode is active, the `func` parameter specifies the comparison function to use.
     * Commonly used for shadow mapping and other depth-based effects where you want to compare 
     * the sampled depth value against a reference value.
     */
    LEQUAL(GL_COMPARE_REF_TO_TEXTURE, GL_LEQUAL),
}