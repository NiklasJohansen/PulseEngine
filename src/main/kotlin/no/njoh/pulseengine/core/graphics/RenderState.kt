package no.njoh.pulseengine.core.graphics

import no.njoh.pulseengine.core.graphics.StencilState.Action.CLEAR
import no.njoh.pulseengine.core.graphics.StencilState.Action.SET
import no.njoh.pulseengine.core.graphics.api.BlendFunction
import no.njoh.pulseengine.core.graphics.renderers.StencilRenderer
import org.lwjgl.opengl.GL20.*

/**
 * Base interface for all render states.
 * A render state represents a collection of API calls changing the current graphics state.
 */
interface RenderState {

    /** Called by the graphics pipeline before rendering the next batch. */
    fun apply(surface: Surface2DInternal)
}

/**
 * This state sets up the base Open GL state for the given surface.
 */
object BaseState : RenderState
{
    override fun apply(surface: Surface2DInternal)
    {
        val context = surface.context

        // Set depth state
        if (context.hasDepthAttachment)
        {
            glEnable(GL_DEPTH_TEST)
            glDepthMask(true)
            glDepthFunc(GL_LEQUAL)
            glDepthRange(surface.camera.nearPlane.toDouble(), surface.camera.farPlane.toDouble())
            glClearDepth(surface.camera.farPlane.toDouble())
        }
        else glDisable(GL_DEPTH_TEST)

        // Set blending options
        if (context.blendFunction != BlendFunction.NONE)
        {
            glEnable(GL_BLEND)
            glBlendFunc(context.blendFunction.src, context.blendFunction.dest)
        }
        else glDisable(GL_BLEND)

        // Set which attachments from the fragment shader data will be written to
        glDrawBuffers(context.textureAttachments)

        // Set viewport size
        glViewport(0, 0, (surface.width * context.textureScale).toInt(), (surface.height * context.textureScale).toInt())

        // Set color and clear surface
        val c = context.backgroundColor
        glClearColor(c.red, c.green, c.blue, c.alpha)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    }
}

/**
 * This state is used to set or remove a stencil mask.
 */
open class StencilState(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val action: Action
) : RenderState {

    enum class Action { SET, CLEAR }

    override fun apply(surface: Surface2DInternal)
    {
        val renderer = surface.getRenderer(StencilRenderer::class) ?: return
        if (action == SET) setStencil(surface, renderer)
        if (action == CLEAR) clearStencil(surface, renderer)
    }

    private fun setStencil(surface: Surface2DInternal, renderer: StencilRenderer)
    {
        layer++
        updateStencilBuffer(surface, renderer, operation = if (layer == 1) GL_REPLACE else GL_INCR)
        enableStenciledDrawing()
    }

    private fun clearStencil(surface: Surface2DInternal, renderer: StencilRenderer)
    {
        layer--

        if (layer > 0)
        {
            updateStencilBuffer(surface, renderer, GL_DECR) // Decrease the value in the stencil buffer for the given area
            enableStenciledDrawing()
        }
        else // Wipe stencil buffer and disable stencil testing when layer is 0
        {
            glClearStencil(0)            // Set clearing value to 0
            glStencilMask(0xff)       // Enable drawing to stencil mask
            glClear(GL_STENCIL_BUFFER_BIT)  // Clear stencil
            glDisable(GL_STENCIL_TEST)      // Disable stencil testing
            glStencilMask(0x00)       // Disable drawing to stencil mask
        }
    }

    private fun updateStencilBuffer(surface: Surface2DInternal, renderer: StencilRenderer, operation: Int)
    {
        glEnable(GL_STENCIL_TEST)                                      // Enable stencil testing
        glStencilOp(GL_KEEP, GL_KEEP, operation)                       // Specify the operation to performed when the stencil test passes
        glColorMask(false, false, false, false) // Disable drawing to color buffer
        glDepthMask(false)                                        // Disable drawing to depth buffer
        glStencilMask(0xff)                                      // Enable drawing to stencil mask
        glStencilFunc(GL_ALWAYS, layer, 0xff)                    // Make the stencil test always pass while writing
        renderer.drawStencil(surface, x, y, width, height)             // Draw the rectangle to the stencil buffer
    }

    private fun enableStenciledDrawing()
    {
        glColorMask(true, true, true, true) // Enable drawing to color buffer
        glDepthMask(true)                                     // Enable drawing to depth buffer
        glStencilMask(0x00)                                  // Disable drawing to stencil mask
        glStencilFunc(GL_EQUAL, layer, 0xff)                 // Stencil test will only pass if stencil value is equal to layer
    }

    companion object
    {
        private var layer = 0
    }
}




