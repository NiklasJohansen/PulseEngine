package engine.modules.graphics

import engine.data.Texture
import org.lwjgl.opengl.GL11.*

class RenderTarget(
    private val gfxState: GraphicsState
) {
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

        // Setup OpenGL
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        glDepthFunc(GL_LEQUAL)
        glDepthRange(GraphicsState.NEAR_PLANE.toDouble(), GraphicsState.FAR_PLANE.toDouble())

        glEnable(GL_BLEND)
        glBlendFunc(gfxState.blendFunc.src, gfxState.blendFunc.dest)

        glClearColor(gfxState.bgRed, gfxState.bgGreen, gfxState.bgBlue, 0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glClearDepth(GraphicsState.FAR_PLANE.toDouble())
    }

    fun end() = fbo.release()

    fun getTexture(): Texture = fbo.texture

    fun cleanUp()
    {
        fbo.delete()
    }
}