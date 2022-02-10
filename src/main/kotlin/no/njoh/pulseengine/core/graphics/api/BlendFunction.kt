package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL11

enum class BlendFunction(val src: Int, val dest: Int)
{
    NONE(-1, -1),
    NORMAL(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA),
    ADDITIVE(GL11.GL_SRC_ALPHA, GL11.GL_ONE),
    SCREEN(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR)
}