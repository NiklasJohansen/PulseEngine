package engine.modules.graphics

import engine.data.Font
import engine.data.RenderMode
import engine.data.Texture
import org.joml.Matrix4f
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GLCapabilities
import java.lang.Float.floatToIntBits
import java.lang.Float.intBitsToFloat


class RetainedModeGraphics : GraphicsEngineInterface
{
    override fun getRenderMode() = RenderMode.RETAINED

    private lateinit var projectionMatrix: Matrix4f
    private lateinit var viewMatrix: Matrix4f
    private lateinit var modelMatrix: Matrix4f

    private var blendFunc = BlendFunction.NORMAL
    private var bgRed = 0.1f
    private var bgGreen = 0.1f
    private var bgBlue = 0.1f
    private var rgba: Float = toRGBA(1f, 1f, 1f, 1f)

    private var depth: Float = 0f
    private val DEPTH_INC = 0.0000001f
    private val farPlane = 10f

    private val textureArray = TextureArray(1024, 1024, 100)
    private val textRenderer = TextRenderer()
    private lateinit var defaultFont: Font

    private val textureRenderer = TextureRenderer(100)
    private val quadRenderer = SimpleQuadRenderer(100)
    private val lineRenderer = ColoredLineRenderer(100)
    private val monoColorLineRenderer = UniColorLineRenderer(100)

    private val batchRenderers = listOf(
        monoColorLineRenderer,
        lineRenderer,
        textureRenderer,
        quadRenderer
    )

    override fun init(viewPortWidth: Int, viewPortHeight: Int)
    {
        updateViewportSize(viewPortWidth, viewPortHeight, true)

        // Load default font
        defaultFont = Font("/FiraSans-Regular.ttf","default_font", floatArrayOf(24f, 72f))
        defaultFont.load()
        initTexture(defaultFont.charTexture)
    }

    override fun initTexture(texture: Texture)
    {
        textureArray.upload(texture)
    }

    override fun updateViewportSize(width: Int, height: Int, windowRecreated: Boolean)
    {
        if(windowRecreated)
            initOpenGL()

        glViewport(0, 0, width, height)
        projectionMatrix = Matrix4f().ortho(0.0f, width.toFloat(), height.toFloat(), 0.0f, -1f, farPlane)
        viewMatrix = Matrix4f()
        modelMatrix = Matrix4f()
    }

    private fun initOpenGL()
    {
        GL.createCapabilities()
        glEnable(GL_BLEND)
        setBackgroundColor(bgRed, bgGreen, bgBlue)
        setBlendFunction(blendFunc)

        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        glDepthFunc(GL_LEQUAL)
        glDepthRange(-1.0, farPlane.toDouble())
        clearBuffer()

        batchRenderers.forEach { it.init() }
    }

    override fun cleanUp()
    {
        batchRenderers.forEach { it.cleanup() }
        textureArray.cleanup()
        defaultFont.delete()
    }

    override fun clearBuffer()
    {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glClearDepth(farPlane.toDouble())
        depth = -0.99f
    }

    override fun postRender()
    {
        batchRenderers.forEach { it.render() }
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        lineRenderer.line(x0, y0, x1, y1)
    }

    override fun drawLinePoint(x: Float, y: Float)
    {
        lineRenderer.linePoint(x, y)
    }

    override fun drawSameColorLines(block: (draw: LineRenderer) -> Unit)
    {
        block(monoColorLineRenderer)
        monoColorLineRenderer.render()
    }

    override fun drawQuad(x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        quadRenderer.quad(x, y, width, height)
    }

    override fun drawQuadVertex(x: Float, y: Float)
    {
        quadRenderer.vertex(x, y)
    }

    override fun drawImage(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        textureRenderer.drawImage(texture, x, y, width, height, rot, xOrigin, yOrigin)
    }

    override fun drawImage(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float)
    {
        textureRenderer.drawImage(texture, x, y, width, height, rot, xOrigin, yOrigin, uMin, vMin, uMax, vMax)
    }

    override fun drawText(text: String, x: Float, y: Float, font: Font?, fontSize: Float, xOrigin: Float, yOrigin: Float)
    {
        textRenderer.draw(this, text, x, y, font ?: defaultFont, fontSize, xOrigin, yOrigin)
    }

    override fun setColor(red: Float, green: Float, blue: Float, alpha: Float)
    {
        rgba = toRGBA(red, green, blue, alpha)
    }

    override fun setBackgroundColor(red: Float, green: Float, blue: Float)
    {
        glClearColor(red, green, blue, 1f)
        this.bgRed = red
        this.bgGreen = green
        this.bgBlue = blue
    }

    override fun setBlendFunction(func: BlendFunction)
    {
        glBlendFunc(func.src, func.dest)
        this.blendFunc
    }

    override fun setLineWidth(width: Float) { }

    private fun toRGBA(r: Float, g: Float, b: Float, a: Float): Float
    {
        val red   = (r * 255).toInt()
        val green = (g * 255).toInt()
        val blue  = (b * 255).toInt()
        val alpha = (a * 255).toInt()

        return intBitsToFloat((red shl 24) or (green shl 16) or (blue shl 8) or alpha)
    }

    inner class TextureRenderer(private val initialCapacity: Int) : BatchRenderer
    {
        private val stride = 10 * java.lang.Float.BYTES
        private val bytes = initialCapacity * 4L * stride
        private var vertexCount = 0

        private lateinit var program: ShaderProgram
        private lateinit var vbo: FloatBufferObject
        private lateinit var ebo: IntBufferObject
        private lateinit var vao: VertexArrayObject
        private var initialized = false

        override fun init()
        {
            vao = VertexArrayObject.create()

            if(!initialized)
            {
                ebo = VertexBufferObject.createElementBuffer(bytes / 6)
                vbo = VertexBufferObject.create(bytes)
                program = ShaderProgram.create("/engine/shaders/default/arrayTexture.vert", "/engine/shaders/default/arrayTexture.frag").use()
                initialized = true
            }

            ebo.bind()
            vbo.bind()
            program.use()
            program.defineVertexAttributeArray("position", 3, GL_FLOAT, stride, 0)
            program.defineVertexAttributeArray("offset", 2, GL_FLOAT, stride, 3 * java.lang.Float.BYTES)
            program.defineVertexAttributeArray("rotation", 1, GL_FLOAT, stride, 5 * java.lang.Float.BYTES)
            program.defineVertexAttributeArray("texCoord", 2, GL_FLOAT, stride, 6 * java.lang.Float.BYTES)
            program.defineVertexAttributeArray("texIndex",1, GL_FLOAT, stride, 8 * java.lang.Float.BYTES)
            program.defineVertexAttributeArray("color",1, GL_FLOAT, stride, 9 * java.lang.Float.BYTES)
            program.setUniform("textureArray", 0)
        }

        fun drawImage(texture: Texture, x: Float, y: Float, w: Float, h: Float, rot: Float, xOrigin: Float, yOrigin: Float)
        {
            val u = texture.uMax
            val v = texture.vMax
            val texIndex = texture.textureId.toFloat() + if(texture.format == GL_ALPHA) 0.5f else 0.0f
            val xOffset = w * xOrigin
            val yOffset = h * yOrigin

            vbo.put(
                x, y, depth, -xOffset, -yOffset, rot, 0f, 0f, texIndex, rgba,
                x, y, depth, -xOffset, h-yOffset, rot, 0f,  v, texIndex, rgba,
                x, y, depth, w-xOffset, h-yOffset, rot,  u,  v, texIndex, rgba,
                x, y, depth, w-xOffset, -yOffset, rot,  u, 0f, texIndex, rgba
            )

            ebo.put(
                vertexCount + 0,
                vertexCount + 1,
                vertexCount + 2,
                vertexCount + 2,
                vertexCount + 3,
                vertexCount + 0
            )

            vertexCount += 4
            depth += DEPTH_INC
        }

        fun drawImage(texture: Texture, x: Float, y: Float, w: Float, h: Float, rot: Float, xOrigin: Float, yOrigin: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float)
        {
            val uMax = texture.uMax * uMax
            val vMax = texture.vMax * vMax
            val uMin = texture.uMax * uMin
            val vMin = texture.vMax * vMin
            val index = texture.textureId.toFloat() + if(texture.format == GL_ALPHA) 0.5f else 0.0f

            val xOffset = w * xOrigin
            val yOffset = h * yOrigin

            vbo.put(
                x, y, depth, -xOffset, -yOffset,  rot, uMin, vMin, index, rgba,
                x, y, depth, -xOffset, h-yOffset, rot, uMin, vMax, index, rgba,
                x, y, depth, w-xOffset, h-yOffset, rot, uMax, vMax, index, rgba,
                x, y, depth, w-xOffset, -yOffset, rot, uMax, vMin, index, rgba
            )

            ebo.put(
                vertexCount + 0,
                vertexCount + 1,
                vertexCount + 2,
                vertexCount + 2,
                vertexCount + 3,
                vertexCount + 0
            )

            vertexCount += 4
            depth += DEPTH_INC
        }

        override fun render()
        {
            if(vertexCount == 0)
                return

            vao.bind()

            program.use()
            program.setUniform("projection", projectionMatrix)
            program.setUniform("view", viewMatrix)
            program.setUniform("model", modelMatrix)

            textureArray.bind()

            vbo.flush()
            ebo.flush()
            ebo.draw(GL_TRIANGLES, 1)

            vertexCount = 0
        }

        override fun cleanup()
        {
            vbo.delete()
            vao.delete()
            ebo.delete()
        }
    }

    inner class SimpleQuadRenderer(initialCapacity: Int) : BatchRenderer
    {
        private val stride = 4 * java.lang.Float.BYTES
        private val bytes = 4L * initialCapacity * stride
        private var vertexCount = 0
        private var singleVertexCount = 0

        private lateinit var program: ShaderProgram
        private lateinit var vbo: FloatBufferObject
        private lateinit var ebo: IntBufferObject
        private lateinit var vao: VertexArrayObject
        private var initialized = false

        override fun init()
        {
            vao = VertexArrayObject.create()

            if (!initialized)
            {
                ebo = VertexBufferObject.createElementBuffer(bytes / 6)
                vbo = VertexBufferObject.create(bytes)
                program = ShaderProgram.create("/engine/shaders/default/default.vert", "/engine/shaders/default/default.frag").use()
            }

            ebo.bind()
            vbo.bind()
            program.use()
            program.defineVertexAttributeArray("position", 3, GL_FLOAT, stride, 0)
            program.defineVertexAttributeArray("color",1, GL_FLOAT, stride, 3 * java.lang.Float.BYTES)
        }

        fun quad(x: Float, y: Float, width: Float, height: Float)
        {
            vbo.put(
                x, y, depth, rgba,
                x, y+height, depth, rgba,
                x+width, y+height, depth, rgba,
                x+width, y, depth, rgba
            )

            ebo.put(
                vertexCount + 0,
                vertexCount + 1,
                vertexCount + 2,
                vertexCount + 2,
                vertexCount + 3,
                vertexCount + 0
            )

            vertexCount += 4
            depth += DEPTH_INC
        }

        fun vertex(x: Float, y: Float)
        {
            vbo.put(x, y, depth, rgba)
            singleVertexCount++

            if (singleVertexCount == 4)
            {
                ebo.put(
                    vertexCount + 0,
                    vertexCount + 1,
                    vertexCount + 2,
                    vertexCount + 2,
                    vertexCount + 3,
                    vertexCount + 0
                )
                singleVertexCount = 0
                vertexCount += 4
                depth += DEPTH_INC
            }
        }

        override fun render()
        {
            if (vertexCount == 0)
                return

            vao.bind()

            program.use()
            program.setUniform("projection", projectionMatrix)
            program.setUniform("view", viewMatrix)
            program.setUniform("model", modelMatrix)

            glBindTexture(GL_TEXTURE_2D, 0)

            vbo.flush()
            ebo.flush()
            ebo.draw(GL_TRIANGLES, 1)

            vertexCount = 0
        }

        override fun cleanup()
        {
            vbo.delete()
            vao.delete()
            ebo.delete()
            program.delete()
        }
    }

    inner class UniColorLineRenderer(initialCapacity: Int) : LineRenderer, BatchRenderer
    {
        private val stride = 3 * java.lang.Float.BYTES
        private val bytes = stride * 2L * initialCapacity

        private lateinit var program: ShaderProgram
        private lateinit var vbo: FloatBufferObject
        private lateinit var vao: VertexArrayObject
        private var initialized = false

        override fun init()
        {
            vao = VertexArrayObject.create()

            if (!initialized)
            {
                vbo = VertexBufferObject.create(bytes)
                program = ShaderProgram.create("/engine/shaders/default/lineUniColor.vert", "/engine/shaders/default/line.frag").use()
                initialized = true
            }

            vbo.bind()
            program.use()
            program.defineVertexAttributeArray("position", 3, GL30.GL_FLOAT, stride, 0)
        }

        override fun linePoint(x0: Float, y0: Float)
        {
            vbo.put(x0, y0, depth)
            depth += DEPTH_INC
        }

        override fun line(x0: Float, y0: Float, x1: Float, y1: Float)
        {
            vbo.put(x0, y0, depth, x1, y1, depth)
            depth += DEPTH_INC
        }

        override fun render()
        {
            vao.bind()
            vbo.bind()
            program.use()
            program.setUniform("projection", projectionMatrix)
            program.setUniform("view", viewMatrix)
            program.setUniform("model", modelMatrix)
            program.setUniform("color", floatToIntBits(rgba))

            vbo.draw(GL_LINES, 3)
        }

        override fun cleanup()
        {
            vbo.delete()
            vao.delete()
            program.delete()
        }
    }

    inner class ColoredLineRenderer(initialCapacity: Int) : BatchRenderer
    {
        private val stride = 4 * java.lang.Float.BYTES
        private val bytes = 2L * initialCapacity * stride

        private lateinit var program: ShaderProgram
        private lateinit var vbo: FloatBufferObject
        private lateinit var vao: VertexArrayObject
        private var initialized = false

        override fun init()
        {
            vao = VertexArrayObject.create()

            if (!initialized)
            {
                vbo = VertexBufferObject.create(bytes)
                program = ShaderProgram.create("/engine/shaders/default/line.vert", "/engine/shaders/default/line.frag").use()
                initialized = true
            }

            vbo.bind()
            program.use()
            program.defineVertexAttributeArray("position", 3, GL_FLOAT, stride, 0)
            program.defineVertexAttributeArray("rgbaColor",1, GL_FLOAT, stride, 3 * java.lang.Float.BYTES)
        }

        fun linePoint(x: Float, y: Float)
        {
            vbo.put(x, y, depth, rgba)
            depth += DEPTH_INC
        }

        fun line(x0: Float, y0: Float, x1: Float, y1: Float)
        {
            vbo.put(x0, y0, depth, rgba, x1, y1, depth, rgba)
            depth += DEPTH_INC
        }

        override fun render()
        {
            vao.bind()
            vbo.bind()
            program.use()
            program.setUniform("projection", projectionMatrix)
            program.setUniform("view", viewMatrix)
            program.setUniform("model", modelMatrix)

            vbo.draw(GL_LINES, 4)
        }

        override fun cleanup()
        {
            vbo.delete()
            vao.delete()
            program.delete()
        }
    }
}

interface BatchRenderer
{
    fun init()
    fun cleanup()
    fun render()
}
