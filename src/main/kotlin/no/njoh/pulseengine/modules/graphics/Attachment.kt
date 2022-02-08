package no.njoh.pulseengine.modules.graphics

import org.lwjgl.opengl.GL30.*

/**
 * Wraps common frame buffer attachments.
 */
enum class Attachment(
    val value: Int,
    val isDrawable: Boolean = true,
    val hasDepth: Boolean = false
) {
    COLOR_TEXTURE_0(GL_COLOR_ATTACHMENT0),
    COLOR_TEXTURE_1(GL_COLOR_ATTACHMENT1),
    COLOR_TEXTURE_2(GL_COLOR_ATTACHMENT2),
    COLOR_TEXTURE_3(GL_COLOR_ATTACHMENT3),
    COLOR_TEXTURE_4(GL_COLOR_ATTACHMENT4),
    DEPTH_TEXTURE(GL_DEPTH_ATTACHMENT, isDrawable = false, hasDepth = true),
    DEPTH_STENCIL_BUFFER(GL_DEPTH_STENCIL_ATTACHMENT, isDrawable = false, hasDepth = true)
}