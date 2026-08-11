package no.njoh.pulseengine.core.graphics.gpu.texture

import org.lwjgl.opengl.GL30.*

/**
 * Wraps common frame buffer attachments.
 */
enum class AttachmentPoint(
    val glValue: Int,
    val isColor: Boolean = true,
    val isDepth: Boolean = false
) {
    COLOR_TEXTURE_0(GL_COLOR_ATTACHMENT0),
    COLOR_TEXTURE_1(GL_COLOR_ATTACHMENT1),
    COLOR_TEXTURE_2(GL_COLOR_ATTACHMENT2),
    COLOR_TEXTURE_3(GL_COLOR_ATTACHMENT3),
    COLOR_TEXTURE_4(GL_COLOR_ATTACHMENT4),
    DEPTH_TEXTURE(GL_DEPTH_ATTACHMENT, isColor = false, isDepth = true),
    DEPTH_STENCIL_BUFFER(GL_DEPTH_STENCIL_ATTACHMENT, isColor = false, isDepth = true);

    val glLocation = glValue - GL_COLOR_ATTACHMENT0
}
