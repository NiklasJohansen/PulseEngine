package no.njoh.pulseengine.core.graphics.gpu

import no.njoh.pulseengine.core.config.RuntimeProfile
import no.njoh.pulseengine.core.config.RuntimeProfile.BASE_GRAPHICS
import no.njoh.pulseengine.core.config.RuntimeProfile.FULL_GRAPHICS
import no.njoh.pulseengine.core.config.RuntimeProfile.HEADLESS

/**
 * Internal OpenGL contract derived from the runtime profile selected by a game.
 */
enum class GlContract(
    val glMajorVersion: Int,
    val glMinorVersion: Int,
    val glslVersion: Int
) {
    /** OpenGL 4.1 core contract used by [BASE_GRAPHICS]. */
    OPENGL_41(glMajorVersion = 4, glMinorVersion = 1, glslVersion = 410),

    /** OpenGL 4.4 core contract used by [FULL_GRAPHICS]. */
    OPENGL_44(glMajorVersion = 4, glMinorVersion = 4, glslVersion = 440)
}

/**
 * Provides the OpenGL contract associated with the selected runtime profile.
 */
val RuntimeProfile.glContract get() = when (this)
{
    HEADLESS      -> null
    BASE_GRAPHICS -> GlContract.OPENGL_41
    FULL_GRAPHICS -> GlContract.OPENGL_44
}