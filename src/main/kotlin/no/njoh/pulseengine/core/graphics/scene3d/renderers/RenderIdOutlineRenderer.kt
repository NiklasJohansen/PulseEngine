package no.njoh.pulseengine.core.graphics.scene3d.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.FullscreenPass
import no.njoh.pulseengine.core.graphics.gpu.buffer.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.gpu.texture.BlendFunction
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.surface.renderers.Renderer
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL14.glBlendFuncSeparate

class RenderIdOutlineRenderer(
    var renderIdSurfaceName: String,
    var renderIdAttachmentPoint: AttachmentPoint = COLOR_TEXTURE_0,
    var outlineWidthPixels: Int = 1,
    override val order: Int = 5
) : Renderer() {

    private lateinit var program: ShaderProgram
    private lateinit var pass: FullscreenPass
    private lateinit var selectionBuffer: StreamingIntBufferObject

    private var readSelection = RenderIdSelectionBitset()
    private var writeSelection = RenderIdSelectionBitset()

    @Volatile
    private var pendingSelectionGeneration = 0L
    private var activeSelectionGeneration = 0L
    private var selectionBufferDirty = false

    override fun init(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        program = ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/surface.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/render_id_outline.frag"))
        )
        pass = FullscreenPass(program).apply { init() }
        selectionBuffer = StreamingIntBufferObject.createUnboundShaderStorageBuffer(INITIAL_SELECTION_WORD_CAPACITY)
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        val pendingGeneration = pendingSelectionGeneration
        if (pendingGeneration != activeSelectionGeneration)
        {
            writeSelection = readSelection.also { readSelection = writeSelection }
            activeSelectionGeneration = pendingGeneration
            selectionBufferDirty = true
        }

        if (readSelection.hasSelection) increaseBatchSize()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        val renderIdTexture = engine.gfx.getSurface(renderIdSurfaceName)?.getTexture(renderIdAttachmentPoint, final = false) ?: return

        if (selectionBufferDirty)
        {
            selectionBuffer.clear()
            selectionBuffer.fill(readSelection.wordCount)
            {
                for (i in 0 until readSelection.wordCount)
                    put(readSelection.words[i])
            }
            selectionBuffer.submit()
            selectionBufferDirty = false
        }

        glDisable(GL_DEPTH_TEST)
        glEnable(GL_BLEND)
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        glDisable(GL_CULL_FACE)

        program.bind()
        program.setUniformSampler("uRenderIdTexture", renderIdTexture, filter = TextureFilter.NEAREST)
        program.setUniform("uTextureSize", renderIdTexture.width.toFloat(), renderIdTexture.height.toFloat())
        program.setUniform("uOutlineWidth", outlineWidthPixels.coerceAtLeast(1))
        program.setUniform("uSelectionWordCount", readSelection.wordCount)
        selectionBuffer.bindSubmittedRange(SELECTION_BUFFER_BINDING)

        pass.draw()
        selectionBuffer.markSubmittedDataInUse()

        val blendFunc = surface.config.blendFunction
        if (blendFunc != BlendFunction.NONE)
        {
            glEnable(GL_BLEND)
            glBlendFuncSeparate(blendFunc.srcRgb, blendFunc.destRgb, blendFunc.srcAlpha, blendFunc.destAlpha)
        }
        else glDisable(GL_BLEND)
    }

    override fun destroy(engine: PulseEngineInternal)
    {
        pass.destroy()
        program.destroy()
        selectionBuffer.destroy()
    }

    fun beginSelectionUpdate() = writeSelection.clear()

    fun setSelected(renderId: Long) = writeSelection.add(renderId)

    fun finishSelectionUpdate() { pendingSelectionGeneration++ }

    private class RenderIdSelectionBitset
    {
        var words = IntArray(INITIAL_WORD_CAPACITY); private set
        var wordCount = 0; private set
        var hasSelection = false; private set

        fun clear()
        {
            words.fill(0, 0, wordCount)
            wordCount = 0
            hasSelection = false
        }

        fun add(renderId: Long)
        {
            if (renderId !in 0L..MAX_RENDER_ID)
                return

            val wordIndex = (renderId ushr WORD_SHIFT).toInt()
            val requiredCapacity = wordIndex + 1
            if (requiredCapacity > words.size)
                words = words.copyOf(maxOf(requiredCapacity, words.size * 2))
            words[wordIndex] = words[wordIndex] or (1 shl (renderId.toInt() and WORD_MASK))
            wordCount = maxOf(wordCount, wordIndex + 1)
            hasSelection = true
        }

        companion object
        {
            private const val INITIAL_WORD_CAPACITY = 16
            private const val WORD_SHIFT = 5
            private const val WORD_MASK = 31
            private const val MAX_BITSET_BYTES = 16 * 1024 * 1024
            private const val MAX_RENDER_ID = (MAX_BITSET_BYTES / Int.SIZE_BYTES) * 32L - 1L
        }
    }

    companion object
    {
        private const val SELECTION_BUFFER_BINDING = 13
        private const val INITIAL_SELECTION_WORD_CAPACITY = 4096
    }
}