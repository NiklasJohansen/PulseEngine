package engine.modules.graphics

import engine.data.Texture
import org.lwjgl.opengl.GL11

class RenderTarget
{
    private lateinit var fbo: FrameBufferObject

    fun init(width: Int, height: Int)
    {
        if(this::fbo.isInitialized)
            fbo.delete()

        fbo = FrameBufferObject.create(width, height)
    }

    fun begin()
    {
        fbo.bind()
        GL11.glClearColor(0f, 0f, 0f, 0f)
        fbo.clear()

        // Setup OpenGL
        GL11.glEnable(GL11.GL_DEPTH_TEST)
        GL11.glDepthMask(true)
        GL11.glDepthFunc(GL11.GL_LEQUAL)
        GL11.glDepthRange(GraphicsState.NEAR_PLANE.toDouble(), GraphicsState.FAR_PLANE.toDouble())
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)
        GL11.glClearDepth(GraphicsState.FAR_PLANE.toDouble())
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA)
    }

    fun end() = fbo.release()

    fun getTexture(): Texture = fbo.texture

    fun cleanUp()
    {
        fbo.delete()
    }
}