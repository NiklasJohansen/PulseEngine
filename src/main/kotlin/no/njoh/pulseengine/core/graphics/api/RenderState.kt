package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.api.StencilState.Action.CLEAR
import no.njoh.pulseengine.core.graphics.api.StencilState.Action.SET
import no.njoh.pulseengine.core.graphics.renderers.StencilRenderer
import org.lwjgl.opengl.GL20.*

/**
 * Base interface for all render states.
 * A render state represents a collection of API calls changing the current graphics state.
 */
interface RenderState
{
    /** Called by the graphics pipeline before rendering the next batch. */
    fun apply(surface: SurfaceInternal)
}

/**
 * Sets up the base OpenGL state before rendering all surfaces to the back-buffer.
 */
object BackBufferBaseState : RenderState
{
    override fun apply(surface: SurfaceInternal)
    {
        // Clear back-buffer with color of given surface
        val c = surface.config.backgroundColor
        glClearColor(c.red, c.green, c.blue, c.alpha)
        glClear(GL_COLOR_BUFFER_BIT)

        // Disable depth testing
        glDisable(GL_DEPTH_TEST)

        // Enable blending
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        // Set viewport size
        glViewport(0, 0, surface.config.width, surface.config.height)
    }
}

/**
 * Sets the OpenGL state for rendering post-processing effects.
 */
object PostProcessingBaseState : RenderState
{
    override fun apply(surface: SurfaceInternal)
    {
        // Clear back-buffer
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)

        // Disable depth testing
        glDisable(GL_DEPTH_TEST)

        // Disable blending
        glDisable(GL_BLEND)
    }
}

/**
 * Sets the view port size to the size of the scaled surface texture
 */
object ViewportState : RenderState
{
    override fun apply(surface: SurfaceInternal)
    {
        glViewport(0, 0, (surface.config.width * surface.config.textureScale).toInt(), (surface.config.height * surface.config.textureScale).toInt())
    }
}

/**
 * Sets up the base OpenGL state before running all batch renderers for the given surface.
 */
object BatchRenderBaseState : RenderState
{
    override fun apply(surface: SurfaceInternal)
    {
        val config = surface.config

        // Set depth state
        if (config.hasDepthAttachment)
        {
            glEnable(GL_DEPTH_TEST)
            glDepthMask(true)
            glDepthFunc(GL_LEQUAL)
            glDepthRange(surface.camera.nearPlane.toDouble(), surface.camera.farPlane.toDouble())
            glClearDepth(surface.camera.farPlane.toDouble())
        }
        else glDisable(GL_DEPTH_TEST)

        // Set blending options
        if (config.blendFunction != BlendFunction.NONE)
        {
            glEnable(GL_BLEND)
            glBlendFunc(config.blendFunction.src, config.blendFunction.dest)
        }
        else glDisable(GL_BLEND)

        // Set viewport size
        glViewport(0, 0, (surface.config.width * config.textureScale).toInt(), (surface.config.height * config.textureScale).toInt())

        // Set color and clear surface
        val c = config.backgroundColor
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

    override fun apply(surface: SurfaceInternal)
    {
        val renderer = surface.getRenderer<StencilRenderer>() ?: return
        if (action == SET) setStencil(surface, renderer)
        if (action == CLEAR) clearStencil(surface, renderer)
    }

    private fun setStencil(surface: SurfaceInternal, renderer: StencilRenderer)
    {
        layer++
        updateStencilBuffer(surface, renderer, operation = if (layer == 1) GL_REPLACE else GL_INCR)
        enableStenciledDrawing()
    }

    private fun clearStencil(surface: SurfaceInternal, renderer: StencilRenderer)
    {
        layer--

        if (layer > 0)
        {
            updateStencilBuffer(surface, renderer, GL_DECR) // Decrease the value in the stencil buffer for the given area
            enableStenciledDrawing()
        }
        else // Wipe stencil buffer and disable stencil testing when layer is 0
        {
            glClearStencil(0)              // Set clearing value to 0
            glStencilMask(0xff)            // Enable drawing to stencil mask
            glClear(GL_STENCIL_BUFFER_BIT) // Clear stencil
            glDisable(GL_STENCIL_TEST)     // Disable stencil testing
            glStencilMask(0x00)            // Disable drawing to stencil mask
        }
    }

    private fun updateStencilBuffer(surface: SurfaceInternal, renderer: StencilRenderer, operation: Int)
    {
        glEnable(GL_STENCIL_TEST)                          // Enable stencil testing
        glStencilOp(GL_KEEP, GL_KEEP, operation)           // Specify the operation to performed when the stencil test passes
        glColorMask(false, false, false, false)            // Disable drawing to color buffer
        glDepthMask(false)                                 // Disable drawing to depth buffer
        glStencilMask(0xff)                                // Enable drawing to stencil mask
        glStencilFunc(GL_ALWAYS, layer, 0xff)              // Make the stencil test always pass while writing
        renderer.drawStencil(surface, x, y, width, height) // Draw the rectangle to the stencil buffer
    }

    private fun enableStenciledDrawing()
    {
        glColorMask(true, true, true, true)  // Enable drawing to color buffer
        glDepthMask(true)                    // Enable drawing to depth buffer
        glStencilMask(0x00)                  // Disable drawing to stencil mask
        glStencilFunc(GL_EQUAL, layer, 0xff) // Stencil test will only pass if stencil value is equal to layer
    }

    companion object
    {
        private var layer = 0
    }
}