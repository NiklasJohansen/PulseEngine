package no.njoh.pulseengine.modules.graphics

import org.lwjgl.opengl.GL11

enum class BlendFunction(val src: Int, val dest: Int)
{
    NORMAL(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA),
    ADDITIVE(GL11.GL_SRC_ALPHA, GL11.GL_ONE),
    SCREEN(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR)
}