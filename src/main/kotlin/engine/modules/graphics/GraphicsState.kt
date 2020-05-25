package engine.modules.graphics

import org.joml.Matrix4f

class GraphicsState
{
    lateinit var projectionMatrix: Matrix4f
    lateinit var modelMatrix: Matrix4f

    val textureArray = TextureArray(1024, 1024, 100)
    var blendFunc = BlendFunction.NORMAL
    var bgRed = 0.1f
    var bgGreen = 0.1f
    var bgBlue = 0.1f
    var rgba = 0f
    var depth: Float = 0f
    val farPlane = 5f
    val nearPlane = -1f

    init
    {
        setRGBA(1f, 1f, 1f, 1f)
    }

    fun increaseDepth()
    {
        depth += DEPTH_INC
    }

    fun resetDepth()
    {
        depth = -0.99f
    }

    fun setRGBA(r: Float, g: Float, b: Float, a: Float)
    {
        val red   = (r * 255).toInt()
        val green = (g * 255).toInt()
        val blue  = (b * 255).toInt()
        val alpha = (a * 255).toInt()

        this.rgba = java.lang.Float.intBitsToFloat((red shl 24) or (green shl 16) or (blue shl 8) or alpha)
    }

    companion object
    {
        private const val DEPTH_INC = 0.000001f
    }
}