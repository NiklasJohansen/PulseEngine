package no.njoh.pulseengine.core.graphics.gpu.texture

import org.lwjgl.opengl.GL11.*

enum class BlendFunction(
    val srcRgb: Int,
    val destRgb: Int,
    val srcAlpha: Int = srcRgb,
    val destAlpha: Int = destRgb
) {
    NONE(-1, -1),
    NORMAL(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA),
    ADDITIVE(GL_SRC_ALPHA, GL_ONE),
    SCREEN(GL_ONE, GL_ONE_MINUS_SRC_COLOR);

    val outputAlphaMode get() = when (this)
    {
        NORMAL -> TextureAlphaMode.PREMULTIPLIED
        else -> TextureAlphaMode.STRAIGHT
    }
}